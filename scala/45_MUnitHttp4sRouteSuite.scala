//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 45: 用 munit-cats-effect 测试 http4s 路由
 *
 * 40 号 Demo 已经展示过如何测试带 IO 的服务逻辑，
 * 这一版继续往外推进一层：直接测试 HTTP 路由。
 */
import cats.effect.{IO, Ref}
import io.circe.generic.auto._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

final case class Todo(id: Long, title: String, done: Boolean)
final case class CreateTodoRequest(title: String)
final case class ErrorResponse(error: String)

class MUnitHttp4sRouteSuite extends CatsEffectSuite {

  implicit val createTodoDecoder: EntityDecoder[IO, CreateTodoRequest] = jsonOf[IO, CreateTodoRequest]
  implicit val createTodoEncoder: EntityEncoder[IO, CreateTodoRequest] = jsonEncoderOf[IO, CreateTodoRequest]
  implicit val todoDecoder: EntityDecoder[IO, Todo] = jsonOf[IO, Todo]
  implicit val todosDecoder: EntityDecoder[IO, List[Todo]] = jsonOf[IO, List[Todo]]
  implicit val todoEncoder: EntityEncoder[IO, Todo] = jsonEncoderOf[IO, Todo]
  implicit val todosEncoder: EntityEncoder[IO, List[Todo]] = jsonEncoderOf[IO, List[Todo]]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def buildApp: IO[HttpApp[IO]] =
    for {
      nextIdRef <- Ref.of[IO, Long](1L)
      todosRef <- Ref.of[IO, Map[Long, Todo]](Map.empty)
    } yield {
      HttpRoutes.of[IO] {
        case req @ POST -> Root / "todos" =>
          req.as[CreateTodoRequest].flatMap { payload =>
            val title = payload.title.trim
            if (title.isEmpty) {
              BadRequest(ErrorResponse("title 不能为空"))
            } else {
              for {
                id <- nextIdRef.getAndUpdate(_ + 1)
                todo = Todo(id, title, done = false)
                _ <- todosRef.update(_ + (id -> todo))
                response <- Created(todo)
              } yield response
            }
          }

        case GET -> Root / "todos" =>
          todosRef.get.map(_.values.toList.sortBy(_.id)).flatMap(Ok(_))
      }.orNotFound
    }

  test("POST /todos 创建成功后，GET /todos 应该能查到数据") {
    for {
      app <- buildApp
      createResponse <- app(
        Request[IO](Method.POST, uri"/todos")
          .withEntity(CreateTodoRequest("写一条 http4s 集成测试"))
      )
      createdTodo <- createResponse.as[Todo]
      listResponse <- app(Request[IO](Method.GET, uri"/todos"))
      todos <- listResponse.as[List[Todo]]
    } yield {
      assertEquals(createResponse.status, Status.Created)
      assertEquals(createdTodo.id, 1L)
      assertEquals(createdTodo.title, "写一条 http4s 集成测试")
      assertEquals(todos.map(_.title), List("写一条 http4s 集成测试"))
    }
  }

  test("POST /todos 在标题为空时应该返回 400") {
    for {
      app <- buildApp
      response <- app(
        Request[IO](Method.POST, uri"/todos")
          .withEntity(CreateTodoRequest("   "))
      )
      error <- response.as[ErrorResponse]
    } yield {
      assertEquals(response.status, Status.BadRequest)
      assertEquals(error.error, "title 不能为空")
    }
  }

  test("连续创建两个 todo 时，列表顺序应该稳定") {
    for {
      app <- buildApp
      _ <- app(Request[IO](Method.POST, uri"/todos").withEntity(CreateTodoRequest("第一条")))
      _ <- app(Request[IO](Method.POST, uri"/todos").withEntity(CreateTodoRequest("第二条")))
      response <- app(Request[IO](Method.GET, uri"/todos"))
      todos <- response.as[List[Todo]]
    } yield {
      assertEquals(response.status, Status.Ok)
      assertEquals(todos.map(_.id), List(1L, 2L))
      assertEquals(todos.map(_.title), List("第一条", "第二条"))
    }
  }
}
