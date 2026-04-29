# Elixir 函数式编程 Demo 14: ExUnit + doctest + Mox + StreamData —— Elixir 测试栈
#
# 对标 Haskell 的 QuickCheck / Rust 的 proptest / Erlang 的 PropEr.
# Elixir 官方/准官方测试四件套:
#   ExUnit      : 标准测试框架, 断言 + 生命周期 + tags
#   doctest     : 文档里的 iex> 示例自动变成测试
#   Mox         : 用 behaviour 做 "类型安全的显式 mock"
#   StreamData  : Property-based testing (随机生成输入验证不变量)
#
# 运行: elixir 14_exunit_doctest_mox_streamdata.exs

Mix.install([
  {:mox,         "~> 1.2"},
  {:stream_data, "~> 1.1"}
])

# ---------- 被测业务 ----------
defmodule Notifier do
  @moduledoc """
  发送通知的对外接口, 实现可换 (真实 SMS / 假的/测试).

  ## Examples

      iex> Notifier.format_phone("+86-13800001111")
      "8613800001111"

      iex> Notifier.format_phone(" 13900002222 ")
      "8613900002222"
  """

  @callback send(to :: String.t(), body :: String.t()) :: :ok | {:error, term()}

  # 被 doctest 直接测试的纯函数
  def format_phone(s) do
    s
    |> String.trim()
    |> String.replace(~r/[^\d]/, "")
    |> prefix_if_needed()
  end
  defp prefix_if_needed("86" <> _ = s), do: s
  defp prefix_if_needed(s),             do: "86" <> s
end

# ---------- 纯逻辑: 用 changeset 判断 "要不要发" ----------
defmodule Alarm do
  def should_notify?(%{severity: sev, quiet_hours?: q?}) do
    sev in [:high, :critical] and not q?
  end

  # 可被 property 测试的不变量: 反例应该永远不发
  def never_notify?(_), do: false
end

# ---------- 注册 Mock 实现 (必须在测试外, 声明阶段) ----------
Mox.defmock(NotifierMock, for: Notifier)

defmodule AlarmService do
  # 通过配置注入: 生产用 RealNotifier, 测试用 NotifierMock
  def notifier, do: Application.get_env(:demo, :notifier, NotifierMock)

  def fire(alert) do
    if Alarm.should_notify?(alert) do
      notifier().send(alert.to, "[#{alert.severity}] #{alert.title}")
    else
      :skipped
    end
  end
end

# ---------- ExUnit ----------
ExUnit.start(autorun: false)
Application.put_env(:demo, :notifier, NotifierMock)

defmodule NotifierTest do
  use ExUnit.Case, async: true
  import Mox
  setup :verify_on_exit!

  doctest Notifier                               # doctest 自动执行上面 @moduledoc 的 iex>

  test "format_phone 其它几种形态" do
    assert Notifier.format_phone("13800001111") == "8613800001111"
    assert Notifier.format_phone("86-138 0000 1111") == "8613800001111"
  end

  describe "AlarmService.fire/1" do
    test "critical + 非静默 => 发送" do
      expect(NotifierMock, :send, fn to, body ->
        assert to == "13800001111"
        assert body =~ "[critical]"
        :ok
      end)

      assert :ok =
               AlarmService.fire(%{
                 to: "13800001111",
                 severity: :critical,
                 title: "CPU 99%",
                 quiet_hours?: false
               })
    end

    test "低级别 => 不发送, mock 绝不应被调用" do
      # 不 expect 任何调用; verify_on_exit! 会保证 mock 真的没被碰
      assert :skipped =
               AlarmService.fire(%{
                 to: "x",
                 severity: :info,
                 title: "fyi",
                 quiet_hours?: false
               })
    end

    test "静默时段 => 即使是 critical 也不发" do
      assert :skipped =
               AlarmService.fire(%{
                 to: "x",
                 severity: :critical,
                 title: "night",
                 quiet_hours?: true
               })
    end
  end

  # ---------- Property-based ----------
  use ExUnitProperties

  property "format_phone 总是以 86 开头且全数字" do
    check all raw <- StreamData.string(:ascii, min_length: 3, max_length: 20) do
      cleaned = Notifier.format_phone(raw)
      assert String.starts_with?(cleaned, "86")
      assert cleaned =~ ~r/^\d+$/
    end
  end

  property "Alarm.should_notify? 对低级别永远返回 false" do
    check all sev <- StreamData.member_of([:info, :low]),
              q?  <- StreamData.boolean() do
      refute Alarm.should_notify?(%{severity: sev, quiet_hours?: q?})
    end
  end
end

ExUnit.run()

IO.puts("""

=== 重点理解 ===
- ExUnit 就是 Elixir 的标配测试框架, async: true 让不同 test 文件并行跑
- doctest Notifier 这一行会把模块文档里所有 `iex>` 自动变成测试 - "文档即测试"
- Mox 要求: 先 defmock NameMock, for: SomeBehaviour, 运行时业务从 config 拿真实 or mock 实现
    * expect/3 声明本次调用的期望 + 返回
    * verify_on_exit! 在测试结束时保证"该被调的都被调了"
    * 最大好处: 签名和 behaviour 一致, IDE 能提示, mock 不会跟业务签名漂移
- StreamData + property: check all 生成器 do ... end, Elixir 风格的 QuickCheck
- 测试策略三段金字塔:
    大量: 单元 / property / doctest  (纯函数, 不碰副作用)
    适量: 接口 + Mox                 (把副作用封成 behaviour)
    少量: 端到端集成 (真实 DB/HTTP)   (配 ExUnit 的 @tag :integration + setup_all)
""")
