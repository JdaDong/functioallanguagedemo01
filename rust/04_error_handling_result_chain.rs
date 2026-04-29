/// Rust 函数式编程 Demo 4: Result 链式错误处理
///
/// 对标 Haskell 的 Either / Scala 的 Either。
/// Rust 没有异常，所有可恢复错误都用 `Result<T, E>` 表达，
/// 配合 `?` 运算符可以写出和 Haskell do-notation 一样干净的错误管道。
///
/// 本 Demo 演示：
///   1. 自定义 Error 枚举 + From 自动转换
///   2. `?` 运算符串联多步计算
///   3. map / map_err / and_then / or_else 函数式组合
///   4. collect::<Result<Vec<_>, _>> 把 Vec<Result> 翻转为 Result<Vec>
///   5. 和 Haskell / Scala 的对照直觉

use std::fmt;
use std::num::ParseIntError;

// ========== 自定义错误类型 ==========

#[derive(Debug)]
enum AppError {
    ParseFailed(String),
    NotPositive(i64),
    Overflow,
    NotFound(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            AppError::ParseFailed(s) => write!(f, "解析失败: {}", s),
            AppError::NotPositive(n) => write!(f, "值必须为正数，得到 {}", n),
            AppError::Overflow       => write!(f, "算术溢出"),
            AppError::NotFound(k)    => write!(f, "键未找到: {}", k),
        }
    }
}

// 让 `?` 能自动把 ParseIntError 转成 AppError
impl From<ParseIntError> for AppError {
    fn from(e: ParseIntError) -> Self { AppError::ParseFailed(e.to_string()) }
}

// ========== 业务函数 ==========

fn parse_positive(s: &str) -> Result<i64, AppError> {
    let n: i64 = s.trim().parse()?;        // ParseIntError 自动转 AppError
    if n <= 0 { Err(AppError::NotPositive(n)) } else { Ok(n) }
}

fn checked_square(n: i64) -> Result<i64, AppError> {
    n.checked_mul(n).ok_or(AppError::Overflow)
}

fn lookup<'a>(table: &'a [(&'a str, i64)], key: &str) -> Result<i64, AppError> {
    table.iter()
        .find(|(k, _)| *k == key)
        .map(|(_, v)| *v)
        .ok_or_else(|| AppError::NotFound(key.to_string()))
}

// 链式：解析 -> 查表 -> 平方 -> 返回
fn pipeline(raw: &str, table: &[(&str, i64)]) -> Result<i64, AppError> {
    let n   = parse_positive(raw)?;        // 第 1 步
    let key = format!("k{}", n);
    let v   = lookup(table, &key)?;        // 第 2 步
    let sq  = checked_square(v)?;          // 第 3 步
    Ok(sq + n)
}

fn main() {
    println!("=== 1. `?` 运算符串联 ===");
    let table = [("k1", 10), ("k2", 20), ("k3", 30)];

    for raw in ["1", "2", "abc", "-5", "99"] {
        match pipeline(raw, &table) {
            Ok(v)  => println!("  pipeline({:>4}) -> Ok({})", raw, v),
            Err(e) => println!("  pipeline({:>4}) -> Err({})", raw, e),
        }
    }

    println!("\n=== 2. map / map_err 组合 ===");
    // map 只改 Ok，map_err 只改 Err
    let r: Result<String, AppError> = parse_positive("42")
        .map(|n| format!("得到 {}", n))
        .map_err(|e| { println!("    上报监控: {}", e); e });
    println!("  map 结果: {:?}", r);

    println!("\n=== 3. and_then / or_else 组合 ===");
    // and_then = flatMap（Ok 才继续），对照 Haskell >>=
    let r = parse_positive("7")
        .and_then(checked_square)
        .and_then(|n| if n > 40 { Ok(n) } else { Err(AppError::NotPositive(n)) });
    println!("  and_then 链: {:?}", r);

    // or_else = 错误分支恢复
    let r: Result<i64, AppError> = parse_positive("oops")
        .or_else(|_| Ok(0));
    println!("  or_else 恢复: {:?}", r);

    println!("\n=== 4. collect::<Result<Vec<_>, _>> 翻转 ===");
    // Vec<Result<T, E>>  ->  Result<Vec<T>, E>
    // 有一个 Err 就整体 Err；全部 Ok 才汇聚成 Vec
    let all_ok: Result<Vec<i64>, _> =
        ["1", "2", "3"].iter().map(|s| parse_positive(s)).collect();
    println!("  全成功: {:?}", all_ok);

    let one_bad: Result<Vec<i64>, _> =
        ["1", "oops", "3"].iter().map(|s| parse_positive(s)).collect();
    println!("  有失败: {:?}", one_bad);

    println!("\n=== 5. 与 Haskell / Scala 的直觉对照 ===");
    println!("  Rust  x?            === Haskell  <-        === Scala  for{{ x <- }}");
    println!("  Result.map          === Haskell  fmap      === Scala  Either.map");
    println!("  Result.and_then     === Haskell  >>=       === Scala  Either.flatMap");
    println!("  Result.map_err      === Haskell  first     === Scala  Either.leftMap");
    println!("  From<E1> for E2     === Haskell  MonadError === Scala implicit Conv");
}
