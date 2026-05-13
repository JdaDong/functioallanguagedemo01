# Erlang Demo 目录

本目录共 **27 个 demo**，覆盖 Erlang/OTP 从语法基础到生产级运维的完整能力面，全部已完成 ✅。

## 📚 文档导航

| 文档 | 用途 |
|---|---|
| [ROADMAP.md](./ROADMAP.md) | 27 个 demo 的分阶段清单（11 个阶段） |
| [ERLANG_ECOSYSTEM.md](./ERLANG_ECOSYSTEM.md) | 25 个工业级开源项目盘点（"学完玩什么"） |
| [run.sh](./run.sh) | 一键运行入口 |

## 🚀 快速开始

```bash
# 进入本目录
cd erlang

# 查看帮助
./run.sh

# 运行单个 demo（编号 1-27）
./run.sh 1

# 运行所有"无副作用"的纯计算 demo
./run.sh safe
```

**运行环境**：需要 Erlang/OTP 26+，部分 demo 依赖额外组件：

| Demo | 额外依赖 |
|---|---|
| 08 (PropEr) | `proper` 库 |
| 11 (Mnesia) | OTP 自带，需启动节点 |
| 17 (Common Test) | `ct_run` 工具 |
| 19 (recon) | `recon` 库 |
| 22 (SSL/TLS) | OTP 自带 `ssl` 应用 |
| 24 (rebar3) | `rebar3` 工具链 |

不带这些依赖也能跑大部分纯函数式 demo（01-07、10、14-16、25、27 等）。

## 🗺 阶段总览

| 阶段 | Demo | 主题 |
|---|---|---|
| 一 | 01-02 | 函数式基础：模式匹配、高阶函数 |
| 二 | 03 | Actor 模型 |
| 三 | 04-05 | OTP 行为与监督树 |
| 四 | 06-07 | 状态存储与分布式 |
| 五 | 08-10 | 测试与协议层（PropEr / gen_statem / 二进制） |
| 六 | 11-12 | 持久化与热升级（Mnesia / hot upgrade） |
| 七 | 13-15 | 网络与生命周期（gen_tcp / receive / link/monitor） |
| 八 | 16-19 | 可观测性与发布（logger / CT / application / recon） |
| 九 | 20-22 | 系统集成（NIF / DETS / SSL） |
| 十 | 23-25 | 性能与工程化（bench / rebar3 / Elixir 互通） |
| 十一 | 26-27 | 事件总线与 Trace（gen_event / dbg） |

详细清单见 [ROADMAP.md](./ROADMAP.md)。

## ⚠️ 踩坑记录（来自实战）

写 / 改 demo 时容易踩的坑：

1. **原子的字符串字面量必须加单引号**——模块名以数字开头时，`-module('01_pattern_matching').` 必须用单引号包裹原子，调用时也要 `'01_pattern_matching':main()`。
2. **`receive` 默认会阻塞**——测试时若忘了 `after` 子句，REPL 会挂住，需用 `Ctrl+G` 切节点强杀。
3. **`spawn` 出来的进程崩溃默认不会通知父进程**——必须用 `link/1` 或 `monitor/2` 才能感知子进程退出。
4. **热升级（demo 12）需要保证 `code:soft_purge/1` 不返回 false**——旧版本若仍有进程在跑老代码，升级会失败；要么 `code:purge/1` 强行干掉，要么先发消息让所有进程退出旧 callback。

详细的 BEAM 与 Elixir 互通对照见 [demo 25](./25_elixir_vs_erlang.erl)。

## 🔗 相关链接

- 本仓库根 [README](../README.md)：7 种语言总览
- 同 BEAM 家族姊妹目录：[`../elixir/`](../elixir/)
- ML 家族对照参考：[`../haskell/`](../haskell/) / [`../ocaml/`](../ocaml/)
