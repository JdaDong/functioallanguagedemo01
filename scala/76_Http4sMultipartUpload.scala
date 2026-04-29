//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 76: http4s Multipart 文件上传
 *
 * 前面已经把流式接口、SSE、WebSocket 都走通了，
 * 这一版继续进入服务端最常见的另一个边界：文件上传。
 *
 * - request body 不再只是普通 JSON，而是 multipart/form-data
 * - 一次请求里可能同时带表单字段和文件流
 * - 服务端通常要把“描述信息 + 二进制内容”一起解析出来
 */
import cats.syntax.all._
import cats.effect.{IO, IOApp}
import fs2.Stream
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.multipart.{Multipart, Multiparts, Part}

import java.nio.charset.StandardCharsets

object Http4sMultipartUpload extends IOApp.Simple {

  final case class UploadSummary(
      owner: String,
      note: String,
      filename: String,
      bytes: Long,
      preview: String
  )

  implicit val uploadSummaryEncoder: EntityEncoder[IO, UploadSummary] = jsonEncoderOf[IO, UploadSummary]

  def textValue(multipart: Multipart[IO], field: String): IO[String] =
    multipart.parts.find(_.name.contains(field)).traverse(_.bodyText.compile.string).map(_.getOrElse("").trim)

  def fileValue(multipart: Multipart[IO], field: String): IO[(String, Long, String)] =
    multipart.parts.find(_.name.contains(field)) match {
      case Some(part) =>
        part.body.compile.to(Array).map { bytes =>
          val preview = new String(bytes.take(48), StandardCharsets.UTF_8).replace("\n", "\\n")
          (part.filename.getOrElse("unnamed.bin"), bytes.length.toLong, preview)
        }

      case None =>
        IO.raiseError(new IllegalArgumentException(s"缺少上传字段: $field"))
    }

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / "uploads" =>
      EntityDecoder.mixedMultipartResource[IO]().use { decoder =>
        request.decodeWith(decoder, strict = true) { multipart =>
          for {
            owner <- textValue(multipart, "owner")
            note <- textValue(multipart, "note")
            file <- fileValue(multipart, "payload")
            (filename, bytes, preview) = file
            summary = UploadSummary(owner, note, filename, bytes, preview)
            response <- Created(summary)
          } yield response
        }
      }
  }.orNotFound

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s Multipart：同时解析表单字段和文件内容 ===")
      multiparts <- Multiparts.forSync[IO]
      multipart <- multiparts.multipart(
        Vector(
          Part.formData[IO](name = "owner", value = "alice"),
          Part.formData[IO](name = "note", value = "quarterly finance upload"),
          Part.fileData[IO](
            name = "payload",
            filename = "report.csv",
            entityBody = Stream
              .emits("quarter,revenue\nQ1,120\nQ2,135\nQ3,142\n".getBytes(StandardCharsets.UTF_8))
              .covary[IO],
            headers = Headers(`Content-Type`(MediaType.text.plain))
          )
        )
      )
      request = Request[IO](Method.POST, uri"/uploads", headers = multipart.headers).withEntity(multipart)
      response <- app(request)
      body <- response.as[String]
      _ <- IO.println(s"status=${response.status.code}, body=$body")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Multipart 让一个请求同时携带普通字段和文件流，这正是上传接口的常态")
      _ <- IO.println("- http4s 可以用 mixed multipart 解码器把大字段安全落到临时文件，避免一次性吃进内存")
      _ <- IO.println("- 真正进入大文件场景时，下一步通常就是继续做分块处理、断点续传或对象存储写入")
    } yield ()
}
