# Elixir 函数式编程 Demo 11: LiveView — 服务端驱动的实时 UI (心智模型版)
#
# LiveView 的真正秘密不是"WebSocket 推数据", 而是一个纯函数式的渲染模型:
#
#     state0 --event--> update --> state1 --render--> html
#     (socket.assigns)            (socket.assigns)
#
# 它和 Elm / React 的 (model, msg, update, view) 是同一个模型,
# 只是 "update / render" 都跑在服务端, diff 之后把增量推给浏览器。
#
# 真实 LiveView 依赖完整 Phoenix 栈 + mix new --live 工程,
# 本 Demo 用 200 行内自己实现"迷你 LiveView"心智模型:
#   - assigns (socket 的状态盒)
#   - mount / handle_event / render 三件套
#   - "事件 -> 更新 -> 重新渲染" 的闭环
# 这样你脱离 Phoenix 框架也能说清 LiveView 究竟在做什么。

defmodule Socket do
  @moduledoc "socket 是 LiveView 里承载状态的数据结构, 这里极简化"
  defstruct assigns: %{}

  def assign(%__MODULE__{} = s, key, value),
    do: %{s | assigns: Map.put(s.assigns, key, value)}

  def update(%__MODULE__{} = s, key, fun),
    do: %{s | assigns: Map.update!(s.assigns, key, fun)}
end

defmodule MiniLiveView do
  @moduledoc "对齐 Phoenix.LiveView 的生命周期接口"
  @callback mount(params :: map, socket :: Socket.t()) :: Socket.t()
  @callback handle_event(event :: String.t(), payload :: map, socket :: Socket.t()) :: Socket.t()
  @callback render(assigns :: map) :: String.t()
end

defmodule CounterLive do
  @behaviour MiniLiveView
  import Socket, only: [assign: 3, update: 3]

  @impl true
  def mount(_params, socket) do
    socket
    |> assign(:count, 0)
    |> assign(:log,   [])
  end

  @impl true
  def handle_event("inc", _, socket),
    do: socket |> update(:count, &(&1 + 1))    |> push_log("click inc")
  def handle_event("dec", _, socket),
    do: socket |> update(:count, &(&1 - 1))    |> push_log("click dec")
  def handle_event("reset", _, socket),
    do: socket |> assign(:count, 0)            |> push_log("reset")
  def handle_event("add", %{"n" => n}, socket),
    do: socket |> update(:count, &(&1 + n))    |> push_log("add #{n}")

  defp push_log(socket, msg), do: update(socket, :log, &Enum.take([msg | &1], 5))

  @impl true
  def render(%{count: c, log: log}) do
    """
    <div>
      <h1>Count: #{c}</h1>
      <button phx-click="dec">-1</button>
      <button phx-click="inc">+1</button>
      <button phx-click="reset">reset</button>
      <ul>
    #{log |> Enum.map(&"    <li>#{&1}</li>") |> Enum.join("\n")}
      </ul>
    </div>
    """
  end
end

# ---------------- Runtime: 模拟 LiveView 的 "process that renders" ----------------

defmodule LiveProcess do
  use GenServer

  def start_link(view_module),
    do: GenServer.start_link(__MODULE__, view_module)

  def send_event(pid, name, payload \\ %{}),
    do: GenServer.call(pid, {:event, name, payload})

  def html(pid), do: GenServer.call(pid, :html)

  @impl true
  def init(view_module) do
    socket = view_module.mount(%{}, %Socket{})
    {:ok, %{view: view_module, socket: socket}}
  end

  @impl true
  def handle_call({:event, name, payload}, _from, state) do
    new_socket = state.view.handle_event(name, payload, state.socket)
    {:reply, state.view.render(new_socket.assigns), %{state | socket: new_socket}}
  end

  def handle_call(:html, _from, state),
    do: {:reply, state.view.render(state.socket.assigns), state}
end

IO.puts("=== Elixir Demo 11: 迷你 LiveView 心智模型 ===\n")

{:ok, pid} = LiveProcess.start_link(CounterLive)
IO.puts("初始渲染:\n#{LiveProcess.html(pid)}")

IO.puts("\n--- 触发 3 次 inc ---")
for _ <- 1..3, do: LiveProcess.send_event(pid, "inc")
IO.puts(LiveProcess.html(pid))

IO.puts("\n--- add n=10 ---")
LiveProcess.send_event(pid, "add", %{"n" => 10})
IO.puts(LiveProcess.html(pid))

IO.puts("\n--- reset ---")
LiveProcess.send_event(pid, "reset")
IO.puts(LiveProcess.html(pid))

IO.puts("""

=== 重点理解 ===
- LiveView 的核心是 (assigns, event) -> assigns' -> html, 纯函数视角
- 真实的 LiveView:
    * 用 WebSocket/LongPoll 连接一个驻守在服务器的 LV 进程
    * 每个客户端对应一个 LiveProcess, 状态完全在服务端, 不用前端状态管理
    * diff 后只推"哪些片段变了", 前端用极薄的 phoenix_live_view.js 做 patch
- 它解决了前端状态管理的大部分复杂度, 代价是: 每个连接要占一个进程 (BEAM 擅长这个)
- 和 React / Elm 的心智模型完全同构, 区别只是"update/render 在哪边跑"
- 真正上 LiveView 需要: mix phx.new --live, 然后阅读 heex 模板 + PubSub + Presence
""")
