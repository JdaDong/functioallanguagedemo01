/**
 * Scala 函数式编程 Demo 15: Reader —— 把环境依赖显式化
 *
 * Reader 的核心思想：
 * 把“需要某个环境才能计算”的逻辑表示成 R => A，
 * 然后再通过 map / flatMap 把这些依赖组合起来。
 */
object ReaderConfig extends App {

  case class Reader[-R, +A](run: R => A) {
    def map[B](f: A => B): Reader[R, B] = Reader(r => f(run(r)))
    def flatMap[R1 <: R, B](f: A => Reader[R1, B]): Reader[R1, B] =
      Reader(r => f(run(r)).run(r))
  }

  case class AppConfig(
    serviceName: String,
    host: String,
    port: Int,
    secure: Boolean,
    env: String
  )

  val protocol: Reader[AppConfig, String] =
    Reader(cfg => if (cfg.secure) "https" else "http")

  val baseUrl: Reader[AppConfig, String] = for {
    p <- protocol
    cfg <- Reader[AppConfig, AppConfig](identity)
  } yield s"$p://${cfg.host}:${cfg.port}"

  val healthCheckUrl: Reader[AppConfig, String] =
    baseUrl.map(url => s"$url/health")

  val banner: Reader[AppConfig, String] = for {
    cfg <- Reader[AppConfig, AppConfig](identity)
    url <- baseUrl
  } yield s"[${cfg.env}] ${cfg.serviceName} running at $url"

  def greet(user: String): Reader[AppConfig, String] = for {
    cfg <- Reader[AppConfig, AppConfig](identity)
    url <- baseUrl
  } yield s"Hello $user, welcome to ${cfg.serviceName}! 当前地址: $url"

  val dev = AppConfig("fp-demo", "localhost", 8080, secure = false, env = "dev")
  val prod = AppConfig("fp-demo", "api.example.com", 443, secure = true, env = "prod")

  println("=== Reader: 同一段逻辑，换环境即可复用 ===")
  println(s"dev banner  = ${banner.run(dev)}")
  println(s"prod banner = ${banner.run(prod)}")

  println("\n=== 读取不同依赖并组合 ===")
  println(s"dev health  = ${healthCheckUrl.run(dev)}")
  println(s"prod health = ${healthCheckUrl.run(prod)}")

  println("\n=== 带参数的 Reader ===")
  println(greet("Alice").run(dev))
  println(greet("Bob").run(prod))

  println("\n=== 重点理解 ===")
  println("- Reader 把隐藏的环境依赖变成了显式输入")
  println("- 同一套业务逻辑可以在 dev / prod 等不同配置上复用")
  println("- 这也是很多依赖注入思想在 FP 里的一个简洁模型")
}
