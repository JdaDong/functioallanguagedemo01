/// Rust 函数式编程 Demo 25: 手写 Future / Waker / 迷你 Executor
///
/// 08 号 Demo 展示了如何"使用"async/await，这个 Demo 告诉你"下面到底发生了什么"：
///
///   - async fn 编译后是一个实现了 Future 的状态机
///   - Future::poll 是"被 executor 不停问: 你好了吗?"
///   - Waker 是 "好了之后我该通知谁再来 poll 我"
///   - Pin 保证自引用的 future 在被 poll 过程中不会被 move
///
/// 这个 Demo 只用 std（没有 tokio），手写一个能跑的最小异步 runtime：
///   1) 自己实现 Future 的两个叶子节点（Ready / Timer）
///   2) 写一个能 block_on 的超小 executor
///   3) 用 async fn 写业务代码，放到自己的 executor 上跑
///
/// 编译运行:
///   rustc 25_pin_future_executor.rs -O -o /tmp/demo25 && /tmp/demo25

use std::future::Future;
use std::pin::Pin;
use std::sync::{Arc, Condvar, Mutex};
use std::task::{Context, Poll, Wake, Waker};
use std::thread;
use std::time::{Duration, Instant};

// ============================================================
// 1) 一个"立即就绪"的 Future
// ============================================================
struct Ready<T> { value: Option<T> }

impl<T: Unpin> Future for Ready<T> {
    type Output = T;
    fn poll(mut self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<T> {
        Poll::Ready(self.value.take().expect("Ready polled after completion"))
    }
}

fn ready<T>(v: T) -> Ready<T> { Ready { value: Some(v) } }

// ============================================================
// 2) 一个真正的"异步定时器" Future
//    第一次 poll 时启动后台线程, 时间到了把 waker 唤醒
// ============================================================
struct TimerFuture {
    inner: Arc<TimerShared>,
}

struct TimerShared {
    done: Mutex<bool>,
    waker: Mutex<Option<Waker>>,
}

impl TimerFuture {
    fn new(d: Duration) -> Self {
        let shared = Arc::new(TimerShared {
            done:  Mutex::new(false),
            waker: Mutex::new(None),
        });
        let worker = Arc::clone(&shared);
        thread::spawn(move || {
            thread::sleep(d);
            *worker.done.lock().unwrap() = true;
            if let Some(w) = worker.waker.lock().unwrap().take() {
                w.wake();
            }
        });
        TimerFuture { inner: shared }
    }
}

impl Future for TimerFuture {
    type Output = ();
    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<()> {
        if *self.inner.done.lock().unwrap() {
            Poll::Ready(())
        } else {
            // 关键一步：把当前 task 的 waker 存起来, 时间到了才能"点名叫我"
            *self.inner.waker.lock().unwrap() = Some(cx.waker().clone());
            Poll::Pending
        }
    }
}

// ============================================================
// 3) 最小 executor: block_on
//    核心就是个循环: poll -> 若 Pending 就阻塞等 wake -> 醒了再 poll
// ============================================================
struct ParkWaker {
    park: Mutex<bool>,
    cv:   Condvar,
}

impl ParkWaker {
    fn new() -> Arc<Self> {
        Arc::new(ParkWaker { park: Mutex::new(false), cv: Condvar::new() })
    }

