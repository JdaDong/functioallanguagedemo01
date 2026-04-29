/**
 * Scala 函数式编程 Demo 14: Functor / Applicative / Monad
 *
 * 这三个抽象常常一起出现：
 * - Functor      : 只会在上下文里 map
 * - Applicative  : 能把上下文中的函数应用到上下文中的值
 * - Monad        : 能根据上一步结果继续生成新的上下文
 */
object FunctorApplicativeMonad extends App {

  trait Functor[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
  }

  trait Applicative[F[_]] extends Functor[F] {
    def pure[A](value: A): F[A]
    def ap[A, B](ff: F[A => B])(fa: F[A]): F[B]

    override def map[A, B](fa: F[A])(f: A => B): F[B] =
      ap(pure(f))(fa)
  }

  trait Monad[F[_]] extends Applicative[F] {
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

    override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
      flatMap(ff)(f => map(fa)(f))
  }

  object Monad {
    def apply[F[_]](implicit instance: Monad[F]): Monad[F] = instance
  }

  implicit val optionMonad: Monad[Option] = new Monad[Option] {
    def pure[A](value: A): Option[A] = Some(value)
    def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] = fa.flatMap(f)
    override def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
  }

  implicit val listMonad: Monad[List] = new Monad[List] {
    def pure[A](value: A): List[A] = List(value)
    def flatMap[A, B](fa: List[A])(f: A => List[B]): List[B] = fa.flatMap(f)
    override def map[A, B](fa: List[A])(f: A => B): List[B] = fa.map(f)
  }

  def demoFunctor[F[_]: Monad, A, B](fa: F[A], f: A => B): F[B] =
    Monad[F].map(fa)(f)

  def demoApplicative[F[_]: Monad, A, B](ff: F[A => B], fa: F[A]): F[B] =
    Monad[F].ap(ff)(fa)

  def demoMonad[F[_]: Monad, A, B](fa: F[A], f: A => F[B]): F[B] =
    Monad[F].flatMap(fa)(f)

  println("=== 用 Option 理解三种抽象 ===")
  val maybeNumber = Option(21)
  println(s"Functor.map: ${demoFunctor(maybeNumber, (x: Int) => x * 2)}")
  println(s"Applicative.ap: ${demoApplicative(Option((x: Int) => x + 10), maybeNumber)}")
  println(s"Monad.flatMap: ${demoMonad(maybeNumber, (x: Int) => Option.when(x > 0)(x * 3))}")

  println("\n=== 用 List 理解上下文中的批量组合 ===")
  val numbers = List(1, 2, 3)
  println(s"Functor.map: ${demoFunctor(numbers, (x: Int) => x * x)}")
  println(s"Applicative.ap: ${demoApplicative(List((x: Int) => x + 1, (x: Int) => x * 10), numbers)}")
  println(s"Monad.flatMap: ${demoMonad(numbers, (x: Int) => List(x, -x))}")

  println("\n=== 一个直观总结 ===")
  println("Functor: 只改上下文里的值，不改变上下文本身")
  println("Applicative: 可以把多个独立上下文组合起来")
  println("Monad: 后一步可以依赖前一步的结果")

  println("\n=== 用 for 推导式看 Monad ===")
  val result = for {
    a <- Option(10)
    b <- Option(5)
    c <- Option(a + b)
  } yield c * 2
  println(s"for 推导式结果: $result")
}
