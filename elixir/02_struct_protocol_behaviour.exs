# Elixir 函数式编程 Demo 02: struct / protocol / behaviour
#
# 这三样合起来 = Elixir 的"多态三件套"，对标：
#   - struct    ↔ 带字段的具名数据类型（≈ Haskell record / Rust struct）
#   - protocol  ↔ 按"值的类型"分派的开放多态（≈ Haskell type class / Rust trait）
#   - behaviour ↔ 接口契约 + @impl 检查（≈ Java interface / Rust trait 的静态部分）
#
# 用一个"计费对象"例子把三者串起来。

defmodule Money do
  @enforce_keys [:amount, :currency]
  defstruct [:amount, :currency]

  def new(amount, currency) when is_number(amount) and amount >= 0,
    do: %__MODULE__{amount: amount, currency: currency}
end

# === 1) Protocol: 按"值的类型"分派（开放式，后续模块可以扩展实现） =====
defprotocol Billable do
  @fallback_to_any true
  @doc "计算该对象的应付金额（返回 Money）"
  def price(item)
end

defimpl Billable, for: Any do
  def price(_), do: Money.new(0, "CNY")
end

defmodule Book do
  @enforce_keys [:title, :price_cents]
  defstruct [:title, :price_cents]
end

defimpl Billable, for: Book do
  def price(%Book{price_cents: c}), do: Money.new(c / 100, "CNY")
end

defmodule Subscription do
  @enforce_keys [:plan, :months]
  defstruct [:plan, :months]
end

defimpl Billable, for: Subscription do
  def price(%Subscription{plan: :basic, months: m}), do: Money.new(29.0 * m, "CNY")
  def price(%Subscription{plan: :pro,   months: m}), do: Money.new(99.0 * m, "CNY")
end

# === 2) Behaviour: 给"计费后端"定契约 ===============================
defmodule BillingBackend do
  @callback charge(Money.t()) :: {:ok, String.t()} | {:error, term()}
  @callback name() :: String.t()
end

defmodule FakeBankBackend do
  @behaviour BillingBackend

  @impl true
  def name, do: "fake-bank"

  @impl true
  def charge(%Money{amount: a, currency: c}) when a > 0,
    do: {:ok, "fake-bank#{:rand.uniform(9999)}:#{a}#{c}"}

  def charge(_), do: {:error, :zero_amount}
end

defmodule LoggingBackend do
  @behaviour BillingBackend
  @impl true
  def name, do: "logging"
  @impl true
  def charge(%Money{} = m) do
    IO.puts("  [logging] 假装收了 #{m.amount} #{m.currency}")
    {:ok, "log-only"}
  end
end

# === 3) 使用：protocol 选"值的形状"，behaviour 选"后端实现" ============
defmodule Checkout do
  def pay(item, backend) when is_atom(backend) do
    money = Billable.price(item)
    IO.puts("[#{backend.name()}] 金额 = #{money.amount} #{money.currency}")
    backend.charge(money)
  end
end

defmodule Main do
  def items do
    [
      %Book{title: "SICP", price_cents: 12800},
      %Subscription{plan: :pro, months: 3},
      %Subscription{plan: :basic, months: 12},
      "随便一个字符串"  # 走 fallback_to_any
    ]
  end

  def run do
    IO.puts("=== Elixir Demo 02: struct / protocol / behaviour ===\n")

    for item <- items() do
      IO.inspect(item, label: "item")
      IO.inspect(Checkout.pay(item, FakeBankBackend), label: "  -> bank")
      IO.inspect(Checkout.pay(item, LoggingBackend),  label: "  -> log ")
      IO.puts("")
    end
  end
end

Main.run()

IO.puts("""
=== 重点理解 ===
- defstruct: 带 @enforce_keys 的 struct 是 Elixir 的"具名记录"
- defprotocol + defimpl: 按数据形状分派, 允许跨项目、跨模块扩展 (ad-hoc 多态)
- @behaviour + @impl: 模块级契约, 编译器会检查回调是否实现正确
- 选择心法:
    值本身长什么样    -> protocol
    接入的插件长什么样 -> behaviour
- 对标:
    protocol  ≈ Haskell type class / Rust trait (带 impl for 具体类型)
    behaviour ≈ Java interface / Erlang -behaviour() 注解
""")
