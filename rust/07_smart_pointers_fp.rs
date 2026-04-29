/// Rust 函数式编程 Demo 7: 智能指针 —— FP 在 Rust 中的"可变性开关"
///
/// Rust 默认不可变，这和 Haskell / 纯 FP 的直觉完全一致。
/// 但现实里我们偶尔确实需要：
///   * 多个持有者共享同一份数据（函数式风格的共享不可变状态）
///   * 内部可变性（比如缓存、计数器、观察者）
///
/// Rust 给出了一组"小而精"的智能指针作为"可控的可变性开关"：
///   Box<T>     —— 在堆上独占一份数据
///   Rc<T>      —— 单线程引用计数，共享不可变数据
///   Arc<T>     —— 多线程引用计数（原子）
///   RefCell<T> —— 单线程的内部可变性（运行时借用检查）
///   Mutex<T>   —— 多线程的内部可变性（锁）
///
/// 组合规律：
///   Rc<RefCell<T>>  ——  单线程共享可变状态（类似 OO）
///   Arc<Mutex<T>>   ——  多线程共享可变状态（常见并发模式）
///   Rc<T>           ——  纯共享不可变，像 Haskell 的惰性节点
///
/// 本 Demo 用最小例子把这张表"立"起来。

use std::cell::RefCell;
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::thread;

fn main() {
    println!("=== 1. Rc<T>: 共享不可变，最贴近纯 FP 的情形 ===");
    // 函数式的链表节点：多个链表可以共享相同的尾部，不需要复制
    let shared_tail: Rc<List<i32>> = Rc::new(List::Cons(3, Rc::new(List::Cons(4, Rc::new(List::Nil)))));
    let a = Rc::new(List::Cons(1, Rc::new(List::Cons(2, shared_tail.clone()))));
    let b = Rc::new(List::Cons(10,                    shared_tail.clone()));
    println!("  a = {:?}", a);
    println!("  b = {:?}", b);
    println!("  尾部被共享了 {} 次", Rc::strong_count(&shared_tail));

    println!("\n=== 2. Rc<RefCell<T>>: 单线程共享可变 ===");
    // 经典玩法：两个观察者引用同一个计数器
    let counter = Rc::new(RefCell::new(0_i64));
    let o1 = counter.clone();
    let o2 = counter.clone();
    *o1.borrow_mut() += 1;
    *o2.borrow_mut() += 10;
    *counter.borrow_mut() += 100;
    println!("  最终值 = {}", counter.borrow());   // 111

    println!("\n=== 3. 内部可变性 + 纯接口：记忆化 ===");
    // 从外部看 memoized_square 是一个普通 Fn（不可变），
    // 里面偷偷用 RefCell 维护缓存 —— 对外仍保持引用透明
    let squarer = Memo::new(|x: i32| x * x);
    println!("  first  call sq(9)  = {}", squarer.call(9));
    println!("  second call sq(9)  = {}  (命中缓存)", squarer.call(9));
    println!("  cache size         = {}", squarer.size());

    println!("\n=== 4. Arc<Mutex<T>>: 多线程共享可变 ===");
    let total = Arc::new(Mutex::new(0_u64));
    let mut handles = Vec::new();
    for tid in 0..4 {
        let t = Arc::clone(&total);
        handles.push(thread::spawn(move || {
            for _ in 0..1_000 { *t.lock().unwrap() += 1; }
            println!("  线程 {tid} done");
        }));
    }
    for h in handles { h.join().unwrap(); }
    println!("  4 线程各累加 1000 次 -> total = {}", *total.lock().unwrap());

    println!("\n=== 5. 选型一图流 ===");
    println!("  共享?   可变?   单线程?");
    println!("  N       N       —        -> 直接 T / &T");
    println!("  Y       N       Y        -> Rc<T>");
    println!("  Y       N       N        -> Arc<T>");
    println!("  Y       Y       Y        -> Rc<RefCell<T>>");
    println!("  Y       Y       N        -> Arc<Mutex<T>>  (读多写少可用 RwLock)");
    println!("  N       Y       —        -> &mut T / Box<T>");
}

// ========== 共享的函数式链表 ==========

#[derive(Debug)]
enum List<T> {
    Cons(T, Rc<List<T>>),
    Nil,
}

// ========== 记忆化：对外 Fn，对内 RefCell ==========

struct Memo<F: Fn(i32) -> i32> {
    f:     F,
    cache: RefCell<std::collections::HashMap<i32, i32>>,
}

impl<F: Fn(i32) -> i32> Memo<F> {
    fn new(f: F) -> Self {
        Memo { f, cache: RefCell::new(std::collections::HashMap::new()) }
    }

    // 注意参数是 &self 不是 &mut self —— 对外看是"纯函数"
    fn call(&self, x: i32) -> i32 {
        if let Some(v) = self.cache.borrow().get(&x) { return *v; }
        let v = (self.f)(x);
        self.cache.borrow_mut().insert(x, v);
        v
    }

    fn size(&self) -> usize { self.cache.borrow().len() }
}
