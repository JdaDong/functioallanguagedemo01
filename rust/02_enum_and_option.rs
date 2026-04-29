/// Rust 函数式编程 Demo 2: 枚举、模式匹配与 Option/Result
///
/// Rust 的 enum 就是代数数据类型 (ADT)，结合模式匹配 (match) 可以
/// 构建类型安全的程序。Option 和 Result 是 Rust 处理空值和错误的核心，
/// 它们的 map/and_then 方法体现了 Monad 的思想。

fn main() {
    println!("=== 枚举与模式匹配 ===");

    // 代数数据类型: 数学表达式
    let expr = Expr::Mul(
        Box::new(Expr::Add(
            Box::new(Expr::Num(3.0)),
            Box::new(Expr::Num(4.0)),
        )),
        Box::new(Expr::Neg(Box::new(Expr::Num(2.0)))),
    );

    println!("表达式: {}", show(&expr));
    println!("结果:   {}", eval(&expr));

    println!("\n=== Option: 优雅处理空值 ===");

    // Option 的链式操作 (Monad 风格)
    println!("safe_divide(10, 3) = {:?}", safe_divide(10.0, 3.0));
    println!("safe_divide(10, 0) = {:?}", safe_divide(10.0, 0.0));

    // map: 对 Some 中的值应用函数
    let maybe_num: Option<i32> = Some(5);
    let doubled = maybe_num.map(|x| x * 2);
    println!("Some(5).map(*2) = {:?}", doubled);

    let nothing: Option<i32> = None;
    let doubled_nothing = nothing.map(|x| x * 2);
    println!("None.map(*2)    = {:?}", doubled_nothing);

    // and_then (flatMap): 链式可能失败的操作
    let result = safe_divide(100.0, 5.0)
        .and_then(|x| safe_divide(x, 4.0))
        .and_then(|x| safe_sqrt(x));
    println!("100/5 -> /4 -> sqrt = {:?}", result);

    let fail_result = safe_divide(100.0, 0.0)
        .and_then(|x| safe_divide(x, 4.0))
        .and_then(|x| safe_sqrt(x));
    println!("100/0 -> /4 -> sqrt = {:?}", fail_result);

    // unwrap_or / unwrap_or_else: 提供默认值
    let value = safe_divide(10.0, 0.0).unwrap_or(0.0);
    println!("safe_divide(10, 0).unwrap_or(0) = {}", value);

    println!("\n=== Result: 带错误信息的计算 ===");

    println!("validate 'ab':    {:?}", validate_username("ab"));
    println!("validate 'alice': {:?}", validate_username("alice"));
    println!("validate 'a@b':   {:?}", validate_username("a@b"));

    // Result 的链式操作
    let registration = validate_username("alice_01")
        .and_then(|name| validate_email("alice@example.com").map(|email| (name, email)));
    println!("注册验证: {:?}", registration);

    println!("\n=== if let / while let 模式匹配 ===");

    // if let: 简洁的单分支匹配
    let config_value: Option<&str> = Some("127.0.0.1");
    if let Some(ip) = config_value {
        println!("服务器 IP: {}", ip);
    }

    // while let: 循环模式匹配
    let mut stack = vec![1, 2, 3, 4, 5];
    print!("栈弹出: ");
    while let Some(top) = stack.pop() {
        print!("{} ", top);
    }
    println!();

    println!("\n=== 模式匹配解构 ===");

    let points = vec![(0, 0), (1, 0), (0, 1), (3, 4), (-1, 2)];
    for point in &points {
        match point {
            (0, 0)         => println!("  {:?} -> 原点", point),
            (x, 0)         => println!("  {:?} -> 在 X 轴上, x={}", point, x),
            (0, y)         => println!("  {:?} -> 在 Y 轴上, y={}", point, y),
            (x, y) if x > &0 && y > &0 => println!("  {:?} -> 第一象限", point),
            _              => println!("  {:?} -> 其他", point),
        }
    }
}

// ========== 代数数据类型: 表达式 ==========

enum Expr {
    Num(f64),
    Add(Box<Expr>, Box<Expr>),
    Mul(Box<Expr>, Box<Expr>),
    Neg(Box<Expr>),
}

fn eval(expr: &Expr) -> f64 {
    match expr {
        Expr::Num(n)      => *n,
        Expr::Add(l, r)   => eval(l) + eval(r),
        Expr::Mul(l, r)   => eval(l) * eval(r),
        Expr::Neg(e)      => -eval(e),
    }
}

fn show(expr: &Expr) -> String {
    match expr {
        Expr::Num(n)      => format!("{}", n),
        Expr::Add(l, r)   => format!("({} + {})", show(l), show(r)),
        Expr::Mul(l, r)   => format!("({} * {})", show(l), show(r)),
        Expr::Neg(e)      => format!("(-{})", show(e)),
    }
}

// ========== 安全计算函数 ==========

fn safe_divide(a: f64, b: f64) -> Option<f64> {
    if b != 0.0 { Some(a / b) } else { None }
}

fn safe_sqrt(x: f64) -> Option<f64> {
    if x >= 0.0 { Some(x.sqrt()) } else { None }
}

// ========== 验证函数 (使用 Result) ==========

#[derive(Debug)]
enum ValidationError {
    TooShort,
    TooLong,
    InvalidChar(char),
}

fn validate_username(name: &str) -> Result<String, ValidationError> {
    if name.len() < 3 {
        return Err(ValidationError::TooShort);
    }
    if name.len() > 20 {
        return Err(ValidationError::TooLong);
    }
    if let Some(c) = name.chars().find(|c| !c.is_alphanumeric() && *c != '_') {
        return Err(ValidationError::InvalidChar(c));
    }
    Ok(name.to_string())
}

fn validate_email(email: &str) -> Result<String, ValidationError> {
    if email.contains('@') {
        Ok(email.to_string())
    } else {
        Err(ValidationError::InvalidChar('@'))
    }
}
