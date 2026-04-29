/// Rust 函数式编程 Demo 3: Trait（特征）与泛型函数式编程
///
/// Trait 是 Rust 的多态机制，类似 Haskell 的 Type Class。
/// 结合泛型，可以写出高度抽象且零成本的函数式代码。
/// 所有权系统确保了函数式编程中的不可变性和安全性。

use std::fmt;

fn main() {
    println!("=== Trait: 多态抽象 ===");

    let shapes: Vec<Box<dyn Shape>> = vec![
        Box::new(Circle { radius: 5.0 }),
        Box::new(Rectangle { width: 3.0, height: 4.0 }),
        Box::new(Triangle { a: 3.0, b: 4.0, c: 5.0 }),
    ];

    for shape in &shapes {
        println!("{}", shape.describe());
    }

    // 函数式: 计算所有形状的面积之和
    let total_area: f64 = shapes.iter().map(|s| s.area()).sum();
    println!("所有形状面积之和: {:.2}", total_area);

    // 找出面积最大的形状
    let largest = shapes
        .iter()
        .max_by(|a, b| a.area().partial_cmp(&b.area()).unwrap());
    if let Some(shape) = largest {
        println!("最大形状: {}", shape.describe());
    }

    println!("\n=== 泛型函数式编程 ===");

    // 泛型的 map 实现
    let nums = vec![1, 2, 3, 4, 5];
    let doubled = my_map(&nums, |x| x * 2);
    println!("my_map(*2): {:?}", doubled);

    let strings = my_map(&nums, |x| format!("#{}", x));
    println!("my_map(format): {:?}", strings);

    // 泛型的 filter
    let evens = my_filter(&nums, |x| x % 2 == 0);
    println!("my_filter(even): {:?}", evens);

    // 泛型的 fold
    let sum = my_fold(&nums, 0, |acc, x| acc + x);
    let product = my_fold(&nums, 1, |acc, x| acc * x);
    println!("my_fold(+): {}", sum);
    println!("my_fold(*): {}", product);

    println!("\n=== 函数组合 ===");

    let double = |x: i32| x * 2;
    let add_one = |x: i32| x + 1;
    let square = |x: i32| x * x;

    // 函数组合: compose(f, g)(x) = f(g(x))
    let double_then_add = compose(add_one, double);
    println!("compose(+1, *2)(3) = {}", double_then_add(3)); // 7

    // 管道: pipe(f, g)(x) = g(f(x))
    let pipeline = pipe(double, pipe(add_one, square));
    println!("pipe(*2, +1, ^2)(3) = {}", pipeline(3)); // 3->6->7->49

    println!("\n=== 不可变性与所有权 ===");

    // Rust 的所有权系统天然保证了不可变性
    let original = vec![1, 2, 3, 4, 5];

    // "修改"操作返回新的集合，原始数据不变
    let with_added: Vec<i32> = original.iter().chain(std::iter::once(&6)).copied().collect();
    let without_first: Vec<i32> = original.iter().skip(1).copied().collect();
    let reversed: Vec<i32> = original.iter().rev().copied().collect();

    println!("原始:       {:?}", original);
    println!("添加 6:     {:?}", with_added);
    println!("去掉第一个: {:?}", without_first);
    println!("反转:       {:?}", reversed);
    println!("原始不变:   {:?}", original);

    println!("\n=== 实战: 学生成绩管道 ===");

    let students = vec![
        Student { name: "Alice".to_string(), scores: vec![90, 85, 92] },
        Student { name: "Bob".to_string(), scores: vec![78, 82, 80] },
        Student { name: "Charlie".to_string(), scores: vec![95, 98, 92] },
        Student { name: "Diana".to_string(), scores: vec![60, 70, 65] },
        Student { name: "Eve".to_string(), scores: vec![88, 90, 85] },
    ];

    // 完整的函数式管道
    let honor_roll: Vec<String> = students
        .iter()
        .map(|s| (s.name.clone(), s.average()))          // 计算平均分
        .filter(|(_, avg)| *avg >= 85.0)                  // 筛选优秀学生
        .map(|(name, avg)| format!("{} ({:.1})", name, avg))  // 格式化
        .collect();

    println!("荣誉榜: {:?}", honor_roll);

    // 最高分学生
    let top = students
        .iter()
        .max_by(|a, b| a.average().partial_cmp(&b.average()).unwrap());
    if let Some(student) = top {
        println!("最高分: {} ({:.1})", student.name, student.average());
    }

    // 班级平均分
    let class_avg: f64 = students.iter().map(|s| s.average()).sum::<f64>()
        / students.len() as f64;
    println!("班级平均分: {:.1}", class_avg);
}

// ========== Trait 定义 ==========

trait Shape: fmt::Display {
    fn area(&self) -> f64;
    fn perimeter(&self) -> f64;
    fn describe(&self) -> String {
        format!("{}, 面积={:.2}, 周长={:.2}", self, self.area(), self.perimeter())
    }
}

// ========== 具体类型 ==========

struct Circle { radius: f64 }
struct Rectangle { width: f64, height: f64 }
struct Triangle { a: f64, b: f64, c: f64 }

impl fmt::Display for Circle {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "圆(r={})", self.radius)
    }
}

impl Shape for Circle {
    fn area(&self) -> f64 { std::f64::consts::PI * self.radius * self.radius }
    fn perimeter(&self) -> f64 { 2.0 * std::f64::consts::PI * self.radius }
}

impl fmt::Display for Rectangle {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "矩形({}x{})", self.width, self.height)
    }
}

impl Shape for Rectangle {
    fn area(&self) -> f64 { self.width * self.height }
    fn perimeter(&self) -> f64 { 2.0 * (self.width + self.height) }
}

impl fmt::Display for Triangle {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "三角形({},{},{})", self.a, self.b, self.c)
    }
}

impl Shape for Triangle {
    fn area(&self) -> f64 {
        let s = self.perimeter() / 2.0;
        (s * (s - self.a) * (s - self.b) * (s - self.c)).sqrt()
    }
    fn perimeter(&self) -> f64 { self.a + self.b + self.c }
}

// ========== 泛型函数式工具 ==========

fn my_map<T, U, F: Fn(&T) -> U>(list: &[T], f: F) -> Vec<U> {
    list.iter().map(f).collect()
}

fn my_filter<T: Clone, F: Fn(&T) -> bool>(list: &[T], f: F) -> Vec<T> {
    list.iter().filter(|x| f(x)).cloned().collect()
}

fn my_fold<T, U, F: Fn(U, &T) -> U>(list: &[T], init: U, f: F) -> U {
    list.iter().fold(init, |acc, x| f(acc, x))
}

// ========== 函数组合 ==========

fn compose<A, B, C>(f: impl Fn(B) -> C, g: impl Fn(A) -> B) -> impl Fn(A) -> C {
    move |x| f(g(x))
}

fn pipe<A, B, C>(f: impl Fn(A) -> B, g: impl Fn(B) -> C) -> impl Fn(A) -> C {
    move |x| g(f(x))
}

// ========== 学生结构体 ==========

struct Student {
    name: String,
    scores: Vec<i32>,
}

impl Student {
    fn average(&self) -> f64 {
        self.scores.iter().sum::<i32>() as f64 / self.scores.len() as f64
    }
}
