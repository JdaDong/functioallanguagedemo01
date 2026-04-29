/// Rust 函数式编程 Demo 24: cargo workspace 工程骨架（速查）
///
/// 前面 01~23 都是单文件 Demo（方便 rustc 直接跑），
/// 但真实 Rust 项目基本都是 cargo workspace + 多 crate 结构。
/// 本 Demo 不执行任何命令，只把"标准项目长什么样"打印出来，
/// 作为 Rust 工程化的参考卡片。
///
/// 编译运行:
///   rustc 24_cargo_workspace.rs -O -o /tmp/demo24 && /tmp/demo24

fn tree() -> Vec<&'static str> {
    vec![
        "myapp/",
        "├── Cargo.toml                  -- workspace 根清单",
        "├── Cargo.lock",
        "├── rust-toolchain.toml         -- 锁 Rust 版本",
        "├── .cargo/",
        "│   └── config.toml             -- 构建/profile 自定义",
        "├── crates/",
        "│   ├── myapp-core/             -- 纯业务逻辑 (无 IO)",
        "│   │   ├── Cargo.toml",
        "│   │   └── src/lib.rs",
        "│   ├── myapp-adapters/         -- 数据库 / HTTP / 外部适配",
        "│   │   ├── Cargo.toml",
        "│   │   └── src/lib.rs",
        "│   ├── myapp-bin/              -- 可执行入口 (main)",
        "│   │   ├── Cargo.toml",
        "│   │   └── src/main.rs",
        "│   └── myapp-macros/           -- proc macro (proc-macro = true)",
        "│       ├── Cargo.toml",
        "│       └── src/lib.rs",
        "├── tests/                      -- workspace 级集成测试",
        "├── benches/                    -- cargo bench (criterion)",
        "├── examples/                   -- 用户能 cargo run --example foo",
        "├── xtask/                      -- 自定义构建任务 (社区约定)",
        "└── target/                     -- 构建产物 (gitignore)",
    ]
}

fn root_cargo_toml() -> &'static str {
    r#"# Cargo.toml  (workspace 根)
[workspace]
members  = ["crates/*"]
resolver = "2"

[workspace.package]
version      = "0.1.0"
edition      = "2021"
rust-version = "1.75"
license      = "Apache-2.0"
authors      = ["you <you@example.com>"]

[workspace.dependencies]
# 只在一处声明版本，子 crate 用 `dep.workspace = true` 引用
serde       = { version = "1", features = ["derive"] }
tokio       = { version = "1", features = ["full"] }
thiserror   = "1"
anyhow      = "1"
tracing     = "0.1"
tracing-subscriber = "0.3"
axum        = "0.7"
proptest    = "1"

[profile.release]
lto       = "fat"
codegen-units = 1
panic     = "abort"
strip     = "debuginfo"

[profile.dev]
opt-level = 0
debug     = 2
"#
}

fn sub_cargo_toml() -> &'static str {
    r#"# crates/myapp-core/Cargo.toml
[package]
name         = "myapp-core"
version.workspace      = true
edition.workspace      = true
rust-version.workspace = true
license.workspace      = true

[dependencies]
serde.workspace     = true
thiserror.workspace = true
# 只依赖自己内部的 crate:
# myapp-macros = { path = "../myapp-macros" }

[dev-dependencies]
proptest.workspace = true
"#
}

fn cargo_config() -> &'static str {
    r#"# .cargo/config.toml
[alias]
xtask  = "run -p xtask --"
rr     = "run --release"
lint   = "clippy --all-targets --all-features -- -D warnings"
fmt    = "fmt --all"
ci     = ["lint", "test", "doc"]

[build]
rustflags = ["-D", "warnings"]

[net]
git-fetch-with-cli = true
"#
}

fn toolchain() -> &'static str {
    r#"# rust-toolchain.toml
[toolchain]
channel    = "1.82.0"
components = ["rustfmt", "clippy", "rust-analyzer", "rust-src"]
profile    = "minimal"
"#
}

fn ci_snippet() -> &'static str {
    r#"# .github/workflows/ci.yml (摘要)
jobs:
  ci:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - run: cargo fmt --all -- --check
      - run: cargo clippy --all-targets --all-features -- -D warnings
      - run: cargo test --all
      - run: cargo doc --no-deps --all-features
"#
}

fn main() {
    println!("=== Rust Demo 24: cargo workspace 工程骨架 ===\n");

    println!("-- 标准目录树 --");
    for line in tree() { println!("{}", line); }

    println!("\n-- 根 Cargo.toml --\n{}", root_cargo_toml());
    println!("-- 子 crate Cargo.toml --\n{}", sub_cargo_toml());
    println!("-- .cargo/config.toml --\n{}", cargo_config());
    println!("-- rust-toolchain.toml --\n{}", toolchain());
    println!("-- CI 片段 --\n{}", ci_snippet());

    println!("=== 常用命令 ===");
    println!("  cargo build --release         构建全部 crate");
    println!("  cargo test                    跑单元+集成测试");
    println!("  cargo bench                   criterion 基准");
    println!("  cargo clippy -- -D warnings   lint 全开");
    println!("  cargo fmt --all               rustfmt");
    println!("  cargo doc --open              生成并打开文档");
    println!("  cargo audit                   依赖安全扫描 (需装 cargo-audit)");
    println!("  cargo deny check              许可证/版本策略");
    println!("  cargo flamegraph              火焰图 (需装 cargo-flamegraph)");

    println!("\n=== 关键理解 ===");
    println!("  - workspace 把多 crate 共享同一把锁文件和 target 目录, 编译缓存命中率高");
    println!("  - workspace.dependencies 集中声明版本, 避免子 crate 版本漂移");
    println!("  - 六边形架构常见拆法: core (纯业务) / adapters (IO 边界) / bin (入口)");
    println!("  - rust-toolchain.toml 让 CI 和本地自动拉同一个 Rust 版本");
    println!("  - 对标: Scala sbt 多模块, Erlang rebar3 umbrella, Haskell cabal project");
}
