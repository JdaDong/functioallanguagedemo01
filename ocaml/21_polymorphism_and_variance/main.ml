(* dune exec ./main.exe
   demo 21: 多态 + 协变/逆变
   - +'a：协变，"输出位置"——能装 Cat 的容器自然能当成"装 Animal 的容器"用
   - -'a：逆变，"输入位置"——能处理 Animal 的函数自然能处理 Cat
   - 普通 'a：不变，既输入又输出
   - OCaml 的 type system 比 Java/C# 更显式，需要程序员标注
*)

(* === 1. 一组类型层级（用 polymorphic variant 模拟 OOP 的子类型） === *)


let animal_name : [< `Animal | `Cat ] -> string = function
  | `Animal -> "动物"
  | `Cat    -> "猫"

(* === 2. 协变 +'a：只读容器 ===
   immutable_box 把 'a 标成 +'a。
   表示：如果 cat <: animal（猫是动物），那么 cat box <: animal box。
   也就是"装猫的盒子" 可以当 "装动物的盒子" 用。 *)
type +'a immutable_box = { value : 'a }

let make_box v = { value = v }

(* === 3. 逆变 -'a：只接收输入的处理器 ===
   如果一个处理器能处理"任何动物"，那它当然也能处理"猫"——更宽容的输入位置。 *)
type -'a handler = { handle : 'a -> string }

let general_animal_handler : [> `Animal | `Cat ] handler =
  { handle = animal_name }

(* === 4. 演示：用一个接受 cat 容器的函数，去吃"装动物的盒子" === *)

(* 这个函数声明它要的是 cat 容器，但因为协变，传 animal 容器也行 *)
let unwrap_to_string (b : [> `Cat | `Animal ] immutable_box) =
  animal_name b.value

let () =
  let cat_box = make_box `Cat in
  let animal_box = make_box `Animal in
  Printf.printf "cat_box    -> %s\n" (unwrap_to_string cat_box);
  Printf.printf "animal_box -> %s\n" (unwrap_to_string animal_box);

  (* 逆变 handler：声明能吃 [> Cat]，但因为 handler 是逆变，
     一个能吃 [> `Animal | `Cat] 的更"宽容"的 handler 也能用 *)
  Printf.printf "handle Cat    = %s\n" (general_animal_handler.handle `Cat);
  Printf.printf "handle Animal = %s\n" (general_animal_handler.handle `Animal);

  (* === 5. 不变（默认）的对照：可变容器不能协变 ===
     如果 box 内部可变，就既是输入又是输出，不能标 +'a。
     这是 Java 数组协变带来 ArrayStoreException 的根因。 *)
  print_endline "可变容器(ref)默认不变——OCaml 编译器会拒绝错误的型变标注"
