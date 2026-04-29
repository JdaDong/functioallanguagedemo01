//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.5.7"

/**
 * Scala 函数式编程 Demo 27: 真正的 cats-effect Resource
 *
 * 18 号 Demo 里我们手写过一个最小 Resource，
 * 现在把它替换成真实库版本，看看生产级写法是什么样子。
 */
import cats.effect.{IO, IOApp, Ref, Resource}

object CatsEffectResource extends IOApp.Simple {

  final case class FakeConnection(name: String, history: Ref[IO, Vector[String]]) {
    def query(sql: String): IO[List[String]] =
      for {
        _ <- IO.println(s"[$name] 执行 SQL: $sql")
        _ <- history.update(_ :+ sql)
      } yield List(s"result-from-$name")
  }

  def openConnection(name: String): Resource[IO, FakeConnection] =
    Resource.make {
      for {
        _ <- IO.println(s"申请连接: $name")
        history <- Ref.of[IO, Vector[String]](Vector.empty)
      } yield FakeConnection(name, history)
    } { conn =>
      for {
        history <- conn.history.get
        _ <- IO.println(s"释放连接: ${conn.name}")
        _ <- IO.println(s"SQL 历史: ${if (history.isEmpty) "无" else history.mkString(" | ")}")
      } yield ()
    }

  val successProgram: IO[Unit] =
    openConnection("orders-db").use { conn =>
      for {
        _ <- conn.query("select * from orders where status = 'paid'")
        _ <- conn.query("select * from order_items where order_id in (1, 2, 3)")
        _ <- IO.println("成功路径执行完毕")
      } yield ()
    }

  val failureProgram: IO[Either[Throwable, Unit]] =
    openConnection("billing-db").use { conn =>
      for {
        _ <- conn.query("select * from invoices limit 10")
        _ <- IO.raiseError[Unit](new RuntimeException("模拟下游超时"))
      } yield ()
    }.attempt

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== Resource 成功路径 ===")
      _ <- successProgram

      _ <- IO.println("\n=== Resource 失败路径 ===")
      failed <- failureProgram
      _ <- IO.println(s"失败路径结果: $failed")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Resource.make 把 acquire / release 强绑定在一起")
      _ <- IO.println("- use 里的业务无论成功还是失败，release 都会执行")
      _ <- IO.println("- 真正的连接池、HTTP 客户端、文件句柄通常都这样组织")
    } yield ()
}
