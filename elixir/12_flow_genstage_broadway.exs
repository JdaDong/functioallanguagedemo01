# Elixir 函数式编程 Demo 12: Flow / GenStage / Broadway — 流式数据管道
#
# Elixir 在"流 + 背压"上有一组官方组件, 从底层到高层:
#   GenStage  : 拉取式的生产者/消费者抽象 (demand-driven), 是下两者的底座
#   Flow      : GenStage 的高层 API, 像 Enum/Stream 一样写并行管道
#   Broadway  : 接消息中间件 (SQS / RabbitMQ / Kafka) 的成熟管道框架
#
# 本 Demo 用 Flow 展示重点: 并行、分区、背压自动生效。
# 运行: elixir 12_flow_genstage_broadway.exs

Mix.install([
  {:flow, "~> 1.2"}
])

defmodule Fake do
  @doc "模拟慢的 CPU 工作"
  def heavy(x) do
    Process.sleep(10)
    x * x
  end

  def is_even(x), do: rem(x, 2) == 0
end

IO.puts("=== Elixir Demo 12: Flow 并行管道 ===\n")

# -- 基线: 串行 Enum ----------------------------------------------
t0 = System.monotonic_time(:millisecond)
serial =
  1..200
  |> Enum.map(&Fake.heavy/1)
  |> Enum.filter(&Fake.is_even/1)
  |> Enum.sum()
dt_serial = System.monotonic_time(:millisecond) - t0
IO.puts("串行 Enum:   sum=#{serial}, 耗时 #{dt_serial}ms")

# -- Flow: 自动拆分到多 stage, 按核数并行 ---------------------------
t1 = System.monotonic_time(:millisecond)
parallel =
  1..200
  |> Flow.from_enumerable(stages: 4, max_demand: 10)
  |> Flow.map(&Fake.heavy/1)
  |> Flow.filter(&Fake.is_even/1)
  |> Enum.sum()
dt_parallel = System.monotonic_time(:millisecond) - t1
IO.puts("Flow 并行:   sum=#{parallel}, 耗时 #{dt_parallel}ms")

# -- Flow + 分区聚合: 按 key 做 word count 的经典场景 -------------
text = """
elixir beats nesting
elixir loves pipes
beats nesting pipes pipes
functional style functional style
pipes functional elixir
"""

counts =
  text
  |> String.split([" ", "\n"], trim: true)
  |> Flow.from_enumerable(stages: 4)
  |> Flow.partition(stages: 2)                    # 按 hash 分到不同的下游 stage
  |> Flow.reduce(fn -> %{} end, fn word, acc ->
    Map.update(acc, word, 1, &(&1 + 1))
  end)
  |> Flow.on_trigger(fn acc -> {[acc], acc} end)   # 聚合完把 map 吐出来
  |> Enum.reduce(%{}, fn m, acc -> Map.merge(acc, m, fn _k, a, b -> a + b end) end)

IO.inspect(counts |> Enum.sort_by(fn {_, c} -> -c end) |> Enum.take(5),
  label: "Flow wordcount top5")

IO.puts("""

=== 重点理解 ===
- Flow 的并行 = 多个下游 stage 按 demand 向上游"拉", 背压自动生效
    * stages: 并行度, 通常 = System.schedulers_online()
    * max_demand: 每个 stage 一次最多要多少条, 控单批大小
- Flow.partition: 让相同 key 落到同一个 stage, 再 reduce, 相当于 MapReduce 的 shuffle
- Flow.on_trigger: 每个 stage 累完后"吐结果", 外层再做最终 merge (经典 combine 模式)
- GenStage (底层):  显式写 Producer / ProducerConsumer / Consumer, 自定义 demand 协议
- Broadway (应用): 一线连 Kafka/SQS, 带自动 ack / batcher / rate_limit, 写业务只关心 handle_message
- 对照:
    Flow     ≈ Rust rayon par_iter / Scala fs2 parEvalMap
    GenStage ≈ Akka Streams graph
    Broadway ≈ Kafka Streams / Pulsar Functions 的轻量版
""")
