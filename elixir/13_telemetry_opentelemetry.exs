# Elixir 函数式编程 Demo 13: Telemetry + OpenTelemetry — 可观测的 Elixir 标配
#
# Telemetry 是 Elixir/Erlang 生态的"可观测性标准协议":
#   :telemetry.execute([:my_app, :request, :stop], %{duration: ns}, %{route: "/x"})
# 任何库 (Ecto, Phoenix, Broadway, Finch) 都用同一个 API 发事件,
# 应用方只需在启动时 attach 一次 handler, 就能把所有指标送到你想要的目的地:
# 终端 / Prometheus (PromEx) / OTLP (OpenTelemetry) / Datadog / ...
#
# 本 Demo 只用 :telemetry (无 TLS/网络依赖), 展示完整的
#   "产生事件 -> 聚合统计 -> 周期性 flush"  闭环, 这就是 PromEx / OTel 的底层机制。

Mix.install([
  {:telemetry,         "~> 1.2"},
  {:telemetry_metrics, "~> 1.0"}
])

# -- 1) 被测代码: 只管 emit 事件, 不关心谁在听 --------------------
defmodule PaymentService do
  def charge(user, amount) do
    start = System.monotonic_time()
    :telemetry.execute([:demo, :payment, :start], %{}, %{user: user, amount: amount})

    # 模拟业务
    Process.sleep(:rand.uniform(30))
    result =
      cond do
        amount <= 0        -> {:error, :invalid}
        :rand.uniform() < 0.1 -> {:error, :upstream}
        true               -> {:ok, "tx-#{:rand.uniform(9999)}"}
      end

    duration = System.monotonic_time() - start
    :telemetry.execute(
      [:demo, :payment, :stop],
      %{duration: duration},
      %{user: user, amount: amount, status: elem(result, 0)}
    )
    result
  end
end

# -- 2) 指标聚合器: 用 GenServer 模拟 "Prometheus registry" --------
defmodule Metrics do
  use GenServer

  def start_link(_), do: GenServer.start_link(__MODULE__, %{counter: %{}, hist: %{}}, name: __MODULE__)
  def snapshot, do: GenServer.call(__MODULE__, :snapshot)

  @impl true
  def init(state) do
    :telemetry.attach_many(
      "demo-handler",
      [
        [:demo, :payment, :stop]
      ],
      &__MODULE__.handle/4,
      nil
    )
    {:ok, state}
  end

  # 被 telemetry 调用, 注意要非常轻量不要阻塞
  def handle([:demo, :payment, :stop], measures, meta, _cfg) do
    GenServer.cast(__MODULE__, {:record, measures, meta})
  end

  @impl true
  def handle_cast({:record, %{duration: d}, meta}, state) do
    status = meta.status
    counter = Map.update(state.counter, {:payment_count, status}, 1, &(&1 + 1))

    d_ms = System.convert_time_unit(d, :native, :microsecond) / 1000.0
    hist = Map.update(state.hist, :payment_ms, [d_ms], &[d_ms | &1])

    {:noreply, %{state | counter: counter, hist: hist}}
  end

  @impl true
  def handle_call(:snapshot, _from, state) do
    hist_stats =
      case state.hist[:payment_ms] do
        nil -> nil
        vs  ->
          sorted = Enum.sort(vs)
          n = length(sorted)
          %{
            count: n,
            avg:   Float.round(Enum.sum(sorted) / n, 2),
            p50:   Enum.at(sorted, div(n, 2)) |> Float.round(2),
            p95:   Enum.at(sorted, min(n - 1, trunc(n * 0.95))) |> Float.round(2),
            max:   Float.round(List.last(sorted), 2)
          }
      end
    {:reply, %{counters: state.counter, payment_ms: hist_stats}, state}
  end
end

IO.puts("=== Elixir Demo 13: Telemetry 指标 ===\n")

{:ok, _} = Metrics.start_link([])

# 模拟一批支付
for i <- 1..100 do
  amount = if rem(i, 17) == 0, do: -1, else: :rand.uniform(1000)
  _ = PaymentService.charge("u#{rem(i, 5)}", amount)
end

Process.sleep(50) # 等 cast 消化完
IO.inspect(Metrics.snapshot(), label: "snapshot")

IO.puts("""

=== 重点理解 ===
- :telemetry.execute(event, measures, meta): 库作者只管喊一嗓子, 不知道谁在听
- :telemetry.attach/attach_many: 应用方在启动时一次性订阅感兴趣的事件
- 常见 :telemetry 命名三段式: [:app, :subject, :start|:stop|:exception]
    Ecto:     [:my_repo, :query, :stop]         -> 每条 SQL 的耗时
    Phoenix:  [:phoenix, :endpoint, :stop]       -> 每个 HTTP 请求
    Broadway: [:broadway, :processor, :stop]     -> 每条消息处理
- 生产中把 Metrics 这一步换成:
    * PromEx -> Prometheus 拉
    * OpenTelemetry.Exporter -> OTLP 推到 Tempo/Jaeger/Datadog
- Handler 必须轻量, 业务线程才不会被打点拖垮; 重活统一丢给 GenServer/Broadway
""")
