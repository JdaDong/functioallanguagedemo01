// 08_AkkaHttp.scala
// Akka HTTP（Directive DSL）
//
// 核心思想：
//   Akka HTTP 是基于 Akka Streams 的 HTTP 服务器，路由使用 Directive DSL：
//
//   与 http4s 的对比：
//     http4s：纯函数式路由，HttpRoutes[IO] = Request[IO] => IO[Option[Response[IO]]]
//             路由是 IO monad 里的函数，类型驱动，可以用 <+> 组合
//
//     Akka HTTP：Directive DSL 通过 ~ 组合，路由用嵌套 DSL 描述
//                背后是 Akka Streams，不是 IO monad
//
//   Directive 是 Akka HTTP 最核心的概念：
//     path("users")   → 匹配路径
//     get             → 匹配 GET 方法
//     post            → 匹配 POST 方法
//     entity(as[T])   → 解析请求体
//     complete(...)   → 返回响应
//
// 本 Demo 演示：
//   1. 基础 Directive：path / get / post / complete
//   2. 参数提取：pathParameter、queryParam、entity
//   3. JSON 序列化（spray-json）
//   4. 与 http4s 路由的直接对比

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-http:10.5.3"
//> using dep "com.typesafe.akka::akka-http-spray-json:10.5.3"
//> using dep "com.typesafe.akka::akka-stream:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap

// ── 领域模型 ──────────────────────────────────────────────────────────────────

case class Product(id: String, name: String, price: Double, stock: Int)
case class CreateProductReq(name: String, price: Double, stock: Int)
case class ErrorResp(code: String, message: String)

// spray-json 序列化（Akka HTTP 默认 JSON 库）
// 对比 http4s：用 circe + io.circe.generic.auto._
given RootJsonFormat[Product]          = jsonFormat4(Product.apply)
given RootJsonFormat[CreateProductReq] = jsonFormat3(CreateProductReq.apply)
given RootJsonFormat[ErrorResp]        = jsonFormat2(ErrorResp.apply)

// ── 路由定义 ──────────────────────────────────────────────────────────────────

class ProductRoutes(store: TrieMap[String, Product]):

  val routes: Route =
    pathPrefix("products") {
      concat(

        // GET /products — 列出所有商品
        // http4s 等价: case GET -> Root / "products" => Ok(...)
        pathEndOrSingleSlash {
          get {
            complete(store.values.toList)
          }
        },

        // POST /products — 创建商品
        // http4s 等价: case req @ POST -> Root / "products" => req.as[CreateProductReq].flatMap(...)
        pathEndOrSingleSlash {
          post {
            entity(as[CreateProductReq]) { req =>
              val id = s"prod-${store.size + 1}"
              val product = Product(id, req.name, req.price, req.stock)
              store.put(id, product)
              complete(StatusCodes.Created, product)
            }
          }
        },

        // GET /products/{id} — 查询单个商品
        // http4s 等价: case GET -> Root / "products" / id => ...
        path(Segment) { id =>
          get {
            store.get(id) match
              case Some(p) => complete(p)
              case None    => complete(StatusCodes.NotFound,
                                ErrorResp("NOT_FOUND", s"商品 $id 不存在"))
          }
        },

        // DELETE /products/{id} — 删除商品
        path(Segment) { id =>
          delete {
            store.remove(id) match
              case Some(_) => complete(StatusCodes.NoContent)
              case None    => complete(StatusCodes.NotFound,
                                ErrorResp("NOT_FOUND", s"商品 $id 不存在"))
          }
        }
      )
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def akkaHttpDemo(): Unit =
  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "http-system")
  import system.executionContext

  val store  = TrieMap.empty[String, Product]
  val routes = ProductRoutes(store).routes

  println("=== Akka HTTP：Directive DSL 路由（对比 http4s）===\n")
  println("HTTP 服务器启动在 http://localhost:8088")
  println("尝试以下命令：")
  println("""  curl -X POST http://localhost:8088/products \\
  |       -H "Content-Type: application/json" \\
  |       -d '{"name":"BTC","price":300.0,"stock":100}'""".stripMargin)
  println("  curl http://localhost:8088/products")
  println("  curl http://localhost:8088/products/prod-1")
  println("  curl -X DELETE http://localhost:8088/products/prod-1")
  println("\n按 Ctrl+C 停止服务器\n")

  val binding = Await.result(
    Http().newServerAt("localhost", 8088).bind(routes),
    5.seconds
  )
  println(s"服务器已绑定到 ${binding.localAddress}")

  println("""
|Akka HTTP vs http4s 路由风格对比：
|
|  http4s (cats-effect):                    Akka HTTP (Directive DSL):
|  ─────────────────────────────────────    ──────────────────────────────────────
|  HttpRoutes.of[IO] {                      pathPrefix("products") {
|    case GET -> Root / "products" / id =>    path(Segment) { id =>
|      ...                                      get {
|  }                                              ...
|                                            } } }
|
|  特点：                                    特点：
|  - 纯函数式，Request => IO[Response]       - Directive DSL，嵌套组合
|  - 类型驱动，编译期安全                    - ~ 操作符组合多个路由
|  - IO monad 描述副作用                     - 基于 Akka Streams 背压
|  - 用 <+> 组合多个路由                     - spray-json 作为默认 JSON 库""".stripMargin)

  // 等待退出信号
  sys.addShutdownHook {
    Await.result(binding.unbind(), 3.seconds)
    system.terminate()
  }
  Thread.currentThread().join()
