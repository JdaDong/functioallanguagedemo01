# Elixir 函数式编程 Demo 07: Supervisor / DynamicSupervisor / Registry
#
# BEAM 的"让它挂, 让监督树重启"理念, 在 Elixir 里这样表达:
#   - Supervisor         : 启动时就知道孩子列表的"静态监督树"
#   - DynamicSupervisor  : 运行时按需 start_child 的"动态工人池"
#   - Registry           : "给进程起名字并按名字查", 取代手工的 ETS 名字表
#
# 本 Demo 用"聊天室"场景把三者串起来:
#   - 每个聊天室是一个 Room GenServer
#   - 用 DynamicSupervisor 按需创建 / 终止 Room
#   - 用 Registry {:room, name} 按名字找到 Room 进程

defmodule Room do
  use GenServer, restart: :transient

  # 通过 Registry 起别名, 让外部用名字而不是 pid 找人
  def via(name), do: {:via, Registry, {ChatRegistry, {:room, name}}}

  def start_link(name), do: GenServer.start_link(__MODULE__, name, name: via(name))
  def post(name, user, msg), do: GenServer.cast(via(name), {:post, user, msg})
  def history(name), do: GenServer.call(via(name), :history)

  @impl true
  def init(name), do: {:ok, %{name: name, log: []}}

  @impl true
  def handle_cast({:post, user, msg}, state) do
    {:noreply, update_in(state.log, &[{user, msg} | &1])}
  end

  @impl true
  def handle_call(:history, _from, state) do
    {:reply, Enum.reverse(state.log), state}
  end
end

defmodule ChatSupervisor do
  use DynamicSupervisor

  def start_link(_), do: DynamicSupervisor.start_link(__MODULE__, :ok, name: __MODULE__)
  @impl true
  def init(:ok), do: DynamicSupervisor.init(strategy: :one_for_one)

  def open(name) do
    DynamicSupervisor.start_child(__MODULE__, %{
      id: {Room, name},
      start: {Room, :start_link, [name]},
      restart: :transient
    })
  end

  def close(name) do
    case Registry.lookup(ChatRegistry, {:room, name}) do
      [{pid, _}] -> DynamicSupervisor.terminate_child(__MODULE__, pid)
      []         -> {:error, :not_found}
    end
  end

  def list_rooms do
    Registry.select(ChatRegistry, [{{:"$1", :_, :_}, [], [:"$1"]}])
    |> Enum.map(fn {:room, name} -> name end)
  end
end

defmodule App do
  def start do
    # 顶层静态监督树: Registry + DynamicSupervisor
    children = [
      {Registry, keys: :unique, name: ChatRegistry},
      ChatSupervisor
    ]
    Supervisor.start_link(children, strategy: :one_for_one, name: App.TopSup)
  end
end

IO.puts("=== Elixir Demo 07: Supervisor / DynamicSupervisor / Registry ===\n")

{:ok, _top} = App.start()

{:ok, _} = ChatSupervisor.open("general")
{:ok, _} = ChatSupervisor.open("random")

Room.post("general", "alice", "hello!")
Room.post("general", "bob",   "hi ada")
Room.post("random",  "carol", "who am I?")

IO.inspect(ChatSupervisor.list_rooms(), label: "list_rooms")
IO.inspect(Room.history("general"),     label: "history(general)")
IO.inspect(Room.history("random"),      label: "history(random)")

# 模拟一个房间崩溃, 让监督树处理
[{pid, _}] = Registry.lookup(ChatRegistry, {:room, "general"})
IO.puts("\n故意让 general 崩溃: #{inspect(pid)}")
Process.exit(pid, :kill)
Process.sleep(50)

# restart: :transient 意味着"异常才重启"; 这里 :kill 视为异常, 监督者会重启
IO.inspect(ChatSupervisor.list_rooms(), label: "重启后房间列表")
IO.inspect(Room.history("general"),     label: "重启后 history(general)  (注意: 状态会丢, 这是 BEAM 的正常行为)")

ChatSupervisor.close("random")
Process.sleep(20)
IO.inspect(ChatSupervisor.list_rooms(), label: "关闭 random 后")

IO.puts("""

=== 重点理解 ===
- Supervisor: 静态监督树, 启动顺序可控, 适合"长期存在的顶层骨架"
- DynamicSupervisor: 动态挂孩子, 适合"按需要创建的工人"(房间/会话/连接)
- Registry: 一等公民的"进程名字表", 支持 unique / duplicate / select / dispatch
- {:via, Registry, {ChatRegistry, key}} 这个 via tuple 是 GenServer 官方支持的别名形式
- restart 策略:
    :permanent -> 崩了就一定重启
    :transient -> 只有异常退出才重启 (正常 :normal 退出就放过)
    :temporary -> 崩了不重启
- 重启后状态默认会丢, 如果要持久化应写到 ETS/mnesia/外部存储, 让进程能"冷启动恢复"
""")
