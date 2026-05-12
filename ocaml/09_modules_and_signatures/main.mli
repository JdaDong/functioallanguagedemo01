(* main.mli — 接口文件
   只暴露这里写的内容，其它 main.ml 里的细节全部对外不可见。
   这就是 OCaml 的"信息隐藏"机制。 *)

(* 抽象类型：外界只知道"有这个类型"，不知道它怎么实现的。
   所以外界没法直接 [] 构造，必须走我们提供的 empty / push。 *)
type 'a stack

val empty : 'a stack
val push  : 'a -> 'a stack -> 'a stack
val pop   : 'a stack -> ('a * 'a stack) option
val size  : 'a stack -> int