    fn wait(&self) {
        let mut g = self.park.lock().unwrap();
        while !*g { g = self.cv.wait(g).unwrap(); }
        *g = false;
    }
}

impl Wake for ParkWaker {
    fn wake(self: Arc<Self>) {
        *self.park.lock().unwrap() = true;
        self.cv.notify_all();
    }
    fn wake_by_ref(self: &Arc<Self>) {
        *self.park.lock().unwrap() = true;
        self.cv.notify_all();
    }
}

fn block_on<F: Future>(fut: F) -> F::Output {
    // Pin 到栈上（最简模式）
    let mut fut = Box::pin(fut);
    let park  = ParkWaker::new();
    let waker = Waker::from(Arc::clone(&park));
    let mut cx = Context::from_waker(&waker);

    loop {
        match fut.as_mut().poll(&mut cx) {
            Poll::Ready(v)  => return v,
            Poll::Pending   => park.wait(),
        }
    }
}

// ============================================================
// 4) 再写一个真正"并发"的组合子: join2
//    同时 poll 两个 future, 两个都 Ready 才 Ready
// ============================================================
struct Join2<A: Future, B: Future> {
    a: Pin<Box<A>>,
    b: Pin<Box<B>>,
    a_out: Option<A::Output>,
    b_out: Option<B::Output>,
}

fn join2<A: Future, B: Future>(a: A, b: B) -> Join2<A, B> {
    Join2 { a: Box::pin(a), b: Box::pin(b), a_out: None, b_out: None }
}

impl<A: Future, B: Future> Future for Join2<A, B> {
    type Output = (A::Output, B::Output);
    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output> {
        // SAFETY: 我们没有 move Pin 内部的数据, 只是分别 poll 两个 Box<Pin<...>>
        let this = unsafe { self.get_unchecked_mut() };

        if this.a_out.is_none() {
            if let Poll::Ready(v) = this.a.as_mut().poll(cx) { this.a_out = Some(v); }
        }
        if this.b_out.is_none() {
            if let Poll::Ready(v) = this.b.as_mut().poll(cx) { this.b_out = Some(v); }
        }
        if this.a_out.is_some() && this.b_out.is_some() {
            Poll::Ready((this.a_out.take().unwrap(), this.b_out.take().unwrap()))
        } else {
            Poll::Pending
        }
    }
}

// ============================================================
// 5) 用 async fn 写业务, 放到自己的 executor 上跑
// ============================================================
async fn fetch_user(id: u64) -> String {
    TimerFuture::new(Duration::from_millis(40)).await;
    format!("user-{}", id)
}

async fn fetch_order(id: u64) -> u64 {
    TimerFuture::new(Duration::from_millis(30)).await;
    id * 100
}

async fn business_flow() -> String {
    // 顺序版: 累计 40 + 30 = 70ms
    let t = Instant::now();
    let user = fetch_user(1).await;
    let order = fetch_order(42).await;
    let seq_ms = t.elapsed().as_millis();

    // 并发版: join 同时等两个 future, 只要 max(40,30) = 40ms
    let t = Instant::now();
    let (user2, order2) = join2(fetch_user(2), fetch_order(43)).await;
    let par_ms = t.elapsed().as_millis();

    format!(
        "ready => seq ({}ms): {} / order {}  |  par ({}ms): {} / order {}",
        seq_ms, user, order, par_ms, user2, order2
    )
}

fn main() {
    println!("=== Rust Demo 25: 手写 Future / Executor ===\n");

    // --- 1) Ready ---
    println!("-- 1) Ready future --");
    let v: i32 = block_on(ready(42));
    println!("  block_on(ready(42)) = {}", v);

    // --- 2) Timer ---
    println!("\n-- 2) Timer future --");
    let t = Instant::now();
    block_on(TimerFuture::new(Duration::from_millis(50)));
    println!("  block_on(Timer(50ms)), 实际耗时 {}ms", t.elapsed().as_millis());

    // --- 3) async fn + join2 ---
    println!("\n-- 3) 真正的异步业务 --");
    let out = block_on(business_flow());
    println!("  {}", out);

    println!("\n=== 关键理解 ===");
    println!("  1) async fn 被编译成一个自动生成的状态机 struct, 实现 Future trait");
    println!("  2) poll 返回 Pending 时, executor 用 waker 把当前任务'挂起'");
    println!("  3) IO 就绪/定时器到期 => 后台线程调用 waker.wake() => executor 再次 poll");
    println!("  4) Pin 保证 Future 在 poll 过程中不会被 move (否则自引用会指野)");
    println!("  5) 真实生态: tokio / async-std / smol 都是这个模型的工业级实现,");
    println!("     外加 IO 多路复用 (epoll/kqueue/IOCP) 作为 waker 的触发源。");
}
