/// Rust 函数式编程 Demo 13: Send / Sync & 内部可变性
///
/// Rust 并发安全的基础是两个 auto trait：
///   - Send：值"能被移动到另一个线程"
///   - Sync：&T "能被多个线程共享"（等价于 T: Sync ⟺ &T: Send）
///
/// 这两个 trait 不是你手动 impl 的，编译器根据结构体字段自动推导。
/// 本 Demo 用四种典型数据类型演示"哪些能跨线程、哪些不能、为什么":
///   1) Arc<T>       : T: Send+Sync 才能 Send+Sync, 不可变共享
///   2) Arc<Mutex<T>>: 内部可变 + 互斥锁, 写时阻塞
///   3) Arc<RwLock<T>>: 内部可变 + 读写锁, 读并发
///   4) Atomic*      : lock-free 计数器, 最快
///
/// 以及一个反例：Rc<T> 不是 Send, 编译期就被拦下。
///
/// 编译运行:
///   rustc 13_send_sync_interior_mut.rs -O -o /tmp/demo13 && /tmp/demo13

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use std::thread;
use std::time::Instant;

const N_THREADS: usize = 8;
const N_OPS: usize = 50_000;

// ============================================================
// 1) Arc<Mutex<T>> —— 最通用的共享可变
// ============================================================
fn bench_mutex() -> u128 {
    let counter = Arc::new(Mutex::new(0u64));
    let start = Instant::now();

    let handles: Vec<_> = (0..N_THREADS)
        .map(|_| {
            let counter = Arc::clone(&counter);
            thread::spawn(move || {
                for _ in 0..N_OPS {
                    let mut guard = counter.lock().unwrap();
                    *guard += 1;
                }
            })
        })
        .collect();

    for h in handles {
        h.join().unwrap();
    }
    let elapsed = start.elapsed().as_millis();
    let final_value = *counter.lock().unwrap();
    println!("  Mutex     : value={}, took={}ms", final_value, elapsed);
    elapsed
}

// ============================================================
// 2) Arc<RwLock<T>> —— 多读少写
// ============================================================
fn bench_rwlock() -> u128 {
    let counter = Arc::new(RwLock::new(0u64));
    let start = Instant::now();

    let handles: Vec<_> = (0..N_THREADS)
        .map(|_| {
            let counter = Arc::clone(&counter);
            thread::spawn(move || {
                for _ in 0..N_OPS {
                    let mut guard = counter.write().unwrap();
                    *guard += 1;
                }
            })
        })
        .collect();

    for h in handles {
        h.join().unwrap();
    }
    let elapsed = start.elapsed().as_millis();
    println!(
        "  RwLock    : value={}, took={}ms (写密集场景比 Mutex 更慢)",
        *counter.read().unwrap(),
        elapsed
    );
    elapsed
}

// ============================================================
// 3) AtomicU64 —— lock-free
// ============================================================
fn bench_atomic() -> u128 {
    let counter = Arc::new(AtomicU64::new(0));
    let start = Instant::now();

    let handles: Vec<_> = (0..N_THREADS)
        .map(|_| {
            let counter = Arc::clone(&counter);
            thread::spawn(move || {
                for _ in 0..N_OPS {
                    counter.fetch_add(1, Ordering::Relaxed);
                }
            })
        })
        .collect();

    for h in handles {
        h.join().unwrap();
    }
    let elapsed = start.elapsed().as_millis();
    println!(
        "  Atomic    : value={}, took={}ms (lock-free, 最快)",
        counter.load(Ordering::Relaxed),
        elapsed
    );
    elapsed
}

// ============================================================
// 4) RwLock 的正确用法：读并发 + 偶尔写
// ============================================================
fn demo_rwlock_read_heavy() {
    let data = Arc::new(RwLock::new(vec![1, 2, 3]));
    let start = Instant::now();

    // 10 个读者 + 2 个写者
    let readers: Vec<_> = (0..10)
        .map(|_| {
            let data = Arc::clone(&data);
            thread::spawn(move || {
                let mut sum = 0u64;
                for _ in 0..10_000 {
                    let guard = data.read().unwrap();
                    sum += guard.iter().map(|&x| x as u64).sum::<u64>();
                }
                sum
            })
        })
        .collect();

    let writers: Vec<_> = (0..2)
        .map(|i| {
            let data = Arc::clone(&data);
            thread::spawn(move || {
                for k in 0..100 {
                    data.write().unwrap().push(i * 1000 + k);
                }
            })
        })
        .collect();

    let total_reads: u64 = readers.into_iter().map(|h| h.join().unwrap()).sum();
    for w in writers {
        w.join().unwrap();
    }
    println!(
        "  RwLock 读密集场景: 10 读 + 2 写, total_reads={}, took={}ms",
        total_reads,
        start.elapsed().as_millis()
    );
}

// ============================================================
// 5) 反例：Rc<T> 不是 Send（编译期被拦下）
// ============================================================
fn show_rc_not_send() {
    println!("  Rc<T> 只能在单线程内用, 跨线程会编译失败:");
    println!("    let rc = Rc::new(42);");
    println!("    thread::spawn(move || println!(\"{{}}\", *rc));");
    println!("    //  ^^^^^^^^^^^^^ `Rc<i32>` cannot be sent between threads");
    println!("  跨线程共享用 Arc<T> 代替.");
}

// ============================================================
// 6) 用 trait bound 明确要求 Send
// ============================================================
fn assert_send_sync<T: Send + Sync>() {}

fn main() {
    println!("=== Rust Demo 13: Send / Sync & 内部可变性 ===\n");

    println!("-- 并发计数 benchmark ({} 线程 × {} 操作) --", N_THREADS, N_OPS);
    let m = bench_mutex();
    let r = bench_rwlock();
    let a = bench_atomic();
    println!(
        "  加速比: Atomic 相对 Mutex ≈ {:.2}x, RwLock 相对 Mutex ≈ {:.2}x",
        m as f64 / a.max(1) as f64,
        m as f64 / r.max(1) as f64
    );

    println!("\n-- RwLock 的真正主场: 读多写少 --");
    demo_rwlock_read_heavy();

    println!("\n-- 反例: Rc<T> --");
    show_rc_not_send();

    println!("\n-- 编译期 Send/Sync 断言 --");
    assert_send_sync::<Arc<Mutex<Vec<String>>>>();
    assert_send_sync::<Arc<AtomicU64>>();
    // assert_send_sync::<std::rc::Rc<i32>>(); // ❌ 编译错误
    println!("  Arc<Mutex<...>>  ✅ Send+Sync");
    println!("  Arc<AtomicU64>   ✅ Send+Sync");
    println!("  Rc<...>          ❌ 不是 Send");

    println!("\n=== 选型指南 ===");
    println!("  只读共享:              Arc<T>");
    println!("  写密集 / 互斥临界区:    Arc<Mutex<T>>");
    println!("  读多写少:              Arc<RwLock<T>>");
    println!("  纯计数器 / flag:        Atomic*");
    println!("  单线程内部可变:         Rc<RefCell<T>> (不可跨线程)");
}
