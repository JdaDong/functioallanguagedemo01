# Elixir 函数式编程 Demo 01: 基础语法 + 管道 + 模式匹配
#
# 本文件用 `elixir 01_basics_pipeline.exs` 直接运行，不依赖 mix。
# 对照 Erlang 的 01_pattern_matching.erl 看最直观：
#   - Erlang 用 "函数子句 + 分号 + 点" 表达分支，大写开头是变量
#   - Elixir 用 def / defp + do...end 块，变量是小写
#   - Elixir 独有的 |> 管道把嵌套函数调用"拉直"成数据流水线

defmodule Basics do
  # 1) 多子句函数 = 模式匹配 + 守卫
  def classify(0), do: :zero
  def classify(n) when is_integer(n) and n > 0, do: :positive
  def classify(n) when is_integer(n) and n < 0, do: :negative
  def classify(_), do: :not_integer

  # 2) 列表递归（尾递归累加）
  def sum(list), do: sum(list, 0)
  defp sum([], acc), do: acc
  defp sum([h | t], acc), do: sum(t, acc + h)

  # 3) 解构赋值 + 模式匹配
  def head_and_rest([h | t]), do: {h, t}

  # 4) map / keyword 解构
  def user_name(%{"name" => name}), do: name
  def user_name(_), do: "匿名"
end

defmodule Pipeline do
  # 5) 管道 |>：把 f(g(h(x))) 写成 x |> h() |> g() |> f()
  def word_stats(text) do
    text
    |> String.downcase()
    |> String.split(~r/\W+/, trim: true)
    |> Enum.frequencies()
    |> Enum.sort_by(fn {_w, c} -> -c end)
    |> Enum.take(3)
  end

  # 6) with：多步骤顺序匹配，任一步失败就短路（对标 Haskell do / Rust ?）
  def parse_age(input) do
    with {age, ""} <- Integer.parse(input),
         true      <- age >= 0 and age <= 150 do
      {:ok, age}
    else
      :error -> {:error, :not_a_number}
      false  -> {:error, :out_of_range}
    end
  end
end

IO.puts("=== Elixir Demo 01: 基础语法 + 管道 + 模式匹配 ===\n")

IO.puts("-- classify/1 --")
for x <- [0, 7, -3, 1.5, :foo] do
  IO.puts("  classify(#{inspect(x)}) = #{inspect(Basics.classify(x))}")
end

IO.puts("\n-- 列表递归 sum --")
IO.puts("  sum([1..5]) = #{Basics.sum(Enum.to_list(1..5))}")

IO.puts("\n-- map 解构 --")
name1 = Basics.user_name(%{"name" => "Ada"})
name2 = Basics.user_name(%{})
IO.puts("  user_name(%{\"name\" => \"Ada\"}) = #{name1}")
IO.puts("  user_name(%{})                = #{name2}")

IO.puts("\n-- 管道 word_stats --")
text = "Elixir is Elixir; elixir pipes beat nesting. nesting is evil."
IO.inspect(Pipeline.word_stats(text), label: "  top3")

IO.puts("\n-- with 语句 --")
IO.inspect(Pipeline.parse_age("42"),   label: "  parse_age(\"42\")  ")
IO.inspect(Pipeline.parse_age("-1"),   label: "  parse_age(\"-1\")  ")
IO.inspect(Pipeline.parse_age("abc"),  label: "  parse_age(\"abc\") ")

IO.puts("""

=== 重点理解 ===
- Elixir 是 Erlang 生态的"人类友好表层"：同一台 BEAM VM, 同一套 OTP
- 多子句函数 + 守卫 = 模式匹配的最常用形态, 比 if/case 更符合 FP 风格
- 管道 |> 让数据流水线天然可读, 是 Elixir 最显眼的独家标志
- with 解决了"多个 {:ok, _} / {:error, _} 串起来"的嵌套地狱
- 对照 Erlang: 同样跑在 BEAM 上, 但语法 + 宏 + 工具链是 Elixir 独立价值
""")
