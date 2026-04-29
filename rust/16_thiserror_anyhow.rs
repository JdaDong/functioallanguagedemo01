/// Rust 函数式编程 Demo 16: thiserror / anyhow 风格 —— 错误处理工程化
///
/// 04 号 Demo 演示了 Result 链式处理，但真实项目里有两个新问题：
///
///   1) 错误类型很多（IO / 解析 / 业务规则 / 数据库），怎么优雅地汇总？
///   2) 库作者 vs 应用作者想要的东西不一样：
///        - 库作者: 精确的错误种类（用 thiserror 做 sum type）
///        - 应用作者: 只关心"能不能往上扔, 带上下文, 最后打印"（用 anyhow）
///
/// 为了不引第三方依赖，这里手写 thiserror / anyhow 的核心能力。
///
/// 编译运行:
///   rustc 16_thiserror_anyhow.rs -O -o /tmp/demo16 && /tmp/demo16

use std::error::Error as StdError;
use std::fmt;
use std::num::ParseIntError;

// ============================================================
// 1) 库作者风格: thiserror ≈ enum + derive(Error)
// ============================================================
#[derive(Debug)]
enum AppError {
    NotFound { key: String },
    InvalidInput(String),
    ParseFailed(ParseIntError),
    BusinessRule { rule: &'static str, detail: String },
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::NotFound { key }     => write!(f, "NotFound: {}", key),
            AppError::InvalidInput(s)      => write!(f, "InvalidInput: {}", s),
            AppError::ParseFailed(e)       => write!(f, "ParseFailed: {}", e),
            AppError::BusinessRule { rule, detail } =>
                write!(f, "BusinessRule[{}]: {}", rule, detail),
        }
    }
}

// 关键：impl Error —— 让它能和标准错误体系互通
impl StdError for AppError {
    fn source(&self) -> Option<&(dyn StdError + 'static)> {
        match self {
            AppError::ParseFailed(e) => Some(e),
            _ => None,
        }
    }
}

// 关键：impl From —— 让 `?` 能自动 cast
// thiserror 的 #[from] 就是帮你写这个
impl From<ParseIntError> for AppError {
    fn from(e: ParseIntError) -> Self { AppError::ParseFailed(e) }
}

// ============================================================
// 2) 应用作者风格: anyhow ≈ Box<dyn Error> + context
// ============================================================
pub struct AnyError {
    inner: Box<dyn StdError + Send + Sync + 'static>,
    context: Vec<String>,
}

impl AnyError {
    fn new<E: StdError + Send + Sync + 'static>(e: E) -> Self {
        AnyError { inner: Box::new(e), context: Vec::new() }
    }
    fn with_context(mut self, msg: impl Into<String>) -> Self {
        self.context.push(msg.into());
        self
    }
}

impl fmt::Debug for AnyError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        writeln!(f, "{}", self.inner)?;
        for (i, c) in self.context.iter().rev().enumerate() {
            writeln!(f, "  {}: {}", i, c)?;
        }
        Ok(())
    }
}

impl<E: StdError + Send + Sync + 'static> From<E> for AnyError {
    fn from(e: E) -> Self { AnyError::new(e) }
}

// Context trait: 让任意 Result 都能 `.context("...")`
trait Context<T> {
    fn context(self, msg: impl Into<String>) -> Result<T, AnyError>;
}
impl<T, E: StdError + Send + Sync + 'static> Context<T> for Result<T, E> {
    fn context(self, msg: impl Into<String>) -> Result<T, AnyError> {
        self.map_err(|e| AnyError::new(e).with_context(msg))
    }
}

// ============================================================
// 3) 一个小业务场景：解析配置行 "key=value"
// ============================================================
fn parse_kv(line: &str) -> Result<(String, i64), AppError> {
    let (k, v) = line
        .split_once('=')
        .ok_or_else(|| AppError::InvalidInput(format!("expected 'key=value', got {:?}", line)))?;

    let v: i64 = v.trim().parse()?; // ParseIntError => AppError via From

    if v < 0 {
        return Err(AppError::BusinessRule {
            rule: "non_negative",
            detail: format!("value of {} must be >= 0, got {}", k, v),
        });
    }

    Ok((k.trim().to_string(), v))
}

fn find_user(id: i64) -> Result<String, AppError> {
    match id {
        1 => Ok("alice".into()),
        2 => Ok("bob".into()),
        _ => Err(AppError::NotFound { key: format!("user:{}", id) }),
    }
}

// ============================================================
// 4) 应用层的"胶水"函数：把若干库错误混在一起用 AnyError
// ============================================================
fn build_greeting(config_line: &str, user_id: i64) -> Result<String, AnyError> {
    let (key, value) = parse_kv(config_line)
        .context(format!("解析配置行失败: line={:?}", config_line))?;

    let user = find_user(user_id)
        .context(format!("查找用户失败: user_id={}", user_id))?;

    Ok(format!("{}: {} = {}", user, key, value))
}

fn main() {
    println!("=== Rust Demo 16: thiserror & anyhow 风格错误处理 ===\n");

    // --- 成功路径 ---
    println!("-- 成功路径 --");
    match build_greeting("retries = 3", 1) {
        Ok(s)  => println!("  ok => {}", s),
        Err(e) => println!("  err => {:?}", e),
    }

    // --- 失败路径: 解析错误 ---
    println!("\n-- 失败: 解析错误 --");
    if let Err(e) = build_greeting("retries = abc", 1) {
        println!("{:?}", e);
    }

    // --- 失败路径: 业务规则 ---
    println!("-- 失败: 业务规则 --");
    if let Err(e) = build_greeting("retries = -1", 1) {
        println!("{:?}", e);
    }

    // --- 失败路径: 用户不存在 ---
    println!("-- 失败: 用户不存在 --");
    if let Err(e) = build_greeting("retries = 3", 999) {
        println!("{:?}", e);
    }

    // --- 底层错误遍历（source chain）---
    println!("-- 用 source() 追溯 root cause --");
    let err: AppError = "bad".parse::<i64>().unwrap_err().into();
    let mut cur: Option<&dyn StdError> = Some(&err);
    let mut depth = 0;
    while let Some(e) = cur {
        println!("  [{}] {}", depth, e);
        cur = e.source();
        depth += 1;
    }

    println!("\n=== 经验法则 ===");
    println!("  写库:    用 thiserror, 定义精确 enum error, 方便上层匹配");
    println!("  写应用:  用 anyhow, 只关心'能不能扔上去 + 加上下文 + 打印'");
    println!("  组合:    库返回 AppError, 应用 fn 返回 anyhow::Result, ? 自动转换");
    println!("  日志:    {{:?}} 打印能看到完整 context chain, {{}} 只看顶层");
}
