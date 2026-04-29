//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 66: cats-effect IOLocal fiber 本地上下文
 *
 * 前面已经补过 ContextRoutes、Dispatcher、streaming client。
 * 这一版继续补一个很适合和“上下文传播”连起来理解的话题：
 *
 * `IOLocal` 可以理解成 fiber-local 的上下文容器。
 * 它不像 `Ref` 那样在 fiber 之间共享同一份可见状态，
 * 而是让每个 fiber 在 fork 时拿到一份上下文副本。
 */
import cats.effect.{IO, IOApp, IOLocal, Resource}

object CatsEffectIOLocal extends IOApp.Simple {

  def withTraceId[A](local: IOLocal[String], traceId: String)(fa: IO[A]): IO[A] =
    Resource
      .make(local.getAndSet(traceId))(previous => local.set(previous))
      .use(_ => fa)

  def log(local: IOLocal[String], label: String): IO[Unit] =
    local.get.flatMap(traceId => IO.println(s"[$label] traceId=$traceId"))

  val run: IO[Unit] =
    for {
      local <- IOLocal("trace-root")
      _ <- IO.println("=== IOLocal：父 fiber 与子 fiber 的上下文隔离 ===")
      _ <- log(local, "main-before")
      _ <- withTraceId(local, "trace-request-9001") {
        for {
          _ <- log(local, "main-in-scope")
          child <- (
            for {
              _ <- log(local, "child-before-update")
              _ <- local.set("trace-child-override")
              _ <- log(local, "child-after-update")
            } yield ()
          ).start
          _ <- local.set("trace-parent-updated")
          _ <- log(local, "main-after-parent-update")
          _ <- child.joinWithNever
          _ <- log(local, "main-after-child-finished")
        } yield ()
      }
      _ <- log(local, "main-after-scope")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- IOLocal 很适合存放 traceId、租户上下文、审计上下文这类 fiber 本地信息")
      _ <- IO.println("- 子 fiber 会继承 fork 时的上下文副本，但后续修改不会反向影响父 fiber")
      _ <- IO.println("- 这类局部上下文适合和 ContextRoutes、日志关联、下游调用透传一起理解")
    } yield ()
}
