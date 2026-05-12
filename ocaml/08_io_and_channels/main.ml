(* dune exec ./main.exe
   demo 08: 最小 IO
   - Printf.printf：格式化输出（%d / %s / %f）
   - Scanf.sscanf：从字符串里抽结构（这里不读 stdin，避免 demo 卡住）
   - in_channel：open_in / input_line / close_in 三件套读文件
   demo 会写一个临时文件再读回来。
*)

let demo_printf () =
  Printf.printf "整数 %d / 字符串 %s / 浮点 %.2f\n" 42 "你好" 3.14159

let demo_scanf () =
  let s = "name=Alice age=30" in
  let name, age =
    Scanf.sscanf s "name=%s age=%d" (fun n a -> n, a)
  in
  Printf.printf "解析得到 name=%s age=%d\n" name age

let demo_file () =
  let path = Filename.temp_file "ocaml_demo08_" ".txt" in
  (* 写 *)
  let oc = open_out path in
  output_string oc "第一行\n第二行\n第三行\n";
  close_out oc;
  (* 读 *)
  let ic = open_in path in
  (try
     while true do
       let line = input_line ic in
       Printf.printf "[读到] %s\n" line
     done
   with End_of_file -> ());
  close_in ic;
  Sys.remove path

let () =
  demo_printf ();
  demo_scanf ();
  demo_file ()
