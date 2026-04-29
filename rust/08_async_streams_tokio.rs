/// Rust 函数式编程 Demo 8: async/await 与 Stream —— FP 风格的异步管道
///
/// 本 Demo 兼顾两种读法：
///   * 纯直觉版（默认编译通过，不依赖任何第三方库）：手写一个最小
///     Future 执行器，展示 async/await 只是"语法糖覆盖下的状态机 + Poll"。
///   * 生产版（注释中给出等价的 tokio + futures 代码）：说明在真实项目里
///     同样的直觉如何直接对应到 tokio::spawn / futures::stream。
///
/// 对标：
///   Haskell: IO + async / conduit / streamly
///   Scala  : cats-effect IO / fs2.Stream
///
/// 运行（纯直觉版）：
///   rustc 08_async_streams_tokio.rs && ./08_async_streams_tokio
///
/// 运行（生产版）：
///   见文件底部 "与 tokio 对照" 小节。

use std::future::Future;
use std::pin::Pin;
use std::task::{Context, Poll, Waker, RawWaker, RawWakerVTable};
use std::sync::Arc;

// ============================================================
// 1. 手写一个最小 block_on 执行器
//    目的：让读者亲眼看到 "async 就是 poll + 再调度"。
// ============================================================

fn dummy_waker() -> Waker {
    fn no_op(_: *const ()) {}
    fn clone(_: *const ()) -> RawWaker { RawWaker::new(std::ptr::null(), &VTABLE) }
    static VTABLE: RawWakerVTable = RawWakerVTable::new(clone, no_op, no_op, no_op);
    unsafe { Waker::from_raw(RawWaker::new(std::ptr::null(), &VTABLE)) }
}

fn block_on<F: Future>(mut f: F) -> F::Output {
    // SAFETY: 只在本函数栈内使用，生命周期受控
    let mut f = unsafe { Pin::new_unchecked(&mut f) };
    let waker = dummy_waker();
    let mut cx = Context::from_waker(&waker);
    loop {
        match f.as_mut().poll(&mut cx) {
            Poll::Ready(v)  => return v,
            Poll::Pending   => std::thread::yield_now(),
        }
    }
}

// ============================================================
// 2. 定义几个 async 函数：注意返回类型其实是 impl Future
// ============================================================

async fn fetch_user(id: u32) -> String {
    // 现实中这里会是一次 HTTP / DB 调用
    format!("user-{id}")
}

async fn fetch_order(user: &str) -> Vec<u32> {
    // 现实中这里会是另一次 IO
    match user {
        "user-1" => vec![100, 101],
        "user-2" => vec![200],
        _        => vec![],
    }
}

// 组合两个 async：在 Haskell 里对应 `do { u <- fetchUser id; fetchOrder u }`
async fn user_orders(id: u32) -> (String, Vec<u32>) {
    let u = fetch_user(id).await;
    let o = fetch_order(&u).await;
    (u, o)
}

// ============================================================
// 3. 手写一个最小 Stream trait —— 异步版的 Iterator
// ============================================================

trait AsyncIter {
    type Item;
    fn poll_next(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>>;
}

// 一个具体 Stream：产生 [0, 1, 2, ..., n)，每次返回 Ready
struct Counter { cur: u32, end: u32 }
impl Counter { fn new(end: u32) -> Self { Self { cur: 0, end } } }

impl AsyncIter for Counter {
    type Item = u32;
    fn poll_next(mut self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<Option<u32>> {
        if self.cur < self.end {
            let v = self.cur; self.cur += 1; Poll::Ready(Some(v))
        } else {
            Poll::Ready(None)
        }
    }
}

// 高阶 combinator：map —— 对标 fs2 的 .map
struct Map<S, F> { inner: S, f: Arc<F> }

impl<S, F, B> AsyncIter for Map<S, F>
where
    S: AsyncIter,
    F: Fn(S::Item) -> B,
{
    type Item = B;
    fn poll_next(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<B>> {
        // 把内部 stream 固定住，再 poll_next，再 map 一下
        // SAFETY: 不移动 inner
        let this = unsafe { self.get_unchecked_mut() };
        let inner = unsafe { Pin::new_unchecked(&mut this.inner) };
        match inner.poll_next(cx) {
            Poll::Ready(Some(v)) => Poll::Ready(Some((this.f)(v))),
            Poll::Ready(None)    => Poll::Ready(None),
            Poll::Pending        => Poll::Pending,
        }
    }
}

// 把 stream 跑成 Vec（≈ fs2 的 compile.toList）
async fn collect_all<S: AsyncIter + Unpin>(mut s: S) -> Vec<S::Item> {
    let mut out = Vec::new();
    loop {
        let v = std::future::poll_fn(|cx| Pin::new(&mut s).poll_next(cx)).await;
        match v { Some(x) => out.push(x), None => break }
    }
    out
}

// ============================================================
// 4. main：串起所有直觉
// ============================================================

fn main() {
    println!("=== 1. async/await 基本组合 ===");
    let (u, o) = block_on(user_orders(1));
    println!("  user_orders(1) -> user={u} orders={o:?}");
    let (u, o) = block_on(user_orders(2));
    println!("  user_orders(2) -> user={u} orders={o:?}");

    println!("\n=== 2. 自定义 Stream + map ===");
    let s      = Counter::new(5);
    let mapped = Map { inner: s, f: Arc::new(|x: u32| x * x) };
    let out    = block_on(collect_all(mapped));
    println!("  Counter(5).map(^2).collect() = {out:?}");

    println!("\n=== 3. 与 tokio 对照（注释即代码，装了 tokio 就能直接换） ===");
    println!("  // Cargo.toml: tokio = {{ version = \"1\", features = [\"full\"] }}");
    println!("  //             futures = \"0.3\"");
    println!("  //");
    println!("  // #[tokio::main]");
    println!("  // async fn main() {{");
    println!("  //     let (u, o) = user_orders(1).await;                        // 对应本 Demo 第 1 段");
    println!("  //     let out: Vec<u32> = futures::stream::iter(0..5)");
    println!("  //         .map(|x| x * x)");
    println!("  //         .collect().await;                                     // 对应本 Demo 第 2 段");
    println!("  //     let both = tokio::join!(fetch_user(1), fetch_user(2));    // 并发组合");
    println!("  // }}");

    println!("\n=== 4. 直觉总结 ===");
    println!("  async fn   === 生成一个实现 Future 的匿名状态机");
    println!("  .await     === 把 Future 暂停点编织进状态机，再由执行器恢复");
    println!("  Stream     === 异步版 Iterator: poll_next 反复返回 Ready(Some)/Ready(None)/Pending");
    println!("  tokio::spawn === Haskell forkIO / Scala IO.start / Erlang spawn");
}
