/**
 * Scala 函数式编程 Demo 16: State —— 在纯函数里表达状态变化
 *
 * State[S, A] 表示：
 * 给我一个旧状态 S，我会返回一个新状态 S 和一个结果 A。
 *
 * 这样我们就能把“状态变化”也写进纯函数里。
 */
object StateCalculator extends App {

  case class State[S, +A](run: S => (S, A)) {
    def map[B](f: A => B): State[S, B] =
      State(s => {
        val (next, value) = run(s)
        (next, f(value))
      })

    def flatMap[B](f: A => State[S, B]): State[S, B] =
      State(s => {
        val (next, value) = run(s)
        f(value).run(next)
      })
  }

  object State {
    def pure[S, A](value: A): State[S, A] = State(s => (s, value))
    def get[S]: State[S, S] = State(s => (s, s))
    def set[S](newState: S): State[S, Unit] = State(_ => (newState, ()))
    def modify[S](f: S => S): State[S, Unit] = State(s => (f(s), ()))
  }

  case class CalcState(total: Int, history: List[String])

  def add(n: Int): State[CalcState, Int] = State { state =>
    val next = state.copy(
      total = state.total + n,
      history = state.history :+ s"add($n) => ${state.total + n}"
    )
    (next, next.total)
  }

  def multiply(n: Int): State[CalcState, Int] = State { state =>
    val next = state.copy(
      total = state.total * n,
      history = state.history :+ s"multiply($n) => ${state.total * n}"
    )
    (next, next.total)
  }

  def reset: State[CalcState, Unit] =
    State.modify(state => state.copy(total = 0, history = state.history :+ "reset => 0"))

  val program: State[CalcState, Int] = for {
    _ <- add(10)
    _ <- multiply(3)
    _ <- add(5)
    state <- State.get[CalcState]
  } yield state.total

  val initial = CalcState(total = 0, history = Nil)
  val (afterProgram, result) = program.run(initial)

  println("=== State: 用纯函数表达状态推进 ===")
  println(s"最终结果: $result")
  println(s"最终状态: $afterProgram")

  println("\n=== 历史记录 ===")
  afterProgram.history.foreach(step => println(s"- $step"))

  println("\n=== 继续组合更多步骤 ===")
  val more = for {
    _ <- reset
    _ <- add(7)
    _ <- multiply(6)
    current <- State.get[CalcState]
  } yield current

  val (finalState, snapshot) = more.run(afterProgram)
  println(s"重置后快照: $snapshot")
  println(s"最终状态: $finalState")

  println("\n=== 重点理解 ===")
  println("- State 把可变状态的推进过程变成了纯函数")
  println("- 每一步都返回新状态，而不是原地修改旧状态")
  println("- 这种模式特别适合解释器、解析器、计数器、游戏状态等场景")
}
