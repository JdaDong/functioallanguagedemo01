/// Rust 函数式编程 Demo 14: mpsc Channel & 数据并行
///
/// 在 tokio 之前就存在、且仍然是 Rust 标配的两套并发工具：
///
///   1) std::sync::mpsc      —— 多生产者、单消费者 channel
///   2) 手写 work-stealing    —— 用 Mutex+Vec 模拟 rayon 的核心思路
///   3) crossbeam::scope 风格 —— 用 thread::scope（Rust 1.63+）安全共享栈上借用
///
/// 没有用到任何第三方 crate，rustc 一个命令就能跑。
///
/// 编译运行:
///   rustc 14_channel_and_parallel.rs -O -o /tmp/demo14 && /tmp/demo14

use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

// ============================================================
// 1) 经典 mpsc: 多生产者 → 单消费者
// ============================================================
fn demo_mpsc_pipeline() {
    println!("-- 1) mpsc: 3 生产者 → 1 消费者 --");
    let (tx, rx) = mpsc::channel::<(usize, u64)>();

    for producer_id in 0..3 {
        let tx = tx.clone();
        thread::spawn(move || {
            for i in 0..5 {
                let msg = (producer_id, (producer_id * 100 + i) as u64);
                tx.send(msg).unwrap();
                thread::sleep(Duration::from_millis(5));
            }
        });
    }
    drop(tx); // 把最初的发送端关掉, 否则 rx 永远不会 None

    for msg in rx {
        println!("  消费到: {:?}", msg);
    }
}

// ============================================================
// 2) pipeline 式: stage1 -> stage2 -> stage3
// ============================================================
fn demo_pipeline_stages() {
    println!("\n-- 2) pipeline: 采集 → 过滤 → 汇总 --");

    // stage1: 生产原始数据
    let (tx1, rx1) = mpsc::channel::<u64>();
    thread::spawn(move || {
        for i in 1..=20 {
            tx1.send(i).unwrap();
        }
    });

    // stage2: 只保留偶数, 转成平方
    let (tx2, rx2) = mpsc::channel::<u64>();
    thread::spawn(move || {
        for n in rx1 {
            if n % 2 == 0 {
                tx2.send(n * n).unwrap();
            }
        }
    });

    // stage3: 汇总求和
    let total: u64 = rx2.iter().sum();
    println!("  偶数平方和(1..=20) = {}", total);
}

// ============================================================
// 3) 手写 work-stealing pool —— 数据并行求和
// ============================================================
fn parallel_sum(data: Vec<u64>, n_workers: usize) -> u64 {
    let queue = Arc::new(Mutex::new(data));
    let total = Arc::new(Mutex::new(0u64));

    let handles: Vec<_> = (0..n_workers)
        .map(|_| {
            let q = Arc::clone(&queue);
            let t = Arc::clone(&total);
            thread::spawn(move || {
                let mut local = 0u64;
                loop {
                    // 每次尽可能抓一批, 减少锁开销
                    let mut batch = {
                        let mut g = q.lock().unwrap();
                        let take = g.len().min(64);
                        g.drain(..take).collect::<Vec<_>>()
                    };
                    if batch.is_empty() {
                        break;
                    }
                    while let Some(x) = batch.pop() {
                        local = local.wrapping_add(x);
                    }
                }
                *t.lock().unwrap() += local;
            })
        })
        .collect();

    for h in handles {
        h.join().unwrap();
    }
    Arc::try_unwrap(total).unwrap().into_inner().unwrap()
}

// ============================================================
// 4) thread::scope —— 安全借用栈上数据
// ============================================================
fn demo_scoped_threads() {
    println!("\n-- 4) thread::scope: 线程可以安全借用栈上数据 --");
    let numbers = vec![1u64, 2, 3, 4, 5, 6, 7, 8, 9, 10];

    // scope 的关键：子线程结束一定早于作用域结束，所以可以借 &numbers
    let (even_sum, odd_sum) = thread::scope(|s| {
        let h_even = s.spawn(|| numbers.iter().filter(|&&x| x % 2 == 0).sum::<u64>());
        let h_odd  = s.spawn(|| numbers.iter().filter(|&&x| x % 2 != 0).sum::<u64>());
        (h_even.join().unwrap(), h_odd.join().unwrap())
    });

    println!("  even_sum={}, odd_sum={}", even_sum, odd_sum);
    println!("  numbers 仍然可用: {:?}", numbers);
}

// ============================================================
// 5) 串行 vs 并行性能对比
// ============================================================
fn bench_parallel_vs_serial() {
    println!("\n-- 5) 并行 vs 串行求和对比 (1M 元素) --");
    let big: Vec<u64> = (1..=1_000_000).collect();

    let t1 = Instant::now();
    let serial: u64 = big.iter().sum();
    let t_serial = t1.elapsed().as_micros();

    let t2 = Instant::now();
    let parallel = parallel_sum(big.clone(), 4);
    let t_parallel = t2.elapsed().as_micros();

    println!("  serial   = {}  ({}μs)", serial, t_serial);
    println!("  parallel = {}  ({}μs, 4 workers)", parallel, t_parallel);
    assert_eq!(serial, parallel);
    println!("  加速比约 {:.2}x (真实业务建议用 rayon, 这里只是演示原理)",
             t_serial as f64 / t_parallel.max(1) as f64);
}

fn main() {
    println!("=== Rust Demo 14: Channel & 数据并行 ===\n");

    demo_mpsc_pipeline();
    demo_pipeline_stages();
    demo_scoped_threads();
    bench_parallel_vs_serial();

    println!("\n=== 工具选型 ===");
    println!("  单向多对一消息:        std::sync::mpsc");
    println!("  高性能多消费者:        crossbeam::channel (SPMC/MPMC)");
    println!("  数据并行 map/reduce:    rayon (par_iter)");
    println!("  async 场景的 channel:  tokio::sync::mpsc / broadcast (见 Demo 19)");
    println!("  线程借栈上数据:        std::thread::scope (稳定于 Rust 1.63)");
}
