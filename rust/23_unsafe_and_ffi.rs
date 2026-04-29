/// Rust 函数式编程 Demo 23: unsafe / 裸指针 / FFI 基础
///
/// Rust 默认的"安全"是在 safe Rust 领域内实现的内存安全保证。
/// 但有时你需要和硬件、C 库、操作系统打交道，就必须进入 `unsafe` 世界。
///
/// 这个 Demo 不用 C 交叉编译，只在单文件里覆盖三件事：
///   1) 裸指针 (*const T / *mut T) 与 Box 互转
///   2) 自己实现一个 unsafe 函数 + 正确的 safety comment
///   3) 调用 libc 的 FFI 函数（strlen / abs），演示 C 互操作语法
///
/// 编译运行:
///   rustc 23_unsafe_and_ffi.rs -O -o /tmp/demo23 && /tmp/demo23
///   （FFI 部分在 Linux / macOS 下都能跑 —— 直接动态链接 libc）

use std::ffi::CString;
use std::os::raw::{c_char, c_int};

// ============================================================
// 1) 裸指针 ↔ Box —— 最典型的 unsafe 模式
// ============================================================
fn demo_raw_pointer() {
    println!("-- 1) 裸指针与 Box 互转 --");

    // safe 世界: Box<T> 拥有一块堆内存, drop 时自动释放
    let boxed: Box<i32> = Box::new(42);

    // 转成裸指针: Box::into_raw, 明确"放弃所有权"
    let raw: *mut i32 = Box::into_raw(boxed);
    println!("  raw pointer = {:p}", raw);

    // 在 unsafe 里解引用
    unsafe {
        // SAFETY: raw 来自 Box::into_raw, 指向有效 i32
        println!("  *raw = {}", *raw);
        *raw += 1;
        println!("  *raw (after +1) = {}", *raw);
    }

    // 重新收回所有权, 避免内存泄漏
    let reclaimed: Box<i32> = unsafe {
        // SAFETY: raw 是且仅是 Box::into_raw 返回的指针, 没有别名
        Box::from_raw(raw)
    };
    println!("  reclaimed Box = {}", reclaimed);
    // reclaimed drop 时正常释放内存
}

// ============================================================
// 2) 自定义 unsafe fn —— 正确的 safety 契约
// ============================================================
/// 把两个同长度的切片原地求和到第一个切片里。
///
/// # Safety
/// - `a.len()` 必须等于 `b.len()`
/// - `a` 和 `b` 在函数执行期间不能别名同一块内存
unsafe fn sum_inplace(a: *mut i32, b: *const i32, len: usize) {
    for i in 0..len {
        // SAFETY: 调用方保证 len 不越界, 且 a/b 不别名
        *a.add(i) += *b.add(i);
    }
}

fn demo_unsafe_fn() {
    println!("\n-- 2) unsafe fn 的 safety 契约 --");
    let mut a = vec![1, 2, 3, 4, 5];
    let b = vec![10, 20, 30, 40, 50];
    assert_eq!(a.len(), b.len());
    unsafe {
        // SAFETY: 上面 assert 保证同长度, a/b 是两个独立 Vec, 不别名
        sum_inplace(a.as_mut_ptr(), b.as_ptr(), a.len());
    }
    println!("  a (after sum_inplace) = {:?}", a);
}

// ============================================================
// 3) FFI —— 调用 libc 的 strlen 和 abs
// ============================================================
extern "C" {
    // size_t strlen(const char *s);
    fn strlen(s: *const c_char) -> usize;
    // int abs(int j);
    fn abs(x: c_int) -> c_int;
}

fn demo_ffi() {
    println!("\n-- 3) FFI: 直接调用 libc::strlen / libc::abs --");

    // CString: 把 Rust &str 转成 C 风格的 NUL-terminated 字符串
    let cs = CString::new("hello from Rust").unwrap();
    let ptr = cs.as_ptr();

    // 调用 C 函数必须在 unsafe
    unsafe {
        let len = strlen(ptr);
        println!("  strlen(\"hello from Rust\") = {}", len);
        println!("  abs(-42)                 = {}", abs(-42));
    }

    // 反向: 从 C 端拿到 *const c_char 转回 Rust &str
    let from_c: &std::ffi::CStr = unsafe {
        // SAFETY: cs 一直持有该字符串, 生命周期够
        std::ffi::CStr::from_ptr(ptr)
    };
    let rust_view = from_c.to_str().unwrap();
    println!("  CStr::from_ptr 回程 = {:?}", rust_view);
}

// ============================================================
// 4) 封装一个 safe wrapper —— FFI 的标准做法
// ============================================================
/// Safe wrapper: 把 "传 *const c_char" 这种不安全接口隐藏起来
/// 外部调用者只能看到 Rust 习惯的 &str 接口
fn safe_c_strlen(s: &str) -> Result<usize, &'static str> {
    let cs = CString::new(s).map_err(|_| "string contains interior NUL")?;
    // SAFETY: CString 保证以 NUL 结尾, strlen 合约满足
    Ok(unsafe { strlen(cs.as_ptr()) })
}

fn demo_safe_wrapper() {
    println!("\n-- 4) 给 FFI 套 safe wrapper --");
    println!("  safe_c_strlen(\"\\\\o\\\\\")   = {:?}", safe_c_strlen("\\o\\"));
    println!("  safe_c_strlen(\"helloRust\") = {:?}", safe_c_strlen("helloRust"));
    println!("  safe_c_strlen(\"with\\0NUL\") = {:?}", safe_c_strlen("with\0NUL"));
}

fn main() {
    println!("=== Rust Demo 23: unsafe & FFI ===\n");

    demo_raw_pointer();
    demo_unsafe_fn();
    demo_ffi();
    demo_safe_wrapper();

    println!("\n=== 核心原则 ===");
    println!("  1) unsafe 不是'关掉编译器检查', 而是'你向编译器承诺这里的约束我自己守'");
    println!("  2) 每个 unsafe 块前都应有 // SAFETY: 注释, 说明为什么合约被满足");
    println!("  3) 对外暴露的 API 尽可能是 safe 的, 把 unsafe 封装在内部");
    println!("  4) FFI 的标准模式: extern \"C\" 声明 + CString/CStr 做边界转换");
    println!("  5) 工具: Miri 能在解释执行时抓 UB, rustc --edition=2024 未来会强制 unsafe_op_in_unsafe_fn");
}
