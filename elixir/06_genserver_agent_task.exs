# Elixir 函数式编程 Demo 06: GenServer / Agent / Task —— OTP 的 Elixir 习惯写法
#
# 三者对比（底层都是 BEAM 进程 + 消息传递）:
#   Task     : 一次性异步计算, 适合"并发拉数据 / 并行 map"
#   Agent    : 极简状态盒子, API = get / update, 适合"共享的小状态"
#   GenServer: 通用状态机, 支持 call / cast / info / init / terminate, 适合"有行为的服务"

defmodule WordCounter do
  # 用 GenServer 封装一个"词频统计服务"
  use GenServer

  # === Client API ===
  def start_link(opts \\ []), do: GenServer.start_link(__MODULE__, %{}, opts)
  def add(pid, word),         do: GenServer.cast(pid, {:add, word})
  def top(pid, n),            do: GenServer.call(pid, {:top, n})
  def total(pid),             do: GenServer.call(pid, :total)
  def stop(pid),              do: GenServer.stop(pid)

  # === Callbacks ===
  @impl true
  def init(state), do: {:ok, state}

  @impl true
  def handle_cast({:add, word}, state) do
    {:noreply, Map.update(state, word, 1, &(&1 + 1))}
  end

  @impl true
  def handle_call({:top, n}, _from, state) do
    top = state |> Enum.sort_by(fn {_w, c} -> -c end) |> Enum.take(n)
    {:reply, top, state}
  end

  def handle_call(:total, _from, state) do
    {:reply, Enum.reduce(state, 0, fn {_, c}, acc -> acc + c end), state}
  end
end

defmodule Counters do
  # Agent: 最轻量的"可变状态盒子", 适合配置 / 缓存这类小东西
  def start, do: Agent.start_link(fn -> %{} end, name: __MODULE__)
  def bump(key), do: Agent.update(__MODULE__, &Map.update(&1, key, 1, fn x -> x + 1 end))
  def snapshot, do: Agent.get(__MODULE__, & &1)
end

IO.puts("=== Elixir Demo 06: GenServer / Agent / Task ===\n")

# === 1) GenServer 示范 ========================================
{:ok, wc} = WordCounter.start_link()
text = "elixir beats nesting elixir loves pipes beats nesting pipes pipes"
for w <- String.split(text), do: WordCounter.add(wc, w)

Process.sleep(50) # 等 cast 消费完
IO.inspect(WordCounter.top(wc, 3), label: "top3 words")
IO.inspect(WordCounter.total(wc),  label: "total")
WordCounter.stop(wc)

# === 2) Agent 示范 ===========================================
{:ok, _} = Counters.start()
for k <- [:a, :a, :b, :a, :c, :b], do: Counters.bump(k)
IO.inspect(Counters.snapshot(), label: "agent snapshot")

# === 3) Task.async + await: 并发拉多个慢数据源 ================
slow = fn tag, ms ->
  Process.sleep(ms)
  "result-#{tag}"
end

t0 = System.monotonic_time(:millisecond)

tasks = [
  Task.async(fn -> slow.(:a, 150) end),
  Task.async(fn -> slow.(:b, 200) end),
  Task.async(fn -> slow.(:c, 180) end)
]

results = Task.await_many(tasks, 1_000)
dt = System.monotonic_time(:millisecond) - t0

IO.inspect(results, label: "Task.await_many")
IO.puts("  并发耗时: #{dt}ms (如果串行应 > 530ms)")

# === 4) Task.async_stream: 并发 map, 对应 FP 的 traverse ======
sum_of_squares =
  1..20
  |> Task.async_stream(fn x -> Process.sleep(20); x * x end, max_concurrency: 4)
  |> Enum.reduce(0, fn {:ok, v}, acc -> acc + v end)

IO.puts("  async_stream 并发 map 后求和 = #{sum_of_squares}")

IO.puts("""

=== 重点理解 ===
- 全都是 BEAM 进程 + 消息传递, 只是抽象程度不同
    Task    : 一次性 async/await / 并发流, 无长期状态
    Agent   : 共享一小块可变状态, API 极简
    GenServer: 通用服务, 有生命周期和多种回调, 适合写"长期存在的服务"
- cast (不等回复) vs call (等回复) —— 一切 OTP 交互的两种基本形态
- Task.async_stream 是 Elixir 最常用的"并发 map", 语义接近 Haskell traverse / Rust rayon par_iter
- Agent/Task 的一切都可以用 GenServer 写, 但用对的抽象能少很多模板
""")
