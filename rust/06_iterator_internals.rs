/// Rust 函数式编程 Demo 6: Iterator 原理 —— 惰性 & 零成本
///
/// Rust 的 `Iterator` trait 只需实现一个方法：`next`。
/// 其余的 map / filter / take / fold 等全部是基于它的默认实现。
/// 因此：
///   1. 任何我们自己写的 struct 实现 Iterator 后，立刻获得整套 FP API
///   2. 整条链路是惰性的：不 collect / sum / for 就不会计算
///   3. 编译器能把链路内联成一个循环，运行期几乎没有额外代价
///
/// 本 Demo：
///   a) 手写一个 Counter 迭代器
///   b) 手写一个无穷斐波那契迭代器
///   c) 手写一个自定义适配器 `SlidingSum`
///   d) 用 peekable / fuse / chain / zip 做组合
///   e) 观察惰性

fn main() {
    println!("=== 1. 手写最小 Iterator: Counter(0..5) ===");
    let sum: u32 = Counter::new(5).sum();                // sum 是 Iterator 默认方法
    let doubled: Vec<u32> = Counter::new(5).map(|x| x * 2).collect();
    println!("  sum     = {}", sum);
    println!("  doubled = {:?}", doubled);

    println!("\n=== 2. 无穷斐波那契 + take ===");
    let fibs: Vec<u64> = FibIter::new().take(10).collect();
    println!("  前 10 项: {:?}", fibs);
    // 注意：不写 take 的话 FibIter 会无限跑，这就是惰性救了我们

    println!("\n=== 3. 自定义适配器: SlidingSum(window=3) ===");
    let xs = vec![1, 2, 3, 4, 5, 6];
    let out: Vec<i64> = SlidingSum::new(xs.into_iter().map(|x| x as i64), 3).collect();
    println!("  滑动窗和: {:?}", out); // [6, 9, 12, 15]

    println!("\n=== 4. peekable / fuse / chain / zip ===");
    let mut p = (1..=5).peekable();
    println!("  peek      = {:?}", p.peek());   // Some(1)，不推进
    println!("  next      = {:?}", p.next());   // Some(1)

    let chained: Vec<i32> = (1..=3).chain(10..=12).collect();
    println!("  chain     = {:?}", chained);

    let zipped: Vec<(i32, char)> = (1..).zip("abcd".chars()).collect();
    println!("  zip 到短的结束: {:?}", zipped);

    println!("\n=== 5. 观察惰性：side-effect 只在被消耗时发生 ===");
    let pipeline = (1..=5).map(|x| {
        println!("    [map 运行] x={}", x);
        x * x
    });
    println!("  此时还没看到任何 [map 运行] 打印");
    let first_even_square = pipeline.filter(|x| x % 2 == 0).next();
    println!("  第一个偶数平方 = {:?}", first_even_square);
    println!("  （注意：map 只对被过滤到的元素真正执行）");
}

// =================================================================
// a) Counter —— 最简 Iterator 实现
// =================================================================

struct Counter { cur: u32, end: u32 }

impl Counter {
    fn new(end: u32) -> Self { Counter { cur: 0, end } }
}

impl Iterator for Counter {
    type Item = u32;
    fn next(&mut self) -> Option<u32> {
        if self.cur < self.end { let v = self.cur; self.cur += 1; Some(v) } else { None }
    }
}

// =================================================================
// b) 无穷斐波那契
// =================================================================

struct FibIter { a: u64, b: u64 }

impl FibIter {
    fn new() -> Self { FibIter { a: 0, b: 1 } }
}

impl Iterator for FibIter {
    type Item = u64;
    fn next(&mut self) -> Option<u64> {
        let out = self.a;
        let nxt = self.a + self.b;
        self.a = self.b;
        self.b = nxt;
        Some(out)                 // 永远返回 Some —— 无穷迭代器
    }
}

// =================================================================
// c) 自定义适配器 SlidingSum
// =================================================================

struct SlidingSum<I: Iterator<Item = i64>> {
    inner:   I,
    window:  usize,
    buf:     std::collections::VecDeque<i64>,
    primed:  bool,
}

impl<I: Iterator<Item = i64>> SlidingSum<I> {
    fn new(inner: I, window: usize) -> Self {
        SlidingSum { inner, window, buf: std::collections::VecDeque::new(), primed: false }
    }
}

impl<I: Iterator<Item = i64>> Iterator for SlidingSum<I> {
    type Item = i64;
    fn next(&mut self) -> Option<i64> {
        if !self.primed {
            for _ in 0..self.window {
                match self.inner.next() {
                    Some(v) => self.buf.push_back(v),
                    None    => return None, // 数据不够凑一个窗口
                }
            }
            self.primed = true;
            return Some(self.buf.iter().sum());
        }
        match self.inner.next() {
            Some(v) => { self.buf.pop_front(); self.buf.push_back(v); Some(self.buf.iter().sum()) }
            None    => None,
        }
    }
}
