/// Rust 函数式编程 Demo 17: tracing 风格结构化日志（手写迷你版）
///
/// 真实项目用的就是 `tracing` crate：
///   - span：表示一段有开始/结束的工作单元
///   - event：span 内部或外部的瞬时记录
///   - field：结构化键值对（不是字符串拼接）
///   - subscriber：把 span/event 收集起来（控制台 / JSON / OpenTelemetry / ...）
///
/// 为了不引外部依赖，这里手写一个最小版本，体会"为什么 tracing 不是另一个 log 库":
///   1) span 是**嵌套栈**: 上下文自动从父 span 继承
///   2) event 自带结构字段, 不靠字符串拼接
///   3) subscriber 可以有多种后端, 同一段业务代码写一次
///
/// 编译运行:
///   rustc 17_tracing_structured_log.rs -O -o /tmp/demo17 && /tmp/demo17

use std::cell::RefCell;
use std::fmt::Write as FmtWrite;
use std::time::Instant;

// ============================================================
// 1) 核心数据结构
// ============================================================
#[derive(Clone, Copy, Debug, PartialEq, PartialOrd, Ord, Eq)]
#[allow(dead_code)]
enum Level { Error, Warn, Info, Debug, Trace }

impl Level {
    fn tag(self) -> &'static str {
        match self {
            Level::Error => "ERROR",
            Level::Warn  => "WARN ",
            Level::Info  => "INFO ",
            Level::Debug => "DEBUG",
            Level::Trace => "TRACE",
        }
    }
}

#[derive(Clone, Debug)]
struct Field {
    key: &'static str,
    value: String,
}

// ============================================================
// 2) 全局 subscriber —— 把日志往哪儿写
// ============================================================
thread_local! {
    static SPAN_STACK: RefCell<Vec<SpanData>> = const { RefCell::new(Vec::new()) };
}

#[derive(Clone)]
struct SpanData {
    name: &'static str,
    fields: Vec<Field>,
    started: Instant,
}

// ============================================================
// 3) span! 宏：进入/离开一段带上下文的工作
// ============================================================
struct SpanGuard { name: &'static str }

impl Drop for SpanGuard {
    fn drop(&mut self) {
        SPAN_STACK.with(|stack| {
            let mut s = stack.borrow_mut();
            if let Some(top) = s.last() {
                if top.name == self.name {
                    let span = s.pop().unwrap();
                    let elapsed = span.started.elapsed();
                    // 输出 span 结束时的"汇总行"
                    let ctx = render_context(&s);
                    println!(
                        "[{:5} {:>7}µs]{}{} span=\"{}\" {}",
                        "SPAN",
                        elapsed.as_micros(),
                        if ctx.is_empty() { "" } else { " " },
                        ctx,
                        span.name,
                        render_fields(&span.fields),
                    );
                }
            }
        });
    }
}

macro_rules! span {
    ($name:expr $(, $key:ident = $val:expr )* $(,)?) => {{
        let fields = vec![
            $( Field { key: stringify!($key), value: format!("{:?}", $val) } ),*
        ];
        SPAN_STACK.with(|stack| {
            stack.borrow_mut().push(SpanData {
                name: $name,
                fields,
                started: Instant::now(),
            });
        });
        SpanGuard { name: $name }
    }};
}

// ============================================================
// 4) event! 宏：瞬时事件
// ============================================================
macro_rules! event {
    ($level:expr, $msg:expr $(, $key:ident = $val:expr )* $(,)?) => {{
        let fields: Vec<Field> = vec![
            $( Field { key: stringify!($key), value: format!("{:?}", $val) } ),*
        ];
        let ctx = SPAN_STACK.with(|s| render_context(&s.borrow()));
        println!(
            "[{} {}]{}{} msg=\"{}\" {}",
            $level.tag(),
            chrono_like_ts(),
            if ctx.is_empty() { "" } else { " " },
            ctx,
            $msg,
            render_fields(&fields),
        );
    }};
}

macro_rules! info  { ($msg:expr $(, $k:ident = $v:expr)* $(,)?) => { event!(Level::Info,  $msg $(, $k = $v)*) } }
#[allow(unused_macros)]
macro_rules! warn_ { ($msg:expr $(, $k:ident = $v:expr)* $(,)?) => { event!(Level::Warn,  $msg $(, $k = $v)*) } }
macro_rules! error { ($msg:expr $(, $k:ident = $v:expr)* $(,)?) => { event!(Level::Error, $msg $(, $k = $v)*) } }

// ============================================================
// 5) 渲染辅助
// ============================================================
fn render_context(stack: &[SpanData]) -> String {
    let mut s = String::new();
    for (i, span) in stack.iter().enumerate() {
        if i > 0 { s.push_str(" → "); }
        s.push_str(span.name);
        if !span.fields.is_empty() {
            s.push('{');
            for (j, f) in span.fields.iter().enumerate() {
                if j > 0 { s.push(','); }
                let _ = write!(s, "{}={}", f.key, f.value);
            }
            s.push('}');
        }
    }
    s
}

fn render_fields(fields: &[Field]) -> String {
    let mut out = String::new();
    for f in fields {
        let _ = write!(out, "{}={} ", f.key, f.value);
    }
    out
}

fn chrono_like_ts() -> String {
    // 故意用相对毫秒代替真实时间, 避免引依赖
    use std::sync::OnceLock;
    static START: OnceLock<Instant> = OnceLock::new();
    let t0 = START.get_or_init(Instant::now);
    format!("+{:>5}ms", t0.elapsed().as_millis())
}

// ============================================================
// 6) 业务代码演示
// ============================================================
fn handle_request(req_id: u64, user_id: u64) {
    let _s = span!("handle_request", req_id = req_id, user = user_id);
    info!("received request");

    let user = load_user(user_id);
    if let Err(why) = charge_user(user_id, 100) {
        error!("charge failed", reason = why);
    } else {
        info!("charge ok", user = user);
    }
}

fn load_user(id: u64) -> String {
    let _s = span!("db.query", table = "users", id = id);
    info!("selecting user");
    format!("user-{}", id)
}

fn charge_user(id: u64, amount: u64) -> Result<(), &'static str> {
    let _s = span!("billing.charge", user = id, amount = amount);
    info!("calling payment gateway");
    if id == 999 { Err("insufficient funds") } else { Ok(()) }
}

fn main() {
    println!("=== Rust Demo 17: tracing 风格结构化日志 ===\n");

    handle_request(1001, 1);
    println!("---");
    handle_request(1002, 999);

    println!("\n=== 关键思想 ===");
    println!("  - span 是嵌套栈: 子 span 的日志自动带上父 span 的所有字段");
    println!("  - field 是结构化键值, 不靠字符串拼接, 方便后端(JSON/ELK/OTel)解析");
    println!("  - subscriber 只是'输出侧', 同一份业务代码可以切换打印/JSON/OTel 后端");
    println!("  - 真实 crate: `tracing` + `tracing-subscriber`, 生态还连了 `tracing-opentelemetry`");
    println!("  - 对标: Scala log4cats, Haskell katip, Erlang OTP logger (Demo 16), Go zap");
}
