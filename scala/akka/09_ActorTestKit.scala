// 09_ActorTestKit.scala
// Akka Typed TestKit（Actor 测试）
//
// 核心思想：
//   Actor 的测试与函数式代码测试有本质区别：
//     - 函数式（cats-effect）：IO 是值，直接 flatMap 然后断言
//     - Actor：消息异步处理，需要特殊的测试工具
//
//   Akka Typed TestKit 提供：
//     - BehaviorTestKit：同步测试，直接处理消息不需要 ActorSystem（轻量）
//     - ActorTestKit：异步测试，真实 ActorSystem + 探针（probe）
//     - TestProbe：替代真实 Actor 的测试探针，可以断言收到的消息
//
// 本 Demo 演示：
//   1. BehaviorTestKit：同步测试 Actor 行为（无 ActorSystem）
//   2. TestProbe：用探针代替真实 Actor，断言消息内容
//   3. 与 munit-cats-effect 测试风格对比
//
// 注意：本 Demo 直接运行（@main），不依赖测试框架
// 实际项目中 ActorTestKit 通常集成进 ScalaTest / munit

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-actor-testkit-typed:2.8.5"

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, TestInbox}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

// ── 被测试的 Actor ────────────────────────────────────────────────────────────

sealed trait GreeterCmd
object GreeterCmd:
  case class Greet(name: String, replyTo: ActorRef[String]) extends GreeterCmd
  case class SetGreeting(prefix: String)                     extends GreeterCmd

object GreeterActor:
  def apply(greeting: String = "你好"): Behavior[GreeterCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case GreeterCmd.Greet(name, replyTo) =>
          replyTo ! s"$greeting, $name!"
          Behaviors.same

        case GreeterCmd.SetGreeting(prefix) =>
          ctx.log.info(s"更换问候语: $greeting → $prefix")
          GreeterActor(prefix)
    }

// ── Counter Actor（用于状态测试）─────────────────────────────────────────────

sealed trait CountCmd
object CountCmd:
  case object Inc extends CountCmd
  case object Dec extends CountCmd
  case class Get(replyTo: ActorRef[Int]) extends CountCmd

object CounterActor2:
  def apply(n: Int = 0): Behavior[CountCmd] =
    Behaviors.receiveMessage {
      case CountCmd.Inc      => CounterActor2(n + 1)
      case CountCmd.Dec      => CounterActor2(n - 1)
      case CountCmd.Get(ref) => ref ! n; Behaviors.same
    }

// ── 演示（直接运行，模拟测试流程）────────────────────────────────────────────

@main def actorTestKitDemo(): Unit =
  println("=== Akka TestKit：Actor 测试（对比 munit-cats-effect）===\n")

  // ── 1. BehaviorTestKit：同步测试（最轻量，无 ActorSystem）──────────────────
  println("── 1. BehaviorTestKit 同步测试 ──────────────────────────────────")

  val testKit  = BehaviorTestKit(GreeterActor("嗨"))
  val inbox    = TestInbox[String]()

  testKit.run(GreeterCmd.Greet("Alice", inbox.ref))
  val reply1 = inbox.receiveMessage()
  assert(reply1 == "嗨, Alice!", s"期望 '嗨, Alice!' 但收到 '$reply1'")
  println(s"  [✓] Greet Alice: $reply1")

  testKit.run(GreeterCmd.SetGreeting("你好"))
  testKit.run(GreeterCmd.Greet("Bob", inbox.ref))
  val reply2 = inbox.receiveMessage()
  assert(reply2 == "你好, Bob!", s"期望 '你好, Bob!' 但收到 '$reply2'")
  println(s"  [✓] Greet Bob after SetGreeting: $reply2")

  // ── 2. ActorTestKit：异步测试（真实 ActorSystem + TestProbe）──────────────
  println("\n── 2. ActorTestKit 异步测试 ──────────────────────────────────────")

  val asyncKit = ActorTestKit()

  try
    val counter = asyncKit.spawn(CounterActor2(0), "counter")
    val probe   = asyncKit.createTestProbe[Int]()

    counter ! CountCmd.Inc
    counter ! CountCmd.Inc
    counter ! CountCmd.Inc
    counter ! CountCmd.Dec
    counter ! CountCmd.Get(probe.ref)

    val count = probe.receiveMessage()
    assert(count == 2, s"期望 2 但收到 $count")
    println(s"  [✓] Inc×3, Dec×1 后计数: $count（期望 2）")

    // TestProbe 可以断言消息内容和超时
    val greeter = asyncKit.spawn(GreeterActor("Hello"), "greeter")
    val strProbe = asyncKit.createTestProbe[String]()

    greeter ! GreeterCmd.Greet("World", strProbe.ref)
    strProbe.expectMessage("Hello, World!")
    println(s"  [✓] expectMessage 断言通过：'Hello, World!'")

    // 验证没有多余消息
    strProbe.expectNoMessage()
    println(s"  [✓] expectNoMessage 通过（无多余消息）")

  finally
    asyncKit.shutdownTestKit()

  println("""
|关键点（与 munit-cats-effect 对比）：
|
|  munit-cats-effect:              Akka TestKit:
|  ──────────────────              ─────────────────────────────
|  test("加法") {                  val kit  = ActorTestKit()
|    for                           val probe = kit.createTestProbe[Int]()
|      result <- IO(1 + 1)         actor ! CountCmd.Get(probe.ref)
|      _ = assertEquals(result,2)  probe.expectMessage(2)
|    yield ()                      kit.shutdownTestKit()
|  }
|
|  特点：                           特点：
|  - IO 是值，直接断言结果          - 消息异步，用 probe 接收断言
|  - 测试函数返回 IO                - BehaviorTestKit 可同步测试
|  - 背后是 cats-effect runtime     - expectMessage 带超时等待""".stripMargin)
