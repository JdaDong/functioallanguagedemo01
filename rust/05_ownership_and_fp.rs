/// Rust 函数式编程 Demo 5: 所有权 × FP — Fn / FnMut / FnOnce
///
/// Rust 最独特的一点：高阶函数 API 并不是只有一个 "Function" 类型，
/// 而是三个 trait，分别描述"闭包如何对待捕获的环境"：
///
///   Fn       —— 只读借用环境，可被多次调用
///   FnMut    —— 可变借用环境，可被多次调用但需要 &mut self
///   FnOnce   —— 按值消耗环境（move），只能被调用一次
///
/// 这三者是 Rust 在保证零成本抽象的前提下兼容 FP 的核心机制。
/// 本 Demo 手写三个高阶函数分别要求它们，直观感受它们的边界。

fn main() {
    println!("=== 1. Fn: 只读借用环境（最像纯 FP）===");
    let base = 10;
    // `move` 可加可不加；不加时闭包以借用形式捕获 base
    let add_base = |x: i32| x + base;
    println!("  apply_fn(5)  = {}", apply_fn(5, add_base));
    println!("  apply_fn(7)  = {}", apply_fn(7, add_base));     // 可重复调用
    println!("  base 仍可用: {}", base);                         // 未被 move

    println!("\n=== 2. FnMut: 需要可变环境（内部状态累加器）===");
    let mut seen: Vec<i32> = Vec::new();
    // 这里闭包借用 seen 的 &mut，每次调用都往里 push
    let mut push_seen = |x: i32| { seen.push(x); seen.len() };
    apply_fn_mut(&mut push_seen, &[1, 2, 3, 4]);
    println!("  累计: {:?}", seen);

    println!("\n=== 3. FnOnce: 按值 move，只能调用一次 ===");
    let banner = String::from("[banner]");
    // move 强制闭包获得 banner 所有权
    let consume = move || {
        println!("  打印横幅: {}", banner);
        banner                      // 把 banner move 出去
    };
    let ret = apply_fn_once(consume);
    println!("  闭包返回值长度: {}", ret.len());
    // println!("{}", banner);      // 编译错误：banner 已被 move

    println!("\n=== 4. 返回闭包：impl Fn vs Box<dyn Fn> ===");
    // 静态分发（零成本）：大小编译期已知
    let adder  = make_adder_static(100);
    let muler  = make_muler_static(3);
    println!("  static adder(1) = {}", adder(1));
    println!("  static muler(4) = {}", muler(4));

    // 动态分发（可放进 Vec、可运行期选择）：堆分配 + vtable
    let ops: Vec<Box<dyn Fn(i32) -> i32>> = vec![
        make_op_boxed("+1"),
        make_op_boxed("*2"),
        make_op_boxed("^2"),
    ];
    let r: Vec<i32> = ops.iter().map(|f| f(5)).collect();
    println!("  dyn  管道(5) = {:?}", r);

    println!("\n=== 5. 所有权如何天然推向 FP 风格 ===");
    let nums = vec![1, 2, 3, 4, 5];
    // 链式 iterator：每一步都只"借"，最后 collect 才产生新 Vec
    let out: Vec<i32> = nums.iter().filter(|&&x| x % 2 == 1).map(|&x| x * x).collect();
    println!("  原始: {:?}", nums);
    println!("  过滤并平方: {:?}", out);
    println!("  原始仍可用（没被消耗）: {:?}", nums);

    println!("\n=== 总结 ===");
    println!("  Rust 把 FP 的\"纯/不可变/组合\"直觉");
    println!("  变成了\"默认借用、修改要显式 &mut、move 要显式 move\"的静态规则，");
    println!("  从而在保留 map/filter/fold 直觉的同时，彻底消除了数据竞争。");
}

// ---------- 三种高阶函数签名 ----------

fn apply_fn<F: Fn(i32) -> i32>(x: i32, f: F) -> i32 { f(x) }

fn apply_fn_mut<F: FnMut(i32) -> usize>(f: &mut F, xs: &[i32]) {
    for &x in xs { let n = f(x); println!("  push({}) -> size={}", x, n); }
}

fn apply_fn_once<F: FnOnce() -> String>(f: F) -> String { f() }

// ---------- 返回闭包：impl Fn vs Box<dyn Fn> ----------

// 静态分发：类型在编译期完全确定
fn make_adder_static(a: i32) -> impl Fn(i32) -> i32 {
    move |x| x + a
}

fn make_muler_static(k: i32) -> impl Fn(i32) -> i32 {
    move |x| x * k
}

// 动态分发：可以放进同一个 Vec，运行期决定
fn make_op_boxed(tag: &str) -> Box<dyn Fn(i32) -> i32> {
    match tag {
        "+1" => Box::new(|x| x + 1),
        "*2" => Box::new(|x| x * 2),
        "^2" => Box::new(|x| x * x),
        _    => Box::new(|x| x),
    }
}
