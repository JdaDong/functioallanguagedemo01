/// Rust 函数式编程 Demo 9: Typestate 模式 —— 用类型把"协议"刻进编译期
///
/// Typestate = 用类型系统把"一个对象在生命周期里允许做什么"明确刻在编译器里。
/// 它是 Haskell Phantom Type / GADT 在 Rust 里最自然的等价物，
/// 同时是 Rust "让非法状态不可表达" 的标志性武器。
///
/// 典型用法：
///   * File 需要先 open 才能 read，读到一半 close 了再读会编译错
///   * HTTP 请求必须设了 Method + URL 才能 send
///   * 订单：Pending -> Paid -> Shipped -> Delivered；跳状态在编译期就挂掉
///
/// 本 Demo 用订单状态机做示例 —— 和 Scala Demo 9 (OrderStateMachine) 直接对照。

use std::marker::PhantomData;

// ========== 用"空结构体"作为类型标签 ==========

struct Pending;
struct Paid;
struct Shipped;
struct Delivered;
struct Cancelled;

// ========== 订单本体：State 是一个幽灵类型参数 ==========

struct Order<State> {
    id:      u64,
    amount:  u64,
    note:    String,
    _state:  PhantomData<State>,   // 运行时大小为 0，仅在编译期存在
}

// ========== 初始构造：只能造出 Pending ==========

impl Order<Pending> {
    fn new(id: u64, amount: u64) -> Self {
        Order { id, amount, note: String::from("created"), _state: PhantomData }
    }

    // 合法转换：Pending -> Paid
    fn pay(self) -> Order<Paid> {
        println!("  [pay] order#{} 收款 {} 元", self.id, self.amount);
        Order { id: self.id, amount: self.amount, note: String::from("paid"), _state: PhantomData }
    }

    // 合法转换：Pending -> Cancelled
    fn cancel(self) -> Order<Cancelled> {
        println!("  [cancel] order#{} 在付款前撤销", self.id);
        Order { id: self.id, amount: self.amount, note: String::from("cancelled"), _state: PhantomData }
    }
}

// ========== 只有 Paid 能 ship ==========

impl Order<Paid> {
    fn ship(self, carrier: &str) -> Order<Shipped> {
        println!("  [ship] order#{} 使用 {} 发货", self.id, carrier);
        Order { id: self.id, amount: self.amount, note: format!("shipped by {carrier}"), _state: PhantomData }
    }
}

// ========== 只有 Shipped 能 deliver ==========

impl Order<Shipped> {
    fn deliver(self) -> Order<Delivered> {
        println!("  [deliver] order#{} 送达", self.id);
        Order { id: self.id, amount: self.amount, note: String::from("delivered"), _state: PhantomData }
    }
}

// ========== 终态：只能查看，不能再推进 ==========

impl Order<Delivered> {
    fn receipt(&self) -> String { format!("RECEIPT#{} amount={} note={}", self.id, self.amount, self.note) }
}

impl Order<Cancelled> {
    fn reason(&self) -> &str { &self.note }
}

// ========== 所有状态共享的只读接口：用 trait 约束更优雅 ==========

trait OrderInfo {
    fn id(&self) -> u64;
    fn amount(&self) -> u64;
}

impl<S> OrderInfo for Order<S> {
    fn id(&self)     -> u64 { self.id }
    fn amount(&self) -> u64 { self.amount }
}

// ============================================================
// main
// ============================================================

fn main() {
    println!("=== 1. 合法路径: Pending -> Paid -> Shipped -> Delivered ===");
    let o = Order::<Pending>::new(1001, 299)
        .pay()
        .ship("SF-Express")
        .deliver();
    println!("  最终: {}", o.receipt());
    println!("  共享字段: id={} amount={}", o.id(), o.amount());

    println!("\n=== 2. 取消路径: Pending -> Cancelled ===");
    let c = Order::<Pending>::new(1002, 88).cancel();
    println!("  取消原因: {}", c.reason());

    println!("\n=== 3. 编译期就挡住的非法转换 ===");
    println!("  Order::<Pending>::new(..).ship(\"..\")");
    println!("  ↑ 无法编译！因为 ship 只在 impl Order<Paid> 里存在");
    println!("  这就是 Typestate 的核心：非法状态在编译期就 \"不存在\"，根本打不出来。");

    println!("\n=== 4. 与 Haskell / Scala 的对照 ===");
    println!("  Rust Typestate           === Haskell Phantom Type / GADT");
    println!("  Order<Paid>              ===  data Order (s :: OrderState)");
    println!("  impl Order<Paid>::ship   ===  ship :: Order 'Paid -> Order 'Shipped");
    println!("  Scala 的直接对应         === Demo 09 OrderStateMachine（sealed trait + 受限 API）");

    println!("\n=== 5. 什么时候用 Typestate ===");
    println!("  * 对象有明确生命周期 / 协议  (文件、连接、会话、订单、请求 Builder)");
    println!("  * 不想在运行时再写防御式 assert / match");
    println!("  * 希望 IDE 自动补全只显示 \"当前状态允许的方法\"");
    println!("  * 不适合：状态空间巨大或动态选择，此时改用运行时 enum + match 更直白");
}
