# Elixir 函数式编程 Demo 10: Phoenix 骨架 — 路由 / Controller / Plug
#
# Phoenix 是 Elixir 最具代表性的 Web 框架, 其核心不在"有很多功能",
# 而是把 HTTP 请求建模成"Plug.Conn 的纯数据流水线":
#
#   incoming Conn -->  router plug --> pipeline plugs --> controller action
#                                                              |
#                                       更新后的 Conn  <-------+
#
# 本 Demo **不启完整 Phoenix**, 只用 Phoenix 依赖的核心: plug_cowboy,
# 用 100 行左右把 Phoenix 的"路由 + 插件 + 控制器"心智模型讲透。
# 真实 Phoenix 再往上加: view / live_view / channel / ecto integration.
#
# 运行: elixir 10_phoenix_router_plug.exs     然后访问 http://127.0.0.1:4001/hello/elixir

Mix.install([
  {:plug_cowboy, "~> 2.7"},
  {:jason,       "~> 1.4"}
])

# ---------- 1) 自定义 Plug: 给每个请求打个 request_id ----------
defmodule RequestIdPlug do
  import Plug.Conn
  def init(_), do: []
  def call(conn, _opts) do
    rid = :crypto.strong_rand_bytes(6) |> Base.url_encode64(padding: false)
    conn
    |> put_resp_header("x-request-id", rid)
    |> assign(:request_id, rid)
  end
end

# ---------- 2) 简易"Controller": 就是"Conn -> Conn"的函数 ----------
defmodule HelloController do
  import Plug.Conn

  def show(conn, %{"name" => name}) do
    body = Jason.encode!(%{
      hello:      name,
      request_id: conn.assigns[:request_id],
      method:     conn.method
    })
    conn
    |> put_resp_content_type("application/json")
    |> send_resp(200, body)
  end
end

defmodule EchoController do
  import Plug.Conn
  def run(conn, _params) do
    {:ok, body, conn} = read_body(conn)
    send_resp(conn, 200, "echo: #{body}")
  end
end

# ---------- 3) Router: 仿 Phoenix 的 pipeline / scope 语法 ----------
defmodule MyRouter do
  use Plug.Router

  plug :match                     # 选路由
  plug RequestIdPlug              # Phoenix 里这叫 pipeline
  plug Plug.Parsers, parsers: [:urlencoded, :json], json_decoder: Jason
  plug :dispatch                  # 调 Controller

  get "/hello/:name" do
    HelloController.show(conn, %{"name" => name})
  end

  post "/echo" do
    EchoController.run(conn, %{})
  end

  get "/ping" do
    send_resp(conn, 200, "pong")
  end

  match _ do
    send_resp(conn, 404, "not found")
  end
end

# ---------- 4) 启动 + 小客户端自验证 ----------
port = 4001
{:ok, _} = Plug.Cowboy.http(MyRouter, [], port: port)
IO.puts("=== Elixir Demo 10: Phoenix 风格 Plug 路由 ===")
IO.puts("  已监听: http://127.0.0.1:#{port}\n")

# 用 :httpc 简单回调打几发请求, 顺手展示"请求也是 Plug.Conn 流水线"
:inets.start()
{:ok, {{_, 200, _}, headers, body}} =
  :httpc.request(:get, {~c"http://127.0.0.1:#{port}/hello/elixir", []}, [], [])

IO.inspect(List.to_string(body), label: "GET /hello/elixir body")
IO.inspect(for {k, v} <- headers, to_string(k) == "x-request-id", do: List.to_string(v),
  label: "request-id header")

{:ok, {{_, 200, _}, _, pong}} =
  :httpc.request(:get, {~c"http://127.0.0.1:#{port}/ping", []}, [], [])
IO.inspect(List.to_string(pong), label: "GET /ping")

{:ok, {{_, 200, _}, _, echo}} =
  :httpc.request(:post,
    {~c"http://127.0.0.1:#{port}/echo", [], ~c"text/plain", ~c"hello from client"},
    [], [])
IO.inspect(List.to_string(echo), label: "POST /echo")

IO.puts("""

=== 重点理解 ===
- Plug 规范: 任何 (conn, opts) -> conn 的模块/函数都是一个 Plug
- Phoenix 的"pipeline"本质就是顺序 plug 链, match -> 中间件 -> dispatch
- Controller 并不是 OOP 控制器, 而是"按约定签名 action(conn, params) -> conn"的函数
- Router DSL (get/post/match) 最终会被宏展开成 Plug.Router 的分发代码 (对照 Demo 05 的路由 DSL)
- 真实 Phoenix 在此之上加: view (模板) / channel (WebSocket) / live_view (服务端驱动 UI) / ecto 集成
""")

Process.sleep(200) # 让日志和响应都落盘

# 如果想长期运行可以改成 Process.sleep(:infinity)
