/// Rust 函数式编程 Demo 10: Parser Combinators —— 零依赖实现 nom 风格
///
/// Parser Combinator 是 FP 里最经典的 DSL 之一：
///   一个 parser 就是一个函数 `&str -> Result<(&str, T), Err>`
///   把小 parser 组合起来就能解析任意复杂语法
///
/// 对标：
///   Haskell: parsec / megaparsec / attoparsec（Demo 10 已展示手写版）
///   Scala  : cats-parse / fastparse
///   Rust   : nom（生产级）—— 这里我们零依赖手写一个迷你版
///
/// 本 Demo 实现：
///   * 原语: digit / char_ / satisfy / tag
///   * 组合子: map / and / or / many0 / many1 / sep_by
///   * 实战: 解析 "JSON lite"（数字、字符串、数组），返回 AST

use std::fmt;

// ============================================================
// 0. 基础类型
// ============================================================

type PResult<'a, T> = Result<(&'a str, T), ParseErr<'a>>;

#[derive(Debug)]
struct ParseErr<'a> { at: &'a str, expected: &'static str }

impl<'a> fmt::Display for ParseErr<'a> {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let preview: String = self.at.chars().take(12).collect();
        write!(f, "expected {} at '{}{}'", self.expected, preview, if self.at.len() > 12 { "…" } else { "" })
    }
}

// ============================================================
// 1. 原语
// ============================================================

fn satisfy<'a>(pred: impl Fn(char) -> bool, name: &'static str) -> impl Fn(&'a str) -> PResult<'a, char> {
    move |input: &'a str| {
        let mut it = input.chars();
        match it.next() {
            Some(c) if pred(c) => Ok((&input[c.len_utf8()..], c)),
            _                  => Err(ParseErr { at: input, expected: name }),
        }
    }
}

fn char_<'a>(expected: char) -> impl Fn(&'a str) -> PResult<'a, char> {
    satisfy(move |c| c == expected, "specific char")
}

fn digit<'a>(input: &'a str) -> PResult<'a, char> {
    satisfy(|c: char| c.is_ascii_digit(), "digit")(input)
}

fn tag<'a>(expected: &'static str) -> impl Fn(&'a str) -> PResult<'a, &'a str> {
    move |input: &'a str| {
        if input.starts_with(expected) {
            Ok((&input[expected.len()..], &input[..expected.len()]))
        } else {
            Err(ParseErr { at: input, expected: "tag" })
        }
    }
}

// 吃掉任意空白（零个或多个）
fn ws<'a>(mut input: &'a str) -> PResult<'a, ()> {
    while let Some(c) = input.chars().next() {
        if c.is_whitespace() { input = &input[c.len_utf8()..]; } else { break; }
    }
    Ok((input, ()))
}

// ============================================================
// 2. 组合子
// ============================================================

