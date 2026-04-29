# Elixir 函数式编程 Demo 05: 宏进阶 — 写一个小型 DSL
#
# Phoenix / Ecto / Plug 的"好用"其实就是宏 DSL：
#   - Phoenix 的 `get "/users/:id", UserCtrl, :show`
#   - Ecto   的 `from u in User, where: u.age > ^age`
#   - Plug   的 `plug :require_auth`
# 本 Demo 自己实现一个"路由 DSL"：
#   use Router
#   route :get,  "/hello/:name", :say_hi
#   route :post, "/echo",        :echo
#
# 它在"编译期"把若干 route 收集起来, 自动生成 dispatch/2, 实现一个零运行时开销的小路由器。

defmodule Router do
  # 1) use Router 会调用 __using__, 相当于"把一堆东西注入到调用方模块"
  defmacro __using__(_opts) do
    quote do
      import Router, only: [route: 3]
      # @before_compile 钩子: 所有 route 宏跑完之后, 再统一生成 dispatch/2
      @before_compile Router
      # 用模块属性做"临时收集盒"
      Module.register_attribute(__MODULE__, :routes, accumulate: true)
    end
  end

  # 2) route DSL: 只负责"把这条路由加进收集盒"
  defmacro route(method, path, handler) do
    quote do
      @routes {unquote(method), unquote(path), unquote(handler)}
    end
  end

  # 3) 编译收尾时, 把所有 @routes 展开成若干函数子句
  defmacro __before_compile__(env) do
    routes = Module.get_attribute(env.module, :routes) |> Enum.reverse()

    clauses =
      for {method, path, handler} <- routes do
        quote do
          def dispatch(unquote(method), unquote(path) = full_path) do
            {:ok, unquote(handler), params_from(unquote(path), full_path)}
          end
        end
      end

    quote do
      unquote_splicing(clauses)
      def dispatch(_method, _path), do: {:error, :not_found}

      # 极简的 ":name" 占位符参数解析
      defp params_from(pattern, full_path) do
        patt = String.split(pattern, "/", trim: true)
        real = String.split(full_path, "/", trim: true)
        Enum.zip(patt, real)
        |> Enum.flat_map(fn
          {":" <> k, v} -> [{k, v}]
          _             -> []
        end)
        |> Map.new()
      end
    end
  end
end

defmodule MyRouter do
  use Router

  route :get,  "/hello/:name", :say_hi
  route :get,  "/users/:id",   :show_user
  route :post, "/echo",        :echo
end

defmodule Handlers do
  def say_hi(%{"name" => name}),   do: "Hello, #{name}!"
  def show_user(%{"id" => id}),    do: "User##{id}"
  def echo(_),                     do: "echoed"
end

defmodule App do
  def call(method, path) do
    case MyRouter.dispatch(method, path) do
      {:ok, handler, params} -> apply(Handlers, handler, [params])
      {:error, :not_found}   -> "404 Not Found"
    end
  end
end

IO.puts("=== Elixir Demo 05: 宏进阶 — 路由 DSL ===\n")

for {m, p} <- [
      {:get,  "/hello/:name"},   # 直接用模式, 不带参数
      {:get,  "/users/:id"},
      {:post, "/echo"}
    ] do
  IO.puts("  route table: #{m} #{p}")
end

IO.puts("\n-- 实际请求 --")
for {m, p} <- [
      {:get,  "/hello/:name"},   # 按模式本身匹配
      {:get,  "/users/:id"},
      {:post, "/echo"},
      {:delete, "/anything"}
    ] do
  IO.puts("  #{m} #{p} -> #{App.call(m, p)}")
end

IO.puts("""

=== 重点理解 ===
- use Router 是 Elixir DSL 的入口惯用语: 宏把一堆 import/attribute/hook 一次性注入
- @before_compile 让你在"用户模块编译收尾前"最后一次拼 AST, 这是 Phoenix/Ecto 的核心手法
- Module.register_attribute(..., accumulate: true) 就是"编译期收集盒"
- unquote_splicing 把一个"AST 列表"展开进模板, 用来批量生成函数子句
- 本 Demo 的路由在编译期就已展开为多条 dispatch/2 子句, 运行时零查表开销
- 真实项目 DSL 的写法心法: 用户代码 -> 宏收集 -> __before_compile__ 统一产代码
""")
