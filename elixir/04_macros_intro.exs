# Elixir 函数式编程 Demo 04: 宏系统入门
#
# Elixir 的宏 ≠ C 的文本宏 / Rust 的 macro_rules!，
# 它是"操作 AST 的 Elixir 代码"：
#   代码 → quote → AST → 用 Elixir 函数变换 → unquote 注入 → 最终代码
#
# 本 Demo 展示三件事：
#   1) quote / unquote 看 AST 长什么样
#   2) defmacro unless：自己实现一个控制结构
#   3) defmacro trace：给函数调用自动包裹"打印入参和耗时"

defmodule Inspecting do
  # quote 返回 AST；调用者既能看它、也能用它
  def show_ast do
    ast = quote do: 1 + 2 * 3
    IO.inspect(ast, label: "AST of 1 + 2 * 3")
  end
end

defmodule MyControl do
  # 自己实现 unless：和 Elixir 标准库同名, 仅作演示
  defmacro my_unless(cond_ast, do: body) do
    quote do
      if unquote(cond_ast), do: nil, else: unquote(body)
    end
  end

  # 生成一个"只在编译期决定的分支表格"
  defmacro weekday_name(n_ast) do
    table = %{1 => "Mon", 2 => "Tue", 3 => "Wed",
              4 => "Thu", 5 => "Fri", 6 => "Sat", 7 => "Sun"}
    quote do
      case unquote(n_ast) do
        unquote(Enum.map(table, fn {k, v} ->
          {:->, [], [[k], v]}
        end))
      end
    end
  end
end

defmodule Tracing do
  # 给一段表达式自动包"打印入参 + 耗时"
  defmacro traced(label, do: body) do
    quote do
      start_us = System.monotonic_time(:microsecond)
      result   = unquote(body)
      took_us  = System.monotonic_time(:microsecond) - start_us
      IO.puts("[trace] #{unquote(label)} -> #{inspect(result)}  (#{took_us}µs)")
      result
    end
  end
end

defmodule Main do
  # 宏必须在调用前 require / import
  require MyControl
  require Tracing
  import  MyControl, only: [my_unless: 2, weekday_name: 1]
  import  Tracing,   only: [traced: 2]

  def run do
    IO.puts("=== Elixir Demo 04: 宏系统入门 ===\n")

    Inspecting.show_ast()

    IO.puts("\n-- my_unless（自制控制结构）--")
    my_unless(1 == 2, do: IO.puts("  条件为假时执行"))
    my_unless(1 == 1, do: IO.puts("  这句不会出现"))

    IO.puts("\n-- weekday_name（编译期展开出 case 表）--")
    for n <- 1..7, do: IO.puts("  #{n} -> #{weekday_name(n)}")

    IO.puts("\n-- traced（包裹任意表达式）--")
    _ = traced("fib(20)", do: (
      fib = fn f, n -> if n < 2, do: n, else: f.(f, n - 1) + f.(f, n - 2) end
      fib.(fib, 20)
    ))

    _ = traced("sum 1..1000", do: Enum.sum(1..1000))
  end
end

Main.run()

IO.puts("""

=== 重点理解 ===
- quote / unquote 是 Elixir 宏的基础: 拿到 AST, 拼装 AST, 再交给编译器
- defmacro 写出来的不是普通函数, 它的输入输出都是 AST 树
- require / import: 宏必须先被 require 过, import 之后才能无前缀调用
- 宏的威力 = 用少量代码消灭模板: 控制结构 (unless/switch), 打点 (trace/log), DSL (Ecto/Phoenix)
- 纪律: 能用函数就别用宏, 宏只在"需要在编译期生成代码"时才值得
""")
