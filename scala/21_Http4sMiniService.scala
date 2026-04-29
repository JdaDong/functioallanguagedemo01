/**
 * Scala 函数式编程 Demo 21: 最小 http4s 风格服务
 *
 * 这里不直接引入真正的 http4s 依赖，
 * 而是先用标准库手写一个极简版本，建立这些直觉：
 *
 * 1. 请求和响应都只是普通数据
 * 2. 路由本质上是 Request => Option[Response]
 * 3. 中间件本质上是 HttpApp => HttpApp 的函数组合
 *
 * 等你真正上 http4s 时，会发现很多概念已经很熟了。
 */
object Http4sMiniService extends App {

  case class Request(
    method: String,
    path: String,
    query: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty,
    body: String = ""
  )

  case class Response(
    status: Int,
    body: String,
    headers: Map[String, String] = Map("Content-Type" -> "application/json; charset=utf-8")
  )

  type HttpRoute = Request => Option[Response]
  type HttpApp = Request => Response
  type Middleware = HttpApp => HttpApp

  object Response {
    def json(status: Int, body: String): Response = Response(status, body)
    def ok(body: String): Response = json(200, body)
    def badRequest(body: String): Response = json(400, body)
    def notFound: Response = json(404, """{"error":"route not found"}""")
  }

  def route(pf: PartialFunction[Request, Response]): HttpRoute =
    req => pf.lift(req)

  def combine(routes: List[HttpRoute]): HttpApp =
    req => routes.iterator.flatMap(route => route(req)).toSeq.headOption.getOrElse(Response.notFound)

  def withLogging(app: HttpApp): HttpApp = req => {
    println(s"[log] ${req.method} ${req.path} query=${req.query}")
    val resp = app(req)
    println(s"[log] -> ${resp.status}")
    resp
  }

  def withRequestId(app: HttpApp): HttpApp = req => {
    val requestId = java.util.UUID.randomUUID().toString.take(8)
    val response = app(req)
    response.copy(headers = response.headers + ("X-Request-Id" -> requestId))
  }

  val healthRoute: HttpRoute = route {
    case Request("GET", "/health", _, _, _) =>
      Response.ok("""{"status":"ok","service":"mini-http"}""")
  }

  val helloRoute: HttpRoute = route {
    case Request("GET", "/hello", query, _, _) =>
      val name = query.getOrElse("name", "anonymous")
      Response.ok(s"""{"message":"hello, $name"}""")
  }

  val usersRoute: HttpRoute = route {
    case Request("GET", "/users/42", _, _, _) =>
      Response.ok("""{"id":42,"name":"Alice","role":"admin"}""")
  }

  val createUserRoute: HttpRoute = route {
    case Request("POST", "/users", _, headers, body) if headers.get("Authorization").contains("Bearer demo-token") =>
      val name = if (body.trim.nonEmpty) body.trim else "unknown"
      Response.json(201, s"""{"message":"created","name":"$name"}""")

    case Request("POST", "/users", _, _, _) =>
      Response.badRequest("""{"error":"missing or invalid token"}""")
  }

  val app: HttpApp =
    withRequestId(withLogging(combine(List(healthRoute, helloRoute, usersRoute, createUserRoute))))

  def simulate(req: Request): Unit = {
    println("\n=== 请求开始 ===")
    println(s"Request: $req")
    val response = app(req)
    println(s"Response: status=${response.status}, headers=${response.headers}, body=${response.body}")
  }

  println("=== 最小 http4s 风格服务 ===")
  simulate(Request("GET", "/health"))
  simulate(Request("GET", "/hello", query = Map("name" -> "jiangdadong")))
  simulate(Request("GET", "/users/42"))
  simulate(Request("POST", "/users", body = "Bob"))
  simulate(Request("POST", "/users", headers = Map("Authorization" -> "Bearer demo-token"), body = "Bob"))
  simulate(Request("GET", "/unknown"))

  println("\n=== 重点理解 ===")
  println("- 路由的本质是：给定请求，看看自己能不能处理")
  println("- 中间件的本质是：在不改业务逻辑的前提下，对整个应用做包裹")
  println("- http4s 只是把这些模式做得更强、更安全、更工程化")
}
