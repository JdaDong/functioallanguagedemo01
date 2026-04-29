/// Rust 函数式编程 Demo 21: proc macro 心智模型（derive 思路）
///
/// 真正的 proc macro 必须放在独立的 `proc-macro = true` crate 里，
/// 所以没法放进单文件 Demo。但它的**心智模型**可以手写出来：
///
///   proc macro 本质就是一个函数:
///      fn derive_X(input: TokenStream) -> TokenStream
///   它在编译期拿到结构体/枚举的 AST, 然后吐出新的代码供编译器接着编译。
///
/// 为了能在单文件里演示, 我们做两件事:
///   1) 提供一个"编译期生成代码"的完整等价写法: macro_rules! 实现 #[derive(Getters)] 的核心效果
///   2) 手写一个"运行时"的 trait 解释器, 展示 derive 宏真实执行的"扫字段 -> 生成 impl"流程
///
/// 并在文档注释里原样给出真实 proc macro 的样板代码, 方便你对照。
///
/// 编译运行:
///   rustc 21_proc_macro_model.rs -O -o /tmp/demo21 && /tmp/demo21

// ============================================================
// 1) 用 macro_rules! 模拟 #[derive(Getters)]
// ============================================================
/// 声明结构体 + 自动生成每个字段的 getter 方法。
macro_rules! define_with_getters {
    (
        $(#[$meta:meta])*
        struct $name:ident {
            $( $field:ident : $ty:ty ),+ $(,)?
        }
    ) => {
        $(#[$meta])*
        struct $name {
            $( $field: $ty ),+
        }

        impl $name {
            $(
                // 为每个字段生成 get_<name> 方法
                pub fn $field(&self) -> &$ty { &self.$field }
            )+
        }
    };
}

define_with_getters! {
    #[derive(Debug)]
    struct User {
        id: u64,
        name: String,
        email: String,
    }
}

// ============================================================
// 2) 用 macro_rules! 模拟 #[derive(Builder)]
// ============================================================
macro_rules! define_with_builder {
    (
        struct $name:ident {
            $( $field:ident : $ty:ty ),+ $(,)?
        }
    ) => {
        #[derive(Debug)]
        struct $name {
            $( $field: $ty ),+
        }

        #[derive(Default)]
        struct Builder {
            $( $field: Option<$ty> ),+
        }

        impl Builder {
            fn new() -> Self { Builder { $( $field: None ),+ } }
            $(
                fn $field(mut self, v: $ty) -> Self {
                    self.$field = Some(v);
                    self
                }
            )+
            fn build(self) -> Result<$name, &'static str> {
                Ok($name {
                    $( $field: self.$field.ok_or(concat!("missing field: ", stringify!($field)))? ),+
                })
            }
        }
    };
}

define_with_builder! {
    struct Request {
        method: String,
        url: String,
        retries: u32,
    }
}

// ============================================================
// 3) 运行时 "derive"：手写"扫描字段 -> 生成行为"
//    —— 展示真 proc macro 背后发生了什么
// ============================================================
/// trait: 返回 "字段名 -> 字段值字符串" 的列表
trait DynReflect {
    fn fields(&self) -> Vec<(&'static str, String)>;
}

/// 运行时 derive 的等价物：不同类型都手动实现 fields()
impl DynReflect for User {
    fn fields(&self) -> Vec<(&'static str, String)> {
        vec![
            ("id",    format!("{}", self.id)),
            ("name",  self.name.clone()),
            ("email", self.email.clone()),
        ]
    }
}
impl DynReflect for Request {
    fn fields(&self) -> Vec<(&'static str, String)> {
        vec![
            ("method",  self.method.clone()),
            ("url",     self.url.clone()),
            ("retries", format!("{}", self.retries)),
        ]
    }
}

/// 一个"通用"的 SQL-INSERT 生成器：只要你 impl DynReflect 就能用
fn to_insert_sql<T: DynReflect>(table: &str, v: &T) -> String {
    let fields = v.fields();
    let cols: Vec<_> = fields.iter().map(|(k, _)| *k).collect();
    let vals: Vec<_> = fields.iter().map(|(_, v)| format!("'{}'", v.replace('\'', "''"))).collect();
    format!("INSERT INTO {} ({}) VALUES ({});", table, cols.join(", "), vals.join(", "))
}

fn main() {
    println!("=== Rust Demo 21: proc macro 心智模型 ===\n");

    // --- define_with_getters! ---
    println!("-- 1) 自动生成 getter --");
    let u = User { id: 1, name: "alice".into(), email: "a@example.com".into() };
    println!("  u.id()    = {}", u.id());
    println!("  u.name()  = {}", u.name());
    println!("  u.email() = {}", u.email());

    // --- define_with_builder! ---
    println!("\n-- 2) 自动生成 builder --");
    let req = Builder::new()
        .method("GET".into())
        .url("https://api.example.com/ping".into())
        .retries(3)
        .build()
        .unwrap();
    println!("  built = {:?}", req);

    // 缺字段演示
    let err = Builder::new().method("GET".into()).build().err();
    println!("  缺字段错误: {:?}", err);

    // --- DynReflect ---
    println!("\n-- 3) 运行时 derive 等价物 --");
    println!("  {}", to_insert_sql("users",    &u));
    println!("  {}", to_insert_sql("requests", &req));

    println!("\n=== 对照真实 proc macro ===");
    println!("  真实 proc macro crate 的典型骨架:");
    println!("    // Cargo.toml");
    println!("    // [lib]");
    println!("    // proc-macro = true");
    println!("    //");
    println!("    // [dependencies]");
    println!("    // syn   = \"2\"       # 解析 Rust AST");
    println!("    // quote = \"1\"       # 生成 Rust token stream");
    println!("    // proc-macro2 = \"1\"");
    println!();
    println!("    #[proc_macro_derive(Getters)]");
    println!("    pub fn derive_getters(input: TokenStream) -> TokenStream {{");
    println!("        let input: DeriveInput = syn::parse(input).unwrap();");
    println!("        // 1) 从 input 抽出字段名 + 类型");
    println!("        // 2) 用 quote!{{ ... }} 生成 impl 块");
    println!("        // 3) 返回 TokenStream 交给编译器接着编译");
    println!("    }}");
    println!();
    println!("  关键差异:");
    println!("    macro_rules!  —— token 模式匹配, 能力弱但不需要额外 crate");
    println!("    proc macro   —— 完整访问 AST, 能力强但必须独立 crate");
    println!("    要做 serde/clap 这种大型 derive, 必须上 proc macro");
}
