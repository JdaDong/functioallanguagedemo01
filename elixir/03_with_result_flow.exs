# Elixir 函数式编程 Demo 03: with 语句 × Result-like 流程控制
#
# with 是 Elixir 用来"多步骤顺序匹配 + 短路失败"的官方方式，
# 在 FP 世界里正对应：
#   - Haskell 的 do-notation over Either
#   - Rust 的 ? 操作符（try-chain）
#   - Scala 的 for-comprehension + Either

defmodule Ticket do
  @enforce_keys [:event, :seat, :user_id]
  defstruct [:event, :seat, :user_id]
end

defmodule Inventory do
  @moduledoc "模拟库存：某个 seat 是否还可售"
  @seats %{
    {"concert-42", "A1"} => :available,
    {"concert-42", "A2"} => :sold,
    {"concert-42", "A3"} => :available
  }

  def reserve(event, seat) do
    case Map.get(@seats, {event, seat}) do
      :available -> {:ok, :reserved}
      :sold      -> {:error, :seat_taken}
      nil        -> {:error, :unknown_seat}
    end
  end
end

defmodule Wallet do
  @balances %{"u1" => 500, "u2" => 10}

  def charge(user_id, amount) do
    case Map.get(@balances, user_id) do
      nil                       -> {:error, :unknown_user}
      bal when bal >= amount    -> {:ok, bal - amount}
      _                         -> {:error, :insufficient_funds}
    end
  end
end

defmodule Booking do
  # 朴素版：深度嵌套 case（"结构性错误代码的样子"）
  def book_nested(user_id, event, seat, price) do
    case Inventory.reserve(event, seat) do
      {:ok, _} ->
        case Wallet.charge(user_id, price) do
          {:ok, left} ->
            {:ok, %{ticket: %Ticket{event: event, seat: seat, user_id: user_id}, left: left}}
          err -> err
        end
      err -> err
    end
  end

  # Elixir 风格：with 版（短路 + 扁平）
  def book(user_id, event, seat, price) do
    with {:ok, :reserved} <- Inventory.reserve(event, seat),
         {:ok, left}      <- Wallet.charge(user_id, price) do
      {:ok, %{ticket: %Ticket{event: event, seat: seat, user_id: user_id}, left: left}}
    else
      {:error, :seat_taken}        -> {:error, "座位 #{seat} 已售出"}
      {:error, :unknown_seat}      -> {:error, "没有这个座位"}
      {:error, :insufficient_funds} -> {:error, "余额不足"}
      {:error, :unknown_user}      -> {:error, "用户不存在"}
    end
  end

  # 常见 FP 模式：把一列 {:ok, x} | {:error, e} 收拢成 {:ok, [x,...]} | {:error, e}
  def sequence(results), do: sequence(results, [])
  defp sequence([], acc),                 do: {:ok, Enum.reverse(acc)}
  defp sequence([{:ok, x} | t], acc),     do: sequence(t, [x | acc])
  defp sequence([{:error, _} = e | _], _), do: e
end

IO.puts("=== Elixir Demo 03: with 语句 × Result 流程 ===\n")

scenarios = [
  {"u1", "concert-42", "A1", 200},   # OK
  {"u1", "concert-42", "A2", 200},   # 座位被抢
  {"u2", "concert-42", "A3", 200},   # 余额不足
  {"u1", "concert-42", "Z9", 200},   # 不存在的座位
  {"u9", "concert-42", "A3", 100}    # 用户不存在
]

for {uid, ev, seat, price} <- scenarios do
  IO.inspect(Booking.book(uid, ev, seat, price),
    label: "book(#{uid}, #{seat}, #{price})")
end

IO.puts("\n-- sequence: 把多个 Result 收拢 --")
IO.inspect(Booking.sequence([{:ok, 1}, {:ok, 2}, {:ok, 3}]), label: "all ok")
IO.inspect(Booking.sequence([{:ok, 1}, {:error, :boom}, {:ok, 3}]), label: "has err")

IO.puts("""

=== 重点理解 ===
- with 的核心: 左边 <- 右边, 匹配成功才继续, 失败跳去 else
- else 分支可以按错误类型分派, 比嵌套 case 清晰得多
- 和 Haskell do-over-Either / Rust ? / Scala for-Either 在语义上完全同构
- sequence / traverse 在 Elixir 里是手写的小工具, 库里可以用 Enum.reduce_while
- 心法: 以"失败能短路"为第一等公民, 让主流程只谈成功路径
""")
