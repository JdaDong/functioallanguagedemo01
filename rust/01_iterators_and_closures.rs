/// Rust 函数式编程 Demo 1: 迭代器与闭包
///
/// Rust 的迭代器 (Iterator) 是零成本抽象的典范——
/// 链式的函数式操作在编译后效率等同于手写的 for 循环。
/// 闭包 (Closure) 是 Rust 中创建匿名函数的方式。

fn main() {
    println!("=== 迭代器: map / filter / fold ===");

    let numbers: Vec<i32> = (1..=10).collect();
    println!("原始列表: {:?}", numbers);

    // map: 对每个元素应用转换
    let doubled: Vec<i32> = numbers.iter().map(|x| x * 2).collect();
    println!("每个翻倍: {:?}", doubled);

    // filter: 保留满足条件的元素
    let evens: Vec<&i32> = numbers.iter().filter(|x| *x % 2 == 0).collect();
    println!("偶数: {:?}", evens);

    // fold: 归约为单个值 (类似 reduce)
    let sum: i32 = numbers.iter().fold(0, |acc, x| acc + x);
    println!("求和: {}", sum);

    // 链式操作: 找出偶数 -> 平方 -> 求和
    let result: i32 = numbers
        .iter()
        .filter(|x| *x % 2 == 0)
        .map(|x| x * x)
        .sum();
    println!("偶数平方和: {}", result);

    println!("\n=== 更多迭代器方法 ===");

    // zip: 合并两个迭代器
    let names = vec!["Alice", "Bob", "Charlie"];
    let scores = vec![95, 87, 92];
    let students: Vec<(&str, &i32)> = names.iter().zip(scores.iter()).collect();
    println!("zip: {:?}", students);

    // enumerate: 带索引遍历
    for (i, name) in names.iter().enumerate() {
        println!("  #{}: {}", i, name);
    }

    // any / all: 断言
    let has_negative = numbers.iter().any(|x| *x < 0);
    let all_positive = numbers.iter().all(|x| *x > 0);
    println!("有负数? {}, 全正数? {}", has_negative, all_positive);

    // find: 找到第一个满足条件的元素
    let first_gt_5 = numbers.iter().find(|x| **x > 5);
    println!("第一个 >5 的数: {:?}", first_gt_5);

    // flat_map: map 后展平
    let nested = vec![vec![1, 2], vec![3, 4], vec![5, 6]];
    let flat: Vec<&i32> = nested.iter().flat_map(|v| v.iter()).collect();
    println!("flat_map: {:?}", flat);

    // take / skip: 切片
    let first_3: Vec<&i32> = numbers.iter().take(3).collect();
    let skip_7: Vec<&i32> = numbers.iter().skip(7).collect();
    println!("前3个: {:?}, 跳过7个: {:?}", first_3, skip_7);

    println!("\n=== 闭包 (Closure) ===");

    // 闭包捕获环境变量
    let multiplier = 3;
    let triple = |x: i32| x * multiplier;
    println!("triple(5) = {}", triple(5));

    // 返回闭包的函数
    let add_n = make_adder(10);
    println!("add_10(5) = {}", add_n(5));

    // 闭包作为参数
    let result = apply_twice(|x| x + 3, 7);
    println!("apply_twice(+3, 7) = {}", result);

    println!("\n=== 实战: 文本处理管道 ===");

    let text = "hello world from rust functional programming";

    // 统计每个单词的长度
    let word_lengths: Vec<(&str, usize)> = text
        .split_whitespace()
        .map(|w| (w, w.len()))
        .collect();
    println!("单词长度: {:?}", word_lengths);

    // 找出最长的单词
    let longest = text
        .split_whitespace()
        .max_by_key(|w| w.len());
    println!("最长单词: {:?}", longest);

    // 将每个单词首字母大写
    let title_case: String = text
        .split_whitespace()
        .map(|w| {
            let mut chars = w.chars();
            match chars.next() {
                None => String::new(),
                Some(c) => c.to_uppercase().to_string() + chars.as_str(),
            }
        })
        .collect::<Vec<_>>()
        .join(" ");
    println!("Title Case: {}", title_case);
}

/// 返回闭包的函数 (高阶函数)
fn make_adder(n: i32) -> impl Fn(i32) -> i32 {
    move |x| x + n
}

/// 对值应用函数两次
fn apply_twice<F: Fn(i32) -> i32>(f: F, x: i32) -> i32 {
    f(f(x))
}
