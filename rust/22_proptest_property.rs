/// Rust 函数式编程 Demo 22: 手写迷你 proptest —— 性质测试的核心思路
///
/// 对标:
///   - Haskell QuickCheck (Demo haskell/12_QuickCheck.hs)
///   - Erlang  PropEr     (Demo erlang/08_property_testing_proper.erl)
///   - Scala   ScalaCheck
///
/// 性质测试三件套:
///   1) Arbitrary         —— 随机生成输入
///   2) Property (forall)  —— 对任意输入都成立的断言
///   3) Shrinking          —— 失败时把复杂反例压缩成最小形式
///
/// 本 Demo 零依赖实现这三件套, 并用它测试几个经典性质:
///   - reverse(reverse(xs)) == xs
///   - sorted(xs).is_sorted()
///   - 一个故意写错的函数, 看 shrink 能把反例压到多小
///
/// 编译运行:
///   rustc 22_proptest_property.rs -O -o /tmp/demo22 && /tmp/demo22

use std::cell::Cell;

// ============================================================
// 1) 线性同余随机数（无依赖, 可复现）
// ============================================================
struct Rng { state: Cell<u64> }

impl Rng {
    fn new(seed: u64) -> Self { Rng { state: Cell::new(seed.max(1)) } }
    fn next_u64(&self) -> u64 {
        let mut s = self.state.get();
        s ^= s << 13;
        s ^= s >> 7;
        s ^= s << 17;
        self.state.set(s);
        s
    }
    fn bounded(&self, max: usize) -> usize {
        (self.next_u64() as usize) % max.max(1)
    }
    fn range_i32(&self, lo: i32, hi: i32) -> i32 {
        let range = (hi - lo + 1) as u32;
        lo + (self.next_u64() as u32 % range) as i32
    }
}

// ============================================================
// 2) Arbitrary trait —— 怎么生成、怎么 shrink
// ============================================================
trait Arbitrary: Sized + Clone + std::fmt::Debug {
    fn arbitrary(rng: &Rng) -> Self;
    /// 返回"比当前更小"的候选值集合
    fn shrink(&self) -> Vec<Self>;
}

impl Arbitrary for i32 {
    fn arbitrary(rng: &Rng) -> Self { rng.range_i32(-50, 50) }
    fn shrink(&self) -> Vec<Self> {
        let mut v = Vec::new();
        if *self != 0  { v.push(0); }
        if *self > 0   { v.push(self / 2); v.push(self - 1); }
        if *self < 0   { v.push(self / 2); v.push(self + 1); }
        v
    }
}

impl<T: Arbitrary> Arbitrary for Vec<T> {
    fn arbitrary(rng: &Rng) -> Self {
        let len = rng.bounded(10);
        (0..len).map(|_| T::arbitrary(rng)).collect()
    }

    fn shrink(&self) -> Vec<Self> {
        let mut candidates = Vec::new();
        if !self.is_empty() {
            // 1) 整体丢掉一半
            let half = self.len() / 2;
            candidates.push(self[..half].to_vec());
            candidates.push(self[half..].to_vec());
            // 2) 逐个删除一个元素
            for i in 0..self.len() {
                let mut c = self.clone();
                c.remove(i);
                candidates.push(c);
            }
            // 3) 把其中一个元素 shrink
            for i in 0..self.len() {
                for sm in self[i].shrink() {
                    let mut c = self.clone();
                    c[i] = sm;
                    candidates.push(c);
                }
            }
        }
        candidates
    }
}

// ============================================================
// 3) 通用 Property runner —— 带 shrink
// ============================================================
struct TestResult<T> {
    #[allow(dead_code)] ok: bool,
    #[allow(dead_code)] counterexample: Option<T>,
    #[allow(dead_code)] tries: usize,
    #[allow(dead_code)] shrinks: usize,
}

fn forall<T, F>(name: &str, seed: u64, n_tries: usize, prop: F) -> TestResult<T>
where
    T: Arbitrary,
    F: Fn(&T) -> bool,
{
    let rng = Rng::new(seed);
    for i in 1..=n_tries {
        let sample = T::arbitrary(&rng);
        if !prop(&sample) {
            // 失败 → 进入 shrink
            let (minimal, shrinks) = shrink_minimize(sample, &prop);
            println!(
                "  ✗ [{}] 失败于第 {} 次, shrink {} 步后最小反例 = {:?}",
                name, i, shrinks, minimal
            );
            return TestResult { ok: false, counterexample: Some(minimal), tries: i, shrinks };
        }
    }
    println!("  ✓ [{}] {} 次随机测试全部通过", name, n_tries);
    TestResult { ok: true, counterexample: None, tries: n_tries, shrinks: 0 }
}

fn shrink_minimize<T: Arbitrary, F: Fn(&T) -> bool>(initial: T, prop: &F) -> (T, usize) {
    let mut best = initial;
    let mut steps = 0;
    loop {
        let candidates = best.shrink();
        let mut improved = false;
        for c in candidates {
            if !prop(&c) {
                best = c;
                steps += 1;
                improved = true;
                break;
            }
        }
        if !improved { return (best, steps); }
    }
}

// ============================================================
// 4) 被测函数
// ============================================================
fn my_reverse<T: Clone>(xs: &[T]) -> Vec<T> {
    let mut out = xs.to_vec();
    out.reverse();
    out
}

fn my_sort(xs: &[i32]) -> Vec<i32> {
    let mut v = xs.to_vec();
    v.sort();
    v
}

/// 故意写错的"求和": 忽略了负数 —— 被 shrink 抓个正着
fn buggy_sum(xs: &[i32]) -> i32 {
    xs.iter().filter(|&&x| x > 0).sum()
}

fn main() {
    println!("=== Rust Demo 22: 手写迷你 proptest ===\n");

    println!("-- 1) 性质: reverse(reverse(xs)) == xs --");
    forall::<Vec<i32>, _>("reverse involution", 0xdeadbeef, 200, |xs| {
        my_reverse(&my_reverse(xs)) == *xs
    });

    println!("\n-- 2) 性质: 排序后保持单调 --");
    forall::<Vec<i32>, _>("sort is monotonic", 0xbadf00d, 200, |xs| {
        let sorted = my_sort(xs);
        sorted.windows(2).all(|w| w[0] <= w[1])
    });

    println!("\n-- 3) 性质: sort 保持元素集合不变 --");
    forall::<Vec<i32>, _>("sort preserves multiset", 0xfeedface, 200, |xs| {
        let mut a = xs.clone();
        let mut b = my_sort(xs);
        a.sort();
        b.sort();
        a == b
    });

    println!("\n-- 4) 故意错的 buggy_sum: 应等于标准 sum --");
    let _ = forall::<Vec<i32>, _>("buggy_sum == std sum", 0xc0ffee, 200, |xs| {
        let expected: i32 = xs.iter().copied().sum();
        buggy_sum(xs) == expected
    });

    println!("\n=== 关键思想 ===");
    println!("  - Arbitrary 提供输入, 不靠人类列 edge case, 靠统计覆盖");
    println!("  - Property 是对**所有输入**成立的断言, 比单元测试强得多");
    println!("  - Shrinking 把几百元素的反例压成 2~3 元素, 定位 bug 非常快");
    println!("  - 真实 crate: proptest / quickcheck, 核心思路和本 Demo 完全一致");
    println!("  - 和状态机测试配合: proptest-state-machine 能测真实并发系统");
}
