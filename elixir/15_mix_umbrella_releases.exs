# Elixir 函数式编程 Demo 15: mix 工程骨架 + umbrella + releases
#
# 这个 Demo 不像前 14 个那样"跑出结果", 而是
# 用**注释 + 可运行的演示段**把 Elixir 真实工程骨架讲一遍:
#   1. 单项目: mix new my_app --sup
#   2. 多子项目: mix new my_umbrella --umbrella, 里面再 mix new app1 / app2
#   3. 发布: mix release, 打出自包含的 BEAM 可执行程序
#   4. 配置分层: config/config.exs / runtime.exs / releases.exs
#   5. 依赖: mix.exs 的 deps 和 mix.lock 的语义
#
# 最后用 Mix.install 快速演示"在一个 exs 里就能拼出完整 OTP Application 的骨架"。

IO.puts("=== Elixir Demo 15: mix 工程骨架 / umbrella / releases ===\n")

IO.puts("""
------------------------------------------------------------------
1) 单项目骨架:  `mix new my_app --sup`
------------------------------------------------------------------
my_app/
├── mix.exs              # 项目定义: 名字/版本/依赖/application 入口
├── mix.lock             # 依赖锁定文件, 必须入库
├── config/
│   ├── config.exs       # 编译期配置 (所有环境)
│   ├── dev.exs / test.exs / prod.exs
│   └── runtime.exs      # 运行时配置 (release 启动时读环境变量最佳入口)
├── lib/
│   ├── my_app.ex        # 业务模块
│   └── my_app/
│       └── application.ex  # OTP Application callback, 启监督树
└── test/
    └── my_app_test.exs  # ExUnit 测试

mix 关键命令:
  mix deps.get               拉依赖
  mix compile                编译
  mix test                   跑测试
  mix format                 官方 formatter (对标 gofmt / rustfmt)
  mix credo / mix dialyxir   Linter + 类型检查 (dialyzer)
  iex -S mix                 带项目上下文的 REPL
""")

IO.puts("""
------------------------------------------------------------------
2) Umbrella 项目:  `mix new my_umbrella --umbrella`
------------------------------------------------------------------
my_umbrella/
├── mix.exs
├── config/
└── apps/
    ├── core/         # 业务模型 (纯数据 + 纯函数, 不依赖 Web/DB)
    │   └── mix.exs
    ├── store/        # Ecto 仓储 (依赖 core)
    │   └── mix.exs
    ├── web/          # Phoenix 接口 (依赖 core + store)
    │   └── mix.exs
    └── worker/       # Broadway 后台 (依赖 core + store)
        └── mix.exs

心法:
- Umbrella 不是"模块切分", 而是"独立可部署的子应用 + 明确的依赖方向"
- 子应用间只能走 in_umbrella: true 的依赖, 编译器会在循环依赖时报错
- 可以把某个 app 单独 mix release 出来 (比如只发 worker, 不带 web)
- 不喜欢 umbrella 的用 "mix new" + path deps 也能做同样的拆分
""")

IO.puts("""
------------------------------------------------------------------
3) Release:  `mix release`
------------------------------------------------------------------
- 产物: _build/prod/rel/my_app/ 下一个自包含的目录:
    * 自带 ERTS (BEAM 虚拟机), 部署机不用装 Erlang/Elixir
    * bin/my_app start | daemon | remote | rpc | stop
    * runtime.exs 里读 System.get_env("DATABASE_URL") 完成运行时配置
- 冷启动: bin/my_app daemon; 运维: bin/my_app remote 进正在跑的节点 iex
- 热升级 (hot code upgrade) 也走 release, 但实践中多用滚动重启 (更简单)
- Docker 友好: 多阶段构建, 最终镜像里只带 _build/prod/rel 目录

对照 Erlang: mix release 相当于 rebar3 release + 更好用的默认值 (runtime.exs / env-aware)
""")

IO.puts("""
------------------------------------------------------------------
4) mix.exs 的典型写法
------------------------------------------------------------------
defmodule MyApp.MixProject do
  use Mix.Project

  def project do
    [
      app: :my_app,
      version: "0.1.0",
      elixir: "~> 1.17",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      releases: releases()
    ]
  end

  # OTP Application 定义 -> application.ex 的入口
  def application do
    [
      extra_applications: [:logger],
      mod: {MyApp.Application, []}   # <- 这是监督树入口
    ]
  end

  defp deps do
    [
      {:ecto_sql,    "~> 3.11"},
      {:phoenix,     "~> 1.7"},
      {:broadway,    "~> 1.1"},
      {:telemetry,   "~> 1.2"},
      {:mox,         "~> 1.2", only: :test},
      {:ex_doc,      "~> 0.34", only: :dev, runtime: false}
    ]
  end

  defp releases do
    [
      my_app: [
        include_executables_for: [:unix],
        applications: [runtime_tools: :permanent]
      ]
    ]
  end
end
""")

# --- 真实跑一次"最小 OTP 应用骨架", 帮你建立"mix new --sup"产物在运行时的心智模型 ---

defmodule Demo15.Worker do
  use GenServer
  def start_link(_), do: GenServer.start_link(__MODULE__, 0, name: __MODULE__)
  def bump, do: GenServer.call(__MODULE__, :bump)
  def count, do: GenServer.call(__MODULE__, :count)

  @impl true
  def init(n), do: {:ok, n}
  @impl true
  def handle_call(:bump, _from, n), do: {:reply, n + 1, n + 1}
  def handle_call(:count, _from, n), do: {:reply, n, n}
end

defmodule Demo15.Application do
  # 等价于 mix new --sup 产生的 application.ex
  def start(_type, _args) do
    children = [Demo15.Worker]
    Supervisor.start_link(children, strategy: :one_for_one, name: Demo15.Sup)
  end
end

{:ok, _} = Demo15.Application.start(:normal, [])
for _ <- 1..3, do: Demo15.Worker.bump()
IO.puts("\n-- 模拟运行 --")
IO.inspect(Demo15.Worker.count(), label: "Worker count (等价于真实 mix 项目里的 GenServer)")

IO.puts("""

=== 重点理解 ===
- mix 是 Elixir 唯一的工程入口, 类比 Rust 的 cargo / Scala 的 sbt / Haskell 的 cabal
- application.ex 的 start/2 返回一个 Supervisor.start_link, 这个就是你整个系统的"根"
- umbrella 让你在一个仓里拆多个独立部署单元, 不等于微服务, 但为之后拆微服务留了线
- release 让部署不再依赖机器上装了什么, 一包打天下, 是生产的默认姿势
- runtime.exs 是"环境变量驱动的运行时配置", 不要再用 config.exs 存生产密码
- 对照:
    mix new my_app --sup     ≈ cargo new my_app + #[derive(tokio::main)]
    mix release              ≈ cargo build --release + 自带运行时 (像 GraalVM native-image)
    mix.exs                  ≈ Cargo.toml
    mix.lock                 ≈ Cargo.lock
""")
