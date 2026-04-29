//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 40: 用 munit-cats-effect 写真实 IO 测试
 *
 * 前面 25 号 Demo 讲的是“测试解释器”，
 * 这一版继续往工程里走：把带 IO 的服务放进真正的测试框架里执行断言。
 */
import cats.effect.{IO, Ref}
import munit.CatsEffectSuite

final case class CounterService(ref: Ref[IO, Int]) {
  def inc: IO[Int] = ref.updateAndGet(_ + 1)
  def reset: IO[Unit] = ref.set(0)
  def get: IO[Int] = ref.get
}

class MUnitCatsEffectSuite extends CatsEffectSuite {

  test("inc 应该让计数器递增") {
    for {
      ref <- Ref.of[IO, Int](0)
      service = CounterService(ref)
      first <- service.inc
      second <- service.inc
      current <- service.get
    } yield {
      assertEquals(first, 1)
      assertEquals(second, 2)
      assertEquals(current, 2)
    }
  }

  test("reset 应该把状态清空") {
    for {
      ref <- Ref.of[IO, Int](10)
      service = CounterService(ref)
      _ <- service.reset
      current <- service.get
    } yield assertEquals(current, 0)
  }

  test("多个 effect 可以在同一个测试里串起来断言") {
    for {
      ref <- Ref.of[IO, Int](3)
      service = CounterService(ref)
      _ <- service.inc
      _ <- service.reset
      _ <- service.inc
      current <- service.get
    } yield assertEquals(current, 1)
  }
}
