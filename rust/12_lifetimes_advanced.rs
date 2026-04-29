/// Rust 函数式编程 Demo 12: 生命周期进阶 —— 'a / 'static / HRTB
///
/// Rust 新手最容易踩的坑就是生命周期。这个 Demo 把三个最常见的进阶主题收齐：
///
///   1) 多生命周期 + 生命周期省略规则
///   2) 'static：什么情况下真的需要？什么情况下其实不必要？
///   3) HRTB（Higher-Ranked Trait Bounds, `for<'a>`）：为什么闭包参数经常被迫写 HRTB
///
/// 编译运行:
///   rustc 12_lifetimes_advanced.rs -O -o /tmp/demo12 && /tmp/demo12

use std::fmt::Debug;

// ============================================================
// 1) 多生命周期：显式标注谁的生命周期短于谁
// ============================================================
// 返回值只和第一个参数的生命周期绑定，第二个参数只是"用一下"
fn longest_first<'a, 'b>(x: &'a str, _hint: &'b str) -> &'a str {
    // 只返回 x，所以返回值的生命周期和 x 一样（'a）
    // 即使 _hint 比 x 更短也无妨
    x
}

// 两个参数都可能被返回，编译器需要保证调用方只能用交集中的生命周期
fn longest_of_two<'a>(x: &'a str, y: &'a str) -> &'a str {
    if x.len() >= y.len() { x } else { y }
}

// ============================================================
// 2) 'static：被反复误解的生命周期
// ============================================================
// 'static 含义：这个引用一直活到程序结束。
// 常见来源：字符串字面量、Box::leak、Arc/Rc 的 static data。
//
// 但 'static **不等于** "从生命周期检查里豁免"，它只是"生命周期 = 程序寿命"。
fn take_static(s: &'static str) {
    println!("  收到 'static 引用: {}", s);
}

// 这里用泛型 + 'static bound 的常见用法：
// "请给我一个任意类型，但它里面不能藏任何短生命周期的借用"
fn spawn_like<T: Send + 'static + Debug>(value: T) -> String {
    // 想象这是 tokio::spawn：任务可能活很久，所以要求 T: 'static
    format!("spawned: {:?}", value)
}

// ============================================================
// 3) HRTB: for<'a> —— 处理"对任意生命周期都成立的闭包"
// ============================================================
// 问题：下面这个函数想接收一个"能处理任何长度借用的闭包"
//        没有 HRTB 的话，编译器不知道该用哪个 'a
fn apply_to_both<F>(f: F) -> (String, String)
where
    // HRTB 读作: "对任意生命周期 'a，F 都能接受一个 &'a str 并返回 String"
    F: for<'a> Fn(&'a str) -> String,
{
    let short = String::from("short");
    let out1 = f(&short);           // 这里 &short 的生命周期比 apply_to_both 短
    let out2 = f("static-literal"); // 这里是 'static
    (out1, out2)
}

// ============================================================
// 4) 结构体里的生命周期
// ============================================================
// 经典场景：结构体只是"引用已有数据"，不拥有它
#[derive(Debug)]
struct Excerpt<'a> {
    part: &'a str,
}

impl<'a> Excerpt<'a> {
    // 生命周期省略第一条规则：&self 返回的引用 == self 的生命周期
    fn first_word(&self) -> &str {
        self.part.split_whitespace().next().unwrap_or("")
    }

    // 显式标注版本（和上面等价，展示编译器怎么展开省略规则）
    #[allow(dead_code)]
    fn first_word_explicit<'b>(&'b self) -> &'b str {
        self.part.split_whitespace().next().unwrap_or("")
    }
}

// ============================================================
// 5) 常见陷阱：想把短命借用塞进 'static 是不可能的
// ============================================================
fn pitfall_demo() {
    // 这样不行：
    //   let s: String = String::from("hi");
    //   take_static(&s);  // ❌ s 只活到函数末尾, 不是 'static
    //
    // 常见 workaround: Box::leak —— 故意泄漏内存换 'static
    let s: String = String::from("boxed-leaked");
    let leaked: &'static str = Box::leak(s.into_boxed_str());
    take_static(leaked);
    println!("  (Box::leak 是故意不回收, 一般只在程序初始化阶段用)");
}

fn main() {
    println!("=== Rust Demo 12: 生命周期进阶 ===\n");

    // --- 多生命周期 ---
    println!("-- 多生命周期标注 --");
    let long = String::from("long-lived-string");
    let hint = String::from("hint");
    let r = longest_first(&long, &hint);
    println!("  longest_first = {:?}", r);
    println!("  longest_of_two = {:?}", longest_of_two("apple", "banana"));

    // --- 'static ---
    println!("\n-- 'static 的正确姿势 --");
    take_static("我是字面量, 天生 'static");
    println!("  {}", spawn_like(42_i32));
    println!("  {}", spawn_like(String::from("owned, 不借用外部, 满足 'static")));
    pitfall_demo();

    // --- HRTB ---
    println!("\n-- HRTB: for<'a> --");
    let (a, b) = apply_to_both(|s: &str| format!("<{}>", s));
    println!("  apply_to_both => {:?}, {:?}", a, b);

    // --- 结构体借用 ---
    println!("\n-- 结构体里的生命周期 --");
    let novel = String::from("Call me Ishmael. Some years ago...");
    let ex = Excerpt { part: &novel };
    println!("  excerpt.first_word() = {}", ex.first_word());
    println!("  excerpt = {:?}", ex);

    println!("\n=== 关键理解 ===");
    println!("  - 生命周期标注不是'让引用活得更久', 而是'告诉编译器谁活得至少和谁一样久'");
    println!("  - 'static 表示引用能活到程序结束, 绝大多数时候你只需要 owned 数据");
    println!("  - tokio::spawn / std::thread::spawn 要 'static, 是因为任务本身会跨越调用者生命周期");
    println!("  - HRTB 的直觉: '不管借多短都能处理', 常见于高阶函数/闭包签名");
}
