# Elixir 函数式编程 Demo 09: Ecto — Repo + Schema + Changeset + Multi（事务）
#
# Ecto 是 Elixir 的"官配 DB 库", 四大概念:
#   - Repo      : 跟数据库的通道 (相当于连接池 + transact 入口)
#   - Schema    : 字段 ↔ 表的映射 (不强耦合 OOP, 纯数据)
#   - Changeset : 有类型 & 验证的"变更计划", 把"要改什么/谁能改"用值描述
#   - Multi     : 多步骤组合成一个数据库事务, 任一步失败整体回滚
#
# 本 Demo 用内存 SQLite (exqlite) 让单文件可独立运行。
# 运行: elixir 09_ecto_repo_changeset_multi.exs

Mix.install([
  {:ecto_sql, "~> 3.11"},
  {:exqlite,  "~> 0.21"}
])

defmodule Demo.Repo do
  use Ecto.Repo, otp_app: :demo, adapter: Ecto.Adapters.SQLite3
end

defmodule Demo.Account do
  use Ecto.Schema
  import Ecto.Changeset

  schema "accounts" do
    field :owner,   :string
    field :balance, :integer, default: 0
    timestamps(type: :utc_datetime)
  end

  def changeset(acc, attrs) do
    acc
    |> cast(attrs, [:owner, :balance])
    |> validate_required([:owner])
    |> validate_number(:balance, greater_than_or_equal_to: 0)
    |> validate_length(:owner, min: 1, max: 32)
  end
end

defmodule Demo.Migrations do
  def up(repo) do
    Ecto.Adapters.SQL.query!(repo, """
      create table if not exists accounts (
        id integer primary key autoincrement,
        owner text not null,
        balance integer not null default 0,
        inserted_at text not null,
        updated_at text not null
      )
    """)
    :ok
  end
end

defmodule Demo.Transfer do
  alias Demo.{Repo, Account}
  import Ecto.Query
  alias Ecto.Multi

  # 典型"转账"事务: 两个更新 + 一条日志, 任一步失败整体回滚
  def transfer(from_id, to_id, amount) when amount > 0 do
    Multi.new()
    |> Multi.run(:from, fn repo, _ -> fetch_locked(repo, from_id) end)
    |> Multi.run(:to,   fn repo, _ -> fetch_locked(repo, to_id)   end)
    |> Multi.run(:check, fn _repo, %{from: from} ->
      if from.balance >= amount, do: {:ok, :ok}, else: {:error, :insufficient}
    end)
    |> Multi.update(:debit,  fn %{from: f} -> Account.changeset(f, %{balance: f.balance - amount}) end)
    |> Multi.update(:credit, fn %{to: t}   -> Account.changeset(t, %{balance: t.balance + amount}) end)
    |> Repo.transaction()
  end

  defp fetch_locked(repo, id) do
    case repo.get(Account, id) do
      nil -> {:error, :not_found}
      acc -> {:ok, acc}
    end
  end
end

# ==================== 运行 ====================

# SQLite 纯内存: ":memory:" 不跨连接共享, 这里用共享内存文件名
config = [database: "file::memory:?cache=shared", pool_size: 1, journal_mode: :memory]
Application.put_env(:demo, Demo.Repo, config)

{:ok, _} = Demo.Repo.start_link(config)
Demo.Migrations.up(Demo.Repo)

IO.puts("=== Elixir Demo 09: Ecto Repo/Schema/Changeset/Multi ===\n")

# -- Changeset: 合法 & 非法 --
good = Demo.Account.changeset(%Demo.Account{}, %{owner: "alice", balance: 100})
bad  = Demo.Account.changeset(%Demo.Account{}, %{owner: "",      balance: -5})
IO.inspect(good.valid?, label: "good.valid?")
IO.inspect(bad.errors,  label: "bad.errors")

{:ok, alice} = Demo.Repo.insert(good)
{:ok, bob}   = Demo.Repo.insert(Demo.Account.changeset(%Demo.Account{}, %{owner: "bob", balance: 30}))

IO.inspect({alice.id, alice.balance, bob.id, bob.balance}, label: "初始余额")

# -- 正常转账 --
case Demo.Transfer.transfer(alice.id, bob.id, 40) do
  {:ok, _}       -> IO.puts("转账 40: OK")
  {:error, k, e, _} -> IO.puts("转账 40 失败 #{k}: #{inspect(e)}")
end

a1 = Demo.Repo.get!(Demo.Account, alice.id)
b1 = Demo.Repo.get!(Demo.Account, bob.id)
IO.inspect({a1.balance, b1.balance}, label: "转账后")

# -- 失败转账(余额不足) 整体回滚 --
case Demo.Transfer.transfer(alice.id, bob.id, 999) do
  {:ok, _}          -> IO.puts("不该成功")
  {:error, :check, :insufficient, changes} ->
    IO.puts("转账 999: 回滚, 哪些步已跑完: #{inspect(Map.keys(changes))}")
end

a2 = Demo.Repo.get!(Demo.Account, alice.id)
b2 = Demo.Repo.get!(Demo.Account, bob.id)
IO.inspect({a2.balance, b2.balance}, label: "回滚后(应与上次相同)")

IO.puts("""

=== 重点理解 ===
- Changeset = "一次变更的意图 + 验证结果", 值可被传递、可被测试, 不等于"直接改 DB"
- Repo.insert/update 真正落库; changeset 不合法时根本不会发 SQL
- Multi = 用值搭出来的事务流水线; 配合 Repo.transaction 保证"全成/全败"
- Ecto 天生是 FP 的: Schema 是纯数据, Changeset 是纯变换, Repo 是唯一副作用出口
- 对照: Scala Doobie 的 ConnectionIO / Rust diesel 的 transaction / Haskell persistent 的 runSqlT
""")
