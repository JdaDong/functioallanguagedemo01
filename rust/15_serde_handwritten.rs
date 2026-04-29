/// Rust 函数式编程 Demo 15: serde 手写版 —— 序列化/反序列化的心智模型
///
/// serde 是 Rust 生态最具代表性的 "trait + derive 驱动" 设计。
/// 为了不引依赖，这个 Demo 手写一个最小 JSON 序列化/反序列化框架，
/// 让你看到 serde 的核心思路：
///
///   trait Serialize   { fn serialize(&self)       -> Value; }
///   trait Deserialize { fn deserialize(v: &Value) -> Result<Self>; }
///
/// 真实 serde 的 derive 展开出来其实就是"为每个字段分别调用 ser/de"。
/// 本 Demo 手动实现 Point / User / Company，再演示数据往返。
///
/// 编译运行:
///   rustc 15_serde_handwritten.rs -O -o /tmp/demo15 && /tmp/demo15

use std::collections::BTreeMap;
use std::fmt::{self, Write};

// ============================================================
// 1) 一个最小 JSON 值模型
// ============================================================
#[derive(Debug, Clone, PartialEq)]
#[allow(dead_code)]
enum Value {
    Null,
    Bool(bool),
    Int(i64),
    Float(f64),
    Str(String),
    Array(Vec<Value>),
    Object(BTreeMap<String, Value>),
}

#[derive(Debug)]
struct DeError(String);
impl fmt::Display for DeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { write!(f, "DeError: {}", self.0) }
}
type DeResult<T> = Result<T, DeError>;

// ============================================================
// 2) 核心 trait —— serde 的简化版
// ============================================================
trait Serialize {
    fn serialize(&self) -> Value;
}
trait Deserialize: Sized {
    fn deserialize(v: &Value) -> DeResult<Self>;
}

// ============================================================
// 3) 给基础类型 impl
// ============================================================
impl Serialize for i64 {
    fn serialize(&self) -> Value { Value::Int(*self) }
}
impl Deserialize for i64 {
    fn deserialize(v: &Value) -> DeResult<Self> {
        match v { Value::Int(n) => Ok(*n), _ => Err(DeError(format!("expected i64, got {:?}", v))) }
    }
}

impl Serialize for String {
    fn serialize(&self) -> Value { Value::Str(self.clone()) }
}
impl Deserialize for String {
    fn deserialize(v: &Value) -> DeResult<Self> {
        match v { Value::Str(s) => Ok(s.clone()), _ => Err(DeError(format!("expected String, got {:?}", v))) }
    }
}

impl Serialize for bool {
    fn serialize(&self) -> Value { Value::Bool(*self) }
}
impl Deserialize for bool {
    fn deserialize(v: &Value) -> DeResult<Self> {
        match v { Value::Bool(b) => Ok(*b), _ => Err(DeError(format!("expected bool, got {:?}", v))) }
    }
}

// 容器类型
impl<T: Serialize> Serialize for Vec<T> {
    fn serialize(&self) -> Value { Value::Array(self.iter().map(|x| x.serialize()).collect()) }
}
impl<T: Deserialize> Deserialize for Vec<T> {
    fn deserialize(v: &Value) -> DeResult<Self> {
        match v {
            Value::Array(xs) => xs.iter().map(T::deserialize).collect(),
            _ => Err(DeError(format!("expected Array, got {:?}", v))),
        }
    }
}

impl<T: Serialize> Serialize for Option<T> {
    fn serialize(&self) -> Value {
        match self { Some(x) => x.serialize(), None => Value::Null }
    }
}
impl<T: Deserialize> Deserialize for Option<T> {
    fn deserialize(v: &Value) -> DeResult<Self> {
        match v { Value::Null => Ok(None), other => T::deserialize(other).map(Some) }
    }
}

// ============================================================
// 4) 业务类型：手写"derive 展开"
// ============================================================
#[derive(Debug, PartialEq)]
struct Point { x: i64, y: i64 }

impl Serialize for Point {
    fn serialize(&self) -> Value {
        let mut m = BTreeMap::new();
        m.insert("x".into(), self.x.serialize());
        m.insert("y".into(), self.y.serialize());
        Value::Object(m)
    }
}
impl Deserialize for Point {
    fn deserialize(v: &Value) -> DeResult<Self> {
        let obj = match v {
            Value::Object(m) => m,
            _ => return Err(DeError(format!("Point expects object, got {:?}", v))),
        };
        let x = obj.get("x").ok_or_else(|| DeError("missing x".into()))?;
        let y = obj.get("y").ok_or_else(|| DeError("missing y".into()))?;
        Ok(Point { x: i64::deserialize(x)?, y: i64::deserialize(y)? })
    }
}

