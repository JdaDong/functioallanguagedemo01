defmodule FunctionalLanguageDemo.MixProject do
  @moduledoc """
  可选的 Mix 工程入口（Elixir FP Demo 01~15）。

  ## 为什么存在？

  仓库里每个 `*.exs` Demo 都被设计成**独立可跑**的脚本：
    - 01~08 只用 Elixir/OTP 标准库（零依赖，`elixir xx.exs` 秒跑）
    - 09~15 在文件顶部用 `Mix.install/1` 嵌入式拉取 hex 包

  因此**即使没有这份 `mix.exs` 文件，所有 Demo 依然完全可以运行**，
  走 `./run.sh` / `./run.sh all` / `./run.sh 1..15` 全部 OK。

  那这份 `mix.exs` 用来干什么？—— 提供一个**可选**的标准 mix 工作流：

    # 方式 A：保持原样（推荐，不需要 mix.exs）
    ./run.sh              # 零依赖组 01~08
    ./run.sh all          # 全部，09~15 首次会 Mix.install 拉包

    # 方式 B：用 mix 预装所有 09~15 用到的依赖（加速首次运行、离线可跑）
    mix deps.get
    mix run 09_ecto_repo_changeset_multi.exs
    mix test              # 如果之后加 ExUnit 测试

    # 方式 C：用 iex 交互式跑 Demo
    iex -S mix
    iex> c("06_genserver_agent_task.exs")

  ## 依赖

  这里列出的版本与各 Demo 顶部 `Mix.install/1` 保持一致，方便
  一次 `mix deps.get` 就能覆盖 09~15 的所有依赖。
  """
  use Mix.Project

  def project do
    [
      app: :functional_language_demo_elixir,
      version: "0.1.0",
      elixir: "~> 1.15",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      description: "函数式编程多语言学习手册 —— Elixir 侧 Demo 01~15 的可选 Mix 入口",
      package: package(),
      # 让 mix run/test 能直接加载 .exs 脚本
      elixirc_paths: [],
      # 脚本放在本目录根下
      default_task: "help"
    ]
  end

  # 即使 01~08 不需要启动任何 application，这里也给出最小骨架
  # 方便未来要把其中某个 Demo 改造成长驻服务（Phoenix / Plug / GenServer）
  def application do
    [
      extra_applications: [:logger, :crypto, :inets, :ssl]
    ]
  end

  defp deps do
    [
      # ---- Demo 09: Ecto ----
      {:ecto, "~> 3.11"},
      {:ecto_sql, "~> 3.11"},

      # ---- Demo 10: Phoenix 风格 Plug ----
      {:plug, "~> 1.15"},
      {:plug_cowboy, "~> 2.7"},

      # ---- Demo 12: Flow / GenStage / Broadway ----
      {:gen_stage, "~> 1.2"},
      {:flow, "~> 1.2"},
      {:broadway, "~> 1.1"},

      # ---- Demo 13: Telemetry + OpenTelemetry ----
      {:telemetry, "~> 1.2"},
      {:telemetry_metrics, "~> 1.0"},
      {:opentelemetry_api, "~> 1.3"},

      # ---- Demo 14: ExUnit 辅助 ----
      {:mox, "~> 1.1", only: [:dev, :test]},
      {:stream_data, "~> 1.0", only: [:dev, :test]}
    ]
  end

  defp package do
    [
      licenses: ["MIT"],
      links: %{"GitHub" => "https://github.com/"},
      maintainers: ["jiangdadong"],
      files: ~w(mix.exs README.md *.exs run.sh ELIXIR_FP_ROADMAP.md)
    ]
  end
end
