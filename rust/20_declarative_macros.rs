/// Rust 函数式编程 Demo 20: 声明宏 macro_rules!
///
/// Rust 的元编程有两层：
///   - 声明宏 (Declarative Macros, macro_rules!) —— 模式匹配式的 token 树替换
///   - 过程宏 (Procedural Macros, #[derive] / attribute / bang)    —— 下个 Demo 讲思路
///
/// 这个 Demo 集中演示 macro_rules! 能干的典型活儿：
///   1) 可变参数 (variadic)
///   2) 递归展开
///   3) 模板化代码生成（常量、函数、impl 块）
///   4) 内部宏 / 公开宏的导出与命名空间 (#[macro_export])
///
/// 编译运行:
///   rustc 20_declarative_macros.rs -O -o /tmp/demo20 && /tmp/demo20

// ============================================================
// 1) 可变参数 —— 最小可用的 vec!
// ============================================================
macro_rules! my_vec {
    // 基线: 空
    () => { Vec::new() };
    // 带逗号可重复的表达式列表
    ( $( $x:expr ),+ $(,)? ) => {{
        let mut v = Vec::new();
        $( v.push($x); )+
        v
    }};
}

// ============================================================
// 2) 递归展开 —— 实现 min!(a, b, c, ...) 取最小值
// ============================================================
macro_rules! min {
    ($x:expr) => { $x };
    ($x:expr, $($rest:expr),+ $(,)?) => {{
        let tail = min!($($rest),+);
        if $x < tail { $x } else { tail }
    }};
}

// ============================================================
// 3) HashMap 字面量
// ============================================================
macro_rules! map {
    () => { ::std::collections::HashMap::new() };
    ( $( $k:expr => $v:expr ),+ $(,)? ) => {{
        let mut m = ::std::collections::HashMap::new();
        $( m.insert($k, $v); )+
        m
    }};
}

// ============================================================
// 4) 代码生成 —— 为多个结构体自动实现 trait
//    这是 derive 宏做不了、但 macro_rules! 能做到的场景
// ============================================================
trait Greet { fn greet(&self) -> String; }

macro_rules! impl_greet_for {
    // 一次声明多个结构体 + 多种打招呼话术
    ( $( $t:ident => $msg:expr ),+ $(,)? ) => {
        $(
            struct $t;
            impl Greet for $t {
                fn greet(&self) -> String {
                    format!("{}: {}", stringify!($t), $msg)
                }
            }
        )+
    };
}
impl_greet_for!(
    English  => "hello",
    Chinese  => "你好",
    Japanese => "こんにちは",
);

// ============================================================
// 5) DSL 风格 —— JSON-like 字面量
// ============================================================
#[derive(Debug)]
#[allow(dead_code)]
enum Json {
    Null,
    Bool(bool),
    Num(f64),
    Str(String),
    Arr(Vec<Json>),
    Obj(Vec<(String, Json)>),
}

macro_rules! json {
    (null)            => { Json::Null };
    (true)            => { Json::Bool(true) };
    (false)           => { Json::Bool(false) };
    // 数字 / 字符串
    ($n:literal)      => {{
        // 利用 TryInto 来判断字面量类型；简单版用两条路径：
        json_literal!($n)
    }};
    // 数组
    ( [ $( $elem:tt ),* $(,)? ] ) => {
        Json::Arr(vec![ $( json!($elem) ),* ])
    };
    // 对象
    ( { $( $k:tt : $v:tt ),* $(,)? } ) => {
        Json::Obj(vec![ $( ( String::from($k), json!($v) ) ),* ])
    };
}

macro_rules! json_literal {
    ($s:literal) => {{
        // 通过 const 泛型路径会更干净, 这里简单粗暴:
        let raw = stringify!($s);
        if raw.starts_with('"') {
            Json::Str(raw.trim_matches('"').to_string())
        } else {
            Json::Num(raw.parse::<f64>().unwrap_or(0.0))
        }
    }};
}

// ============================================================
// 6) 一个"调试用 dbg!" 的实现（体会 macro 的 token 捕获）
// ============================================================
macro_rules! my_dbg {
    ($e:expr) => {{
        let val = $e;
        eprintln!("[dbg] {} = {:?}  @ {}:{}", stringify!($e), val, file!(), line!());
        val
    }};
}

fn main() {
    println!("=== Rust Demo 20: 声明宏 macro_rules! ===\n");

    // --- 1) my_vec ---
    println!("-- 1) my_vec! 可变参数 --");
    let v: Vec<i32> = my_vec![1, 2, 3, 4, 5];
    println!("  my_vec!(1..5) = {:?}", v);

    // --- 2) min ---
    println!("\n-- 2) min! 递归 --");
    println!("  min!(3,1,4,1,5,9,2,6) = {}", min!(3, 1, 4, 1, 5, 9, 2, 6));

    // --- 3) map ---
    println!("\n-- 3) map! 字面量 --");
    let m: std::collections::HashMap<_, _> = map! {
        "one" => 1,
        "two" => 2,
        "three" => 3,
    };
    let mut entries: Vec<_> = m.into_iter().collect();
    entries.sort();
    println!("  map!(...) = {:?}", entries);

    // --- 4) 多结构体 impl ---
    println!("\n-- 4) 代码生成: impl_greet_for! --");
    println!("  {}", English.greet());
    println!("  {}", Chinese.greet());
    println!("  {}", Japanese.greet());

    // --- 5) DSL: json! ---
    println!("\n-- 5) DSL: json! --");
    let doc = json!({
        "name": "alice",
        "age":  30,
        "tags": ["admin", "founder"],
        "deleted": false,
    });
    println!("  {:?}", doc);

    // --- 6) my_dbg ---
    println!("\n-- 6) my_dbg! --");
    let x = my_dbg!(1 + 2 + 3);
    println!("  x = {}", x);

    println!("\n=== macro_rules! 能力边界 ===");
    println!("  能做: token 模式匹配 / 递归 / 字面量捕获 / 代码生成");
    println!("  不能做: 看不到 AST / 无法按类型分派 / 无法做复杂推理");
    println!("  要做复杂元编程 → 下一关: proc macro (derive / attribute / bang)");
}
