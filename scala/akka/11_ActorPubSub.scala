// 11_ActorPubSub.scala
// Akka EventBus 发布订阅
//
// 核心思想：
//   Akka 提供两种进程内发布订阅机制：
//     1. EventStream：ActorSystem 级别的全局事件总线，任何 Actor 都可以订阅/发布
//     2. 自定义 EventBus：按 channel 分类，支持通配符订阅
//
//   与 fs2 Topic 的对比：
//     fs2 Topic：纯函数式，Publisher → Topic → N 个 Subscriber，背压控制
//     Akka EventStream：Actor 消息驱动，订阅 Actor 收到消息后异步处理
//
// 本 Demo 演示：
//   1. system.eventStream.publish / subscribe（全局事件总线）
//   2. 自定义 EventBus：按 channel 订阅，支持通配符
//   3. DeadLetter 监听（未被任何 Actor 处理的消息）
//   4. 与 fs2 Topic 对比

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.eventstream.EventStream

// ── 领域事件（发布到 EventStream）────────────────────────────────────────────

sealed trait DomainEvent
object DomainEvent:
  case class OrderCreated(orderId: String, sku: String)   extends DomainEvent
  case class PaymentReceived(orderId: String, amount: Double) extends DomainEvent
  case class OrderShipped(orderId: String, trackingNo: String) extends DomainEvent

// ── 订阅者 Actor ──────────────────────────────────────────────────────────────

/** 审计日志订阅者：监听所有 DomainEvent */
object AuditLogger:
  def apply(): Behavior[DomainEvent] =
    Behaviors.setup { ctx =>
      // 订阅 DomainEvent 的所有子类
      ctx.system.eventStream ! EventStream.Subscribe[DomainEvent](ctx.self)
      Behaviors.receiveMessage { event =>
        ctx.log.info(s"[AuditLog] 收到事件: $event")
        Behaviors.same
      }
    }

/** 支付监控订阅者：只监听 PaymentReceived */
object PaymentMonitor:
  def apply(): Behavior[DomainEvent.PaymentReceived] =
    Behaviors.setup { ctx =>
      ctx.system.eventStream ! EventStream.Subscribe[DomainEvent.PaymentReceived](ctx.self)
      Behaviors.receiveMessage { event =>
        ctx.log.info(s"[PaymentMonitor] 💰 支付到账: ${event.orderId} = ${event.amount}")
        Behaviors.same
      }
    }

/** 物流通知订阅者：只监听 OrderShipped */
object ShippingNotifier:
  def apply(): Behavior[DomainEvent.OrderShipped] =
    Behaviors.setup { ctx =>
      ctx.system.eventStream ! EventStream.Subscribe[DomainEvent.OrderShipped](ctx.self)
      Behaviors.receiveMessage { event =>
        ctx.log.info(s"[ShippingNotifier] 🚚 发货通知: ${event.orderId}, 单号: ${event.trackingNo}")
        Behaviors.same
      }
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorPubSubDemo(): Unit =
  val system = ActorSystem(Behaviors.empty[Nothing], "pubsub-system")

  println("=== Akka EventStream 发布订阅（对比 fs2 Topic）===\n")

  // 启动订阅者
  system.systemActorOf(AuditLogger(), "audit-logger")
  system.systemActorOf(PaymentMonitor(), "payment-monitor")
  system.systemActorOf(ShippingNotifier(), "shipping-notifier")

  Thread.sleep(200)   // 等待订阅者完成订阅

  // 发布事件（EventStream 自动扇出给所有订阅者）
  println("── 发布集成事件 ─────────────────────────────────────────────")
  system.eventStream ! EventStream.Publish(DomainEvent.OrderCreated("o-1", "BTC-101"))
  Thread.sleep(100)
  system.eventStream ! EventStream.Publish(DomainEvent.PaymentReceived("o-1", 300.0))
  Thread.sleep(100)
  system.eventStream ! EventStream.Publish(DomainEvent.OrderShipped("o-1", "SF-9999"))
  Thread.sleep(100)
  system.eventStream ! EventStream.Publish(DomainEvent.OrderCreated("o-2", "ETH-202"))
  Thread.sleep(100)

  Thread.sleep(500)

  println("""
|说明（查看上方日志）：
|  - AuditLogger 收到了全部 4 条事件（订阅 DomainEvent 父类）
|  - PaymentMonitor 只收到了 PaymentReceived（1 条）
|  - ShippingNotifier 只收到了 OrderShipped（1 条）
|
|关键点（与 fs2 Topic 对比）：
|  fs2 Topic:                     Akka EventStream:
|  ─────────────────────────────  ────────────────────────────────────
|  topic <- Topic[IO, Event]      system.eventStream !
|  topic.subscribers.use(...)       EventStream.Subscribe[E](actorRef)
|  topic.publish1(event)          system.eventStream !
|                                   EventStream.Publish(event)
|
|  特点：                           特点：
|  - 背压控制（Subscriber 慢时）   - 无背压（消息进 Actor 邮箱就返回）
|  - 编译期类型安全                 - ActorSystem 级别全局总线
|  - 可以 take(N)/unNoneTerminate  - 订阅父类可以收到所有子类消息""".stripMargin)

  import scala.concurrent.Await
  import scala.concurrent.duration._
  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