// map: 对 parser 的结果做变换（对标 fmap）
fn map<'a, A, B, P, F>(p: P, f: F) -> impl Fn(&'a str) -> PResult<'a, B>
where
    P: Fn(&'a str) -> PResult<'a, A>,
    F: Fn(A) -> B,
{
    move |input| p(input).map(|(rest, a)| (rest, f(a)))
}

// and: 先 p 再 q，返回 (A, B)
fn and<'a, A, B, P, Q>(p: P, q: Q) -> impl Fn(&'a str) -> PResult<'a, (A, B)>
where
    P: Fn(&'a str) -> PResult<'a, A>,
    Q: Fn(&'a str) -> PResult<'a, B>,
{
    move |input| {
        let (rest, a) = p(input)?;
        let (rest, b) = q(rest)?;
        Ok((rest, (a, b)))
    }
}

// or: p 失败则试 q（这里要求两边同类型）
fn or<'a, A, P, Q>(p: P, q: Q) -> impl Fn(&'a str) -> PResult<'a, A>
where
    P: Fn(&'a str) -> PResult<'a, A>,
    Q: Fn(&'a str) -> PResult<'a, A>,
{
    move |input| p(input).or_else(|_| q(input))
}

// many0: 零或多次
fn many0<'a, A, P>(p: P) -> impl Fn(&'a str) -> PResult<'a, Vec<A>>
where
    P: Fn(&'a str) -> PResult<'a, A>,
{
    move |mut input| {
        let mut out = Vec::new();
        loop {
            match p(input) {
                Ok((rest, a)) => { out.push(a); input = rest; }
                Err(_)        => break,
            }
        }
        Ok((input, out))
    }
}

// many1: 一或多次
fn many1<'a, A, P>(p: P) -> impl Fn(&'a str) -> PResult<'a, Vec<A>>
where
    P: Fn(&'a str) -> PResult<'a, A>,
{
    move |input| {
        let (rest, first)    = p(input)?;
        let (rest, mut rest_vec) = many0(&p)(rest)?;
        rest_vec.insert(0, first);
        Ok((rest, rest_vec))
    }
}

// sep_by: p (sep p)*
fn sep_by<'a, A, S, P, Q>(p: P, sep: Q) -> impl Fn(&'a str) -> PResult<'a, Vec<A>>
where
    P: Fn(&'a str) -> PResult<'a, A>,
    Q: Fn(&'a str) -> PResult<'a, S>,
{
    move |input| {
        let (mut rest, first) = match p(input) { Ok(v) => v, Err(_) => return Ok((input, Vec::new())) };
        let mut out = vec![first];
        loop {
            let after_sep = match sep(rest) { Ok((r, _)) => r, Err(_) => break };
            let (r, v)    = match p(after_sep) { Ok(v) => v, Err(_) => break };
            out.push(v); rest = r;
        }
        Ok((rest, out))
    }
}

// ============================================================
// 3. 实战：迷你 JSON（Number / String / Array）
// ============================================================

#[derive(Debug)]
enum Json {
    Num(i64),
    Str(String),
    Arr(Vec<Json>),
}

fn parse_number(input: &str) -> PResult<'_, Json> {
    let (input, _)       = ws(input)?;
    let (input, sign)    = or(map(char_('-'), |_| -1_i64), |i| Ok((i, 1_i64)))(input)?;
    let (input, digits)  = many1(digit)(input)?;
    let n: i64 = digits.iter().collect::<String>().parse().unwrap();
    Ok((input, Json::Num(sign * n)))
}

fn parse_string(input: &str) -> PResult<'_, Json> {
    let (input, _) = ws(input)?;
    let (input, _) = char_('"')(input)?;
    let (input, chars) = many0(satisfy(|c| c != '"', "non-quote"))(input)?;
    let (input, _) = char_('"')(input)?;
    Ok((input, Json::Str(chars.into_iter().collect())))
}

fn parse_array(input: &str) -> PResult<'_, Json> {
    let (input, _)     = ws(input)?;
    let (input, _)     = char_('[')(input)?;
    let (input, items) = sep_by(parse_value, |i| { let (i, _) = ws(i)?; char_(',')(i) })(input)?;
    let (input, _)     = ws(input)?;
    let (input, _)     = char_(']')(input)?;
    Ok((input, Json::Arr(items)))
}

fn parse_value(input: &str) -> PResult<'_, Json> {
    // 先跳空白，再三选一
    let (input, _) = ws(input)?;
    or(parse_number, or(parse_string, parse_array))(input)
}

// ============================================================
// main
// ============================================================

fn main() {
    println!("=== 1. 原语: digit / char_ / tag ===");
    println!("  digit(\"7abc\")   = {:?}", digit("7abc"));
    println!("  char_('x')(\"xy\") = {:?}", char_('x')("xy"));
    println!("  tag(\"let\")(\"let x\") = {:?}", tag("let")("let x"));

    println!("\n=== 2. 组合子: many1 / sep_by ===");
    let nums: Vec<String> = match many1(digit)("12345rest") {
        Ok((rest, ds)) => { println!("  剩余: {:?}", rest); ds.iter().map(|c| c.to_string()).collect() }
        Err(e) => { println!("  err: {e}"); vec![] }
    };
    println!("  many1(digit)(\"12345rest\") = {:?}", nums);

    let csv = sep_by(digit, char_(','));
    println!("  sep_by(digit, ',')(\"1,2,3,4\") = {:?}", csv("1,2,3,4"));

    println!("\n=== 3. 实战: 迷你 JSON ===");
    for src in [
        r#"42"#,
        r#""hello""#,
        r#"[1, 2, 3]"#,
        r#"[ "a", 10, [ -5, "nested" ] ]"#,
    ] {
        match parse_value(src) {
            Ok((rest, ast)) => println!("  {:<30} -> {:?}  (rest={:?})", src, ast, rest),
            Err(e)          => println!("  {:<30} -> ERR: {}", src, e),
        }
    }

    println!("\n=== 4. 与 Haskell / Scala 的对照 ===");
    println!("  map / and / or / many1 / sep_by");
    println!("  === Haskell parsec: <$> / <*> / <|> / many1 / sepBy");
    println!("  === Scala cats-parse: .map / .* / | / .rep / .rep(sep)");
    println!("  === Rust nom 生产版: map / tuple / alt / many1 / separated_list0");

    println!("\n=== 5. 为什么 parser combinator 是 FP 的招牌 ===");
    println!("  * parser 本身就是函数，没有『类』也没有『继承』，组合才是全部");
    println!("  * 类型签名即文档 —— `Fn(&str) -> Result<(&str, T), _>`");
    println!("  * 惰性 / 回溯 / 错误报告 都可以靠纯函数组合精确控制");
}