#[derive(Debug, PartialEq)]
struct User {
    name: String,
    age: i64,
    email: Option<String>,
    tags: Vec<String>,
}

impl Serialize for User {
    fn serialize(&self) -> Value {
        let mut m = BTreeMap::new();
        m.insert("name".into(),  self.name.serialize());
        m.insert("age".into(),   self.age.serialize());
        m.insert("email".into(), self.email.serialize());
        m.insert("tags".into(),  self.tags.serialize());
        Value::Object(m)
    }
}
impl Deserialize for User {
    fn deserialize(v: &Value) -> DeResult<Self> {
        let obj = match v {
            Value::Object(m) => m,
            _ => return Err(DeError(format!("User expects object, got {:?}", v))),
        };
        let get = |k: &str| obj.get(k).ok_or_else(|| DeError(format!("missing field {}", k)));
        Ok(User {
            name:  String::deserialize(get("name")?)?,
            age:   i64::deserialize(get("age")?)?,
            email: Option::<String>::deserialize(get("email")?)?,
            tags:  Vec::<String>::deserialize(get("tags")?)?,
        })
    }
}

// ============================================================
// 5) 把 Value 打印成 JSON 文本（仅用于演示）
// ============================================================
fn to_json(v: &Value) -> String {
    let mut s = String::new();
    write_value(&mut s, v).unwrap();
    s
}

fn write_value(out: &mut String, v: &Value) -> fmt::Result {
    match v {
        Value::Null => out.write_str("null"),
        Value::Bool(b) => write!(out, "{}", b),
        Value::Int(i) => write!(out, "{}", i),
        Value::Float(f) => write!(out, "{}", f),
        Value::Str(s) => write!(out, "\"{}\"", s.replace('"', "\\\"")),
        Value::Array(xs) => {
            out.write_char('[')?;
            for (i, x) in xs.iter().enumerate() {
                if i > 0 { out.write_char(',')?; }
                write_value(out, x)?;
            }
            out.write_char(']')
        }
        Value::Object(m) => {
            out.write_char('{')?;
            for (i, (k, v)) in m.iter().enumerate() {
                if i > 0 { out.write_char(',')?; }
                write!(out, "\"{}\":", k)?;
                write_value(out, v)?;
            }
            out.write_char('}')
        }
    }
}

fn main() {
    println!("=== Rust Demo 15: 手写 serde (序列化/反序列化) ===\n");

    // --- Point ---
    let p = Point { x: 10, y: 20 };
    let v = p.serialize();
    let text = to_json(&v);
    let back = Point::deserialize(&v).unwrap();
    println!("-- Point --");
    println!("  原始: {:?}", p);
    println!("  JSON: {}", text);
    println!("  回程: {:?}", back);
    assert_eq!(p, back);

    // --- User ---
    let u = User {
        name:  "alice".into(),
        age:   30,
        email: Some("alice@example.com".into()),
        tags:  vec!["admin".into(), "founder".into()],
    };
    let v = u.serialize();
    let text = to_json(&v);
    let back = User::deserialize(&v).unwrap();
    println!("\n-- User --");
    println!("  原始: {:?}", u);
    println!("  JSON: {}", text);
    println!("  回程: {:?}", back);
    assert_eq!(u, back);

    // --- 缺字段的反序列化错误 ---
    println!("\n-- 错误路径演示 --");
    let mut bad_obj = BTreeMap::new();
    bad_obj.insert("name".into(), Value::Str("bob".into()));
    let bad = Value::Object(bad_obj);
    let err = User::deserialize(&bad).unwrap_err();
    println!("  缺字段触发的错误: {}", err);

    println!("\n=== 对照真实 serde ===");
    println!("  真实 serde:");
    println!("    #[derive(Serialize, Deserialize)]");
    println!("    struct User {{ name: String, age: u32, ... }}");
    println!("  derive 宏展开后做的事情和本 Demo 一模一样：");
    println!("    - 对每个字段调用 Serialize/Deserialize 的实现");
    println!("    - 处理 Option、Vec、嵌套结构");
    println!("    - 还提供 #[serde(rename)] / #[serde(default)] 等控制");
    println!("  serde 真正的价值在于: 一个 trait 抽象 + 多种格式后端 (JSON/YAML/Bincode/...)");
}
