/**
 * Scala 函数式编程 Demo 19: 并发组合 —— 把独立任务并行起来
 *
 * 在函数式风格里，并发的关键不是共享可变状态，
 * 而是把任务表示成可组合的计算，然后再决定串行还是并行。
 *
 * 这里先用 Scala 标准库的 Future 建立直觉。
 */
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object ConcurrencyDemo extends App {

  case class UserDashboard(profile: String, orders: List[String], coupons: List[String])

  def timed[A](label: String)(thunk: => A): A = {
    val start = System.currentTimeMillis()
    val result = thunk
    val end = System.currentTimeMillis()
    println(s"[$label] 耗时: ${end - start} ms")
    result
  }

  def fetchProfile(userId: Int): Future[String] = Future {
    println("开始获取用户资料")
    Thread.sleep(700)
    s"user-$userId"
  }

  def fetchOrders(userId: Int): Future[List[String]] = Future {
    println("开始获取订单列表")
    Thread.sleep(900)
    List(s"order-$userId-1", s"order-$userId-2")
  }

  def fetchCoupons(userId: Int): Future[List[String]] = Future {
    println("开始获取优惠券")
    Thread.sleep(600)
    List("coupon-10", "coupon-20")
  }

  println("=== 串行组合 Future ===")
  val sequentialDashboard = timed("串行") {
    Await.result(
      for {
        profile <- fetchProfile(42)
        orders <- fetchOrders(42)
        coupons <- fetchCoupons(42)
      } yield UserDashboard(profile, orders, coupons),
      5.seconds
    )
  }
  println(s"串行结果: $sequentialDashboard")

  println("\n=== 并行组合 Future ===")
  val parallelDashboard = timed("并行") {
    val profileF = fetchProfile(42)
    val ordersF = fetchOrders(42)
    val couponsF = fetchCoupons(42)

    Await.result(
      for {
        profile <- profileF
        orders <- ordersF
        coupons <- couponsF
      } yield UserDashboard(profile, orders, coupons),
      5.seconds
    )
  }
  println(s"并行结果: $parallelDashboard")

  println("\n=== 批量并发处理 ===")
  def fetchOrderCount(userId: Int): Future[Int] = Future {
    println(s"统计用户 $userId 的订单数")
    Thread.sleep(300 + (userId % 3) * 150)
    userId % 5 + 1
  }

  val counts = Await.result(
    Future.sequence(List(101, 102, 103, 104).map(fetchOrderCount)),
    5.seconds
  )
  println(s"批量结果: $counts")

  println("\n=== 失败恢复 ===")
  def unstableInventoryService(): Future[Int] = Future {
    Thread.sleep(400)
    throw new RuntimeException("库存服务超时")
  }

  val inventory = Await.result(
    unstableInventoryService().recover {
      case e: Throwable =>
        println(s"发生错误: ${e.getMessage}，回退到默认库存 0")
        0
    },
    3.seconds
  )
  println(s"恢复后的库存值: $inventory")

  println("\n=== 重点理解 ===")
  println("- 串行和并行的差别，常常只是‘何时创建任务’这个组合方式不同")
  println("- 独立任务可以并行启动，再在最后把结果汇总")
  println("- Future 很适合建立并发直觉，但它是 eager 的；后面引入 IO 时会更容易控制执行时机")
}
