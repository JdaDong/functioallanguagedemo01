/// Rust 函数式编程 Demo 11: Trait Object vs impl Trait —— 静态 vs 动态分发
///
/// Rust 里"用 trait 作返回/参数类型"有两种完全不同的写法，新手必踩的坑。
///
///   - impl Trait       => 静态分发，编译期单态化，零成本抽象
///   - Box<dyn Trait>   => 动态分发，运行时查 vtable，有指针间接跳转
///
/// 这个 Demo 把两种写法并排对比，清楚展现：什么时候该用哪个。
///
/// 编译运行:
///   rustc 11_trait_object_vs_impl.rs -O -o /tmp/demo11 && /tmp/demo11

// ============================================================
// 1) 同一个 trait：Greet
// ============================================================
trait Greet {
    fn hello(&self) -> String;
}

struct English;
struct Chinese;
struct Japanese;

impl Greet for English {
    fn hello(&self) -> String {
        "hello".into()
    }
}
impl Greet for Chinese {
    fn hello(&self) -> String {
        "你好".into()
    }
}
impl Greet for Japanese {
    fn hello(&self) -> String {
        "こんにちは".into()
    }
}

// ============================================================
// 2) 静态分发：impl Trait —— 编译期单态化
// ============================================================
// 编译器会为每个具体类型生成一份独立的 say_static 副本：
//   say_static::<English>, say_static::<Chinese>, say_static::<Japanese>
// 调用时直接 inline，没有任何运行时代价。
fn say_static<G: Greet>(g: G) -> String {
    format!("[static] {}", g.hello())
}

// impl Trait 作返回值：同样是静态分发
// 注意：一个函数只能返回"一种"具体类型，所以下面这样是不行的：
//   fn pick_wrong(flag: bool) -> impl Greet {
//       if flag { English } else { Chinese }  // ❌ 类型不一致
//   }
fn make_english() -> impl Greet {
    English
}

// ============================================================
// 3) 动态分发：Box<dyn Trait> —— vtable 运行时派发
// ============================================================
// 这里能把异构类型塞进同一个 Vec，代价是每次调用多一次间接跳转。
fn say_dynamic(g: &dyn Greet) -> String {
    format!("[dyn] {}", g.hello())
}

fn pick_dyn(flag: u8) -> Box<dyn Greet> {
    match flag {
        0 => Box::new(English),
        1 => Box::new(Chinese),
        _ => Box::new(Japanese),
    }
}

// ============================================================
// 4) 一个容易被忽略的细节：object safety
// ============================================================
// 并不是所有 trait 都能做成 dyn Trait，必须满足 "object safe"：
//   - 方法不能有泛型参数
//   - 方法不能返回 Self
//   - Sized 不能作为 Self 的约束
//
// 下面这个 trait 就不是 object safe 的:
trait NotObjectSafe {
    fn clone_self(&self) -> Self
    where
        Self: Sized;  // 用 where Self: Sized 把这个方法从 vtable 中剔除
    fn name(&self) -> &'static str;
}

struct Foo;
impl NotObjectSafe for Foo {
    fn clone_self(&self) -> Self {
        Foo
    }
    fn name(&self) -> &'static str {
        "Foo"
    }
}

fn main() {
    println!("=== Rust Demo 11: Trait Object vs impl Trait ===\n");

    // --- 静态分发 ---
    println!("-- 静态分发 (impl Trait) --");
    println!("  {}", say_static(English));
    println!("  {}", say_static(Chinese));
    println!("  {}", say_static(Japanese));
    println!("  make_english().hello() = {}", make_english().hello());

    // --- 动态分发 ---
    println!("\n-- 动态分发 (Box<dyn Trait>) --");
    let zoo: Vec<Box<dyn Greet>> = vec![
        Box::new(English),
        Box::new(Chinese),
        Box::new(Japanese),
    ];
    for g in &zoo {
        println!("  {}", say_dynamic(g.as_ref()));
    }

    let picked = pick_dyn(1);
    println!("  pick_dyn(1) = {}", picked.hello());

    // --- object safety 演示 ---
    println!("\n-- object safety --");
    let foo = Foo;
    // 只能调用不需要 Self: Sized 的方法
    let dyn_ref: &dyn NotObjectSafe = &foo;
    println!("  name via dyn = {}", dyn_ref.name());
    // dyn_ref.clone_self(); // ❌ 编译错误: 方法需要 Sized
    println!("  clone_self 只能在已知具体类型时调用: {}", foo.clone_self().name());

    println!("\n=== 决策指南 ===");
    println!("  默认用 impl Trait:    性能零开销, 编译期多态");
    println!("  异构集合/插件系统:    Box<dyn Trait>, 统一类型");
    println!("  返回多种实现:         必须用 Box<dyn Trait>, impl Trait 做不到");
    println!("  trait 方法带泛型:     只能用泛型函数, 不能做 dyn");
    println!("  动态插件/运行时决定:  dyn Trait 的主场");
}
