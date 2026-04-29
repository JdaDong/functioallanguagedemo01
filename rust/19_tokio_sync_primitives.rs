/// Rust 函数式编程 Demo 19: 同步原语 & 事件模型（oneshot / broadcast / select 的思路）
///
/// tokio 提供了一套异步同步原语（oneshot / mpsc / broadcast / Notify / select!），
/// 这里不引依赖，用标准库 + Condvar 手写出"语义等价"的同步版本，
/// 帮你在脑子里对上 tokio 的心智模型。
///
///   1) oneshot      —— 一次性 send/recv（请求-应答模式标配）
///   2) broadcast    —— 多消费者 fanout（所有订阅者都能收到）
///   3) Notify       —— 无载荷的"有事了"信号
///   4) select 风格   —— 多个事件源谁先到用谁
///
/// 编译运行:
///   rustc 19_tokio_sync_primitives.rs -O -o /tmp/demo19 && /tmp/demo19

use std::sync::{Arc, Condvar, Mutex};
use std::thread;
use std::time::{Duration, Instant};

// ============================================================
// 1) oneshot —— 一次性通知
// ============================================================
struct OneshotSender<T> { slot: Arc<(Mutex<Option<T>>, Condvar)> }
struct OneshotReceiver<T> { slot: Arc<(Mutex<Option<T>>, Condvar)> }

fn oneshot<T>() -> (OneshotSender<T>, OneshotReceiver<T>) {
    let slot = Arc::new((Mutex::new(None), Condvar::new()));
    (OneshotSender { slot: Arc::clone(&slot) }, OneshotReceiver { slot })
}

impl<T> OneshotSender<T> {
    fn send(self, value: T) {
        let (m, cv) = &*self.slot;
        *m.lock().unwrap() = Some(value);
        cv.notify_all();
    }
}

impl<T> OneshotReceiver<T> {
    fn recv(self) -> T {
        let (m, cv) = &*self.slot;
        let mut g = m.lock().unwrap();
        while g.is_none() {
            g = cv.wait(g).unwrap();
        }
        g.take().unwrap()
    }
}

// ============================================================
// 2) broadcast —— 多消费者 fanout（简化版：有界环形缓冲）
// ============================================================
struct Broadcast<T: Clone> {
    inner: Mutex<Vec<T>>,
    cv: Condvar,
}

impl<T: Clone + Send + 'static> Broadcast<T> {
    fn new() -> Arc<Self> {
        Arc::new(Broadcast { inner: Mutex::new(Vec::new()), cv: Condvar::new() })
    }

    fn send(self: &Arc<Self>, v: T) {
        self.inner.lock().unwrap().push(v);
        self.cv.notify_all();
    }

    /// 每个订阅者维护自己的"读到第几条"游标
    fn subscribe(self: &Arc<Self>) -> BroadcastReceiver<T> {
        BroadcastReceiver { bus: Arc::clone(self), cursor: self.inner.lock().unwrap().len() }
    }
}

struct BroadcastReceiver<T: Clone> {
    bus: Arc<Broadcast<T>>,
    cursor: usize,
}

impl<T: Clone + Send + 'static> BroadcastReceiver<T> {
    fn recv(&mut self, deadline: Instant) -> Option<T> {
        let mut guard = self.bus.inner.lock().unwrap();
        while guard.len() <= self.cursor {
            let now = Instant::now();
            if now >= deadline { return None; }
            let (g, _) = self.bus.cv.wait_timeout(guard, deadline - now).unwrap();
            guard = g;
        }
        let v = guard[self.cursor].clone();
        self.cursor += 1;
        Some(v)
    }
}

// ============================================================
// 3) Notify —— 无载荷信号
// ============================================================
struct Notify {
    state: Mutex<bool>,
    cv: Condvar,
}

impl Notify {
    fn new() -> Arc<Self> {
        Arc::new(Notify { state: Mutex::new(false), cv: Condvar::new() })
    }
    fn notify_one(self: &Arc<Self>) {
        *self.state.lock().unwrap() = true;
        self.cv.notify_one();
    }
    fn wait(self: &Arc<Self>) {
        let mut g = self.state.lock().unwrap();
        while !*g {
            g = self.cv.wait(g).unwrap();
        }
        *g = false;
    }
}

