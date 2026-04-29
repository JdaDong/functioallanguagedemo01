# Elixir 函数式编程 Demo 08: Task.Supervisor × Task.async_stream —— 受监督的并发管道
#
# Task.async_stream 是 Elixir 最常用的"并发 map / traverse":
#   - 控制 max_concurrency
#   - 支持 timeout / on_timeout
#   - 用 ordered: false 可以按"谁先完成先出"流式消费
# 搭配 Task.Supervisor 就能"让每个并发任务都挂在监督树下", 失败不会把调用者拖死。

defmodule ImageWorker do
  @moduledoc "模拟一个会慢、会偶尔失败的外部调用"

  def download(url, tag) do
    latency = :rand.uniform(200)
    Process.sleep(latency)

    cond do
      String.contains?(url, "boom") -> raise "remote failure for #{url}"
      String.contains?(url, "slow") -> Process.sleep(500); {:ok, "#{tag}:bytes"}
      true                          -> {:ok, "#{tag}:#{latency}ms"}
    end
  end
end

defmodule Pipeline do
  def run(urls, sup) do
    Task.Supervisor.async_stream_nolink(
      sup,
      urls,
      fn {tag, url} -> ImageWorker.download(url, tag) end,
      max_concurrency: 4,
      timeout: 300,
      on_timeout: :kill_task,
      ordered: false
    )
    |> Enum.map(fn
      {:ok, {:ok, payload}}  -> {:ok, payload}
      {:ok, {:error, e}}     -> {:error, e}
      {:exit, :timeout}      -> {:error, :timeout}
      {:exit, reason}        -> {:error, {:crashed, reason}}
    end)
  end
end

IO.puts("=== Elixir Demo 08: Task.Supervisor × async_stream ===\n")

# 顶层监督树启一个 Task.Supervisor
{:ok, _top} =
  Supervisor.start_link(
    [{Task.Supervisor, name: PipelineSup}],
    strategy: :one_for_one
  )

urls = [
  {:a, "https://cdn/a.png"},
  {:b, "https://cdn/b.png"},
  {:c, "https://cdn/boom.png"},   # 会 raise
  {:d, "https://cdn/d.png"},
  {:e, "https://cdn/slow.png"},   # 会 timeout
  {:f, "https://cdn/f.png"},
  {:g, "https://cdn/g.png"},
  {:h, "https://cdn/h.png"}
]

t0 = System.monotonic_time(:millisecond)
results = Pipeline.run(urls, PipelineSup)
dt = System.monotonic_time(:millisecond) - t0

IO.puts("耗时: #{dt}ms\n")
for r <- results, do: IO.inspect(r, label: "  result")

ok_cnt  = Enum.count(results, fn {x, _} -> x == :ok end)
err_cnt = Enum.count(results, fn {x, _} -> x == :error end)
IO.puts("\n统计: ok=#{ok_cnt}, err=#{err_cnt}")

IO.puts("""

=== 重点理解 ===
- async_stream = Enum.map 的"并发、有上限、可超时、可不阻塞调用者"版本
- ordered: false 让早完成的先返回, 适合 I/O 重的并发抓取
- async_stream_nolink + Task.Supervisor 让"某个子任务 crash 不会把调用方一起带走"
- on_timeout: :kill_task 把超时统一当作退出, 简化错误处理
- 心法: 并发管道的每一环都要明确回答 "出错怎么办 / 超时怎么办 / 背压怎么办"
- 对照: Rust rayon par_iter / Haskell traverseConcurrently / Scala fs2 parEvalMap
""")
