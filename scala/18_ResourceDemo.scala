/**
 * Scala 函数式编程 Demo 18: Resource —— 正确管理资源生命周期
 *
 * 打开文件、数据库连接、网络连接之后，都必须在用完后释放。
 * Resource 的核心价值是：把“申请 + 使用 + 释放”打包成一个整体，
 * 避免忘记 close，也避免异常时泄漏资源。
 */
object ResourceDemo extends App {

  final case class Resource[A](allocate: () => (A, () => Unit)) {
    def map[B](f: A => B): Resource[B] =
      Resource(() => {
        val (resource, release) = allocate()
        (f(resource), release)
      })

    def flatMap[B](f: A => Resource[B]): Resource[B] =
      Resource(() => {
        val (first, releaseFirst) = allocate()
        try {
          val (second, releaseSecond) = f(first).allocate()
          (second, () => {
            releaseSecond()
            releaseFirst()
          })
        } catch {
          case e: Throwable =>
            releaseFirst()
            throw e
        }
      })

    def use[B](f: A => B): Either[Throwable, B] = {
      val (resource, release) = allocate()
      try {
        Right(f(resource))
      } catch {
        case e: Throwable => Left(e)
      } finally {
        release()
      }
    }
  }

  object Resource {
    def make[A](acquire: => A)(release: A => Unit): Resource[A] =
      Resource(() => {
        val resource = acquire
        (resource, () => release(resource))
      })
  }

  case class Config(serviceUrl: String, token: String)
  case class Connection(url: String, token: String) {
    def get(path: String): String = s"GET $url$path (token=$token)"
  }

  val configResource: Resource[Config] = Resource.make {
    println("[acquire] 打开配置文件")
    Config("https://api.demo.local", "token-123")
  } { _ =>
    println("[release] 关闭配置文件")
  }

  def connectionResource(config: Config): Resource[Connection] = Resource.make {
    println(s"[acquire] 建立到 ${config.serviceUrl} 的连接")
    Connection(config.serviceUrl, config.token)
  } { conn =>
    println(s"[release] 关闭连接: ${conn.url}")
  }

  val apiResource: Resource[Connection] = for {
    config <- configResource
    conn <- connectionResource(config)
  } yield conn

  println("=== 正常使用资源 ===")
  val success = apiResource.use { conn =>
    val userResp = conn.get("/users/42")
    val orderResp = conn.get("/orders/42")
    println(userResp)
    println(orderResp)
    "请求完成"
  }
  println(s"结果: $success")

  println("\n=== 即使业务失败，也会释放资源 ===")
  val failure = apiResource.use { conn =>
    println(conn.get("/reports/today"))
    throw new RuntimeException("下游服务返回 500")
  }
  println(s"结果: $failure")

  println("\n=== 重点理解 ===")
  println("- Resource 把 acquire / use / release 绑定在一起")
  println("- 不管成功还是抛异常，release 都会被执行")
  println("- cats-effect 的 Resource 会在这个基础上进一步支持异步、取消和更强的组合能力")
}