// ============================================================
// 4) select 风格：两个 channel 谁先来用谁
// ============================================================
fn select_first<A, B>(a: OneshotReceiver<A>, b: OneshotReceiver<B>) -> SelectResult<A, B>
where
    A: Send + 'static,
    B: Send + 'static,
{
    let done = Arc::new((Mutex::new(None::<SelectResult<A, B>>), Condvar::new()));

    let done_a = Arc::clone(&done);
    thread::spawn(move || {
        let v = a.recv();
        let (m, cv) = &*done_a;
        let mut g = m.lock().unwrap();
        if g.is_none() { *g = Some(SelectResult::Left(v)); cv.notify_all(); }
    });

    let done_b = Arc::clone(&done);
    thread::spawn(move || {
        let v = b.recv();
        let (m, cv) = &*done_b;
        let mut g = m.lock().unwrap();
        if g.is_none() { *g = Some(SelectResult::Right(v)); cv.notify_all(); }
    });

    let (m, cv) = &*done;
    let mut g = m.lock().unwrap();
    while g.is_none() { g = cv.wait(g).unwrap(); }
    g.take().unwrap()
}

enum SelectResult<A, B> { Left(A), Right(B) }

// ============================================================
// 5) 运行器
// ============================================================
fn demo_oneshot() {
    println!("-- 1) oneshot: worker 完成后回传结果 --");
    let (tx, rx) = oneshot::<u64>();
    thread::spawn(move || {
        thread::sleep(Duration::from_millis(30));
        tx.send(42);
    });
    println!("  收到结果 = {}", rx.recv());
}

fn demo_broadcast() {
    println!("\n-- 2) broadcast: 3 订阅者同时收到全部消息 --");
    let bus = Broadcast::<String>::new();
    let mut r1 = bus.subscribe();
    let mut r2 = bus.subscribe();
    let mut r3 = bus.subscribe();

    let bus_pub = Arc::clone(&bus);
    thread::spawn(move || {
        for i in 1..=3 {
            bus_pub.send(format!("event-{}", i));
            thread::sleep(Duration::from_millis(5));
        }
    });

    let deadline = Instant::now() + Duration::from_millis(500);
    for (name, r) in [("r1", &mut r1), ("r2", &mut r2), ("r3", &mut r3)] {
        let mut got = Vec::new();
        while let Some(m) = r.recv(deadline) { got.push(m); if got.len() == 3 { break; } }
        println!("  {} => {:?}", name, got);
    }
}

fn demo_notify() {
    println!("\n-- 3) Notify: 只是'有事了'信号, 不带数据 --");
    let sig = Notify::new();
    let sig_c = Arc::clone(&sig);
    thread::spawn(move || {
        thread::sleep(Duration::from_millis(20));
        sig_c.notify_one();
    });
    let t = Instant::now();
    sig.wait();
    println!("  等到信号, 耗时 {}ms", t.elapsed().as_millis());
}

fn demo_select() {
    println!("\n-- 4) select 风格: 谁先完成用谁 --");
    let (tx_a, rx_a) = oneshot::<&'static str>();
    let (tx_b, rx_b) = oneshot::<u64>();

    thread::spawn(move || {
        thread::sleep(Duration::from_millis(50));
        tx_a.send("慢的来了");
    });
    thread::spawn(move || {
        thread::sleep(Duration::from_millis(10));
        tx_b.send(999);
    });

    match select_first(rx_a, rx_b) {
        SelectResult::Left(v)  => println!("  左边先: {}", v),
        SelectResult::Right(v) => println!("  右边先: {}", v),
    }
}

fn main() {
    println!("=== Rust Demo 19: 同步原语 & 事件模型 ===\n");
    demo_oneshot();
    demo_broadcast();
    demo_notify();
    demo_select();

    println!("\n=== 对照 tokio ===");
    println!("  tokio::sync::oneshot::channel()     一次性 async 通知");
    println!("  tokio::sync::mpsc::channel(cap)     多生产者单消费者, 带反压");
    println!("  tokio::sync::broadcast::channel(n)  fanout 多消费者");
    println!("  tokio::sync::Notify                 无载荷信号 (等价 Condvar)");
    println!("  tokio::select! {{}}                   同时等多个 future, 谁先就绪用谁");
    println!("  心智一致, 只是 async 版本不阻塞线程, 而是让出给 executor");
}
