(* dune exec ./main.exe
   demo 34: ETL 流水线 ── Async + Pipe 的工业管道
   - Pipe 是 Jane Street Async 里的"惰性流"，类似 Go channel
   - 流水线: source → parse → validate → enrich → sink
   - 错误恢复: 解析失败的行不阻塞下游，单独走错误支线
   - 这正是 Jane Street 内部 ticker plant / 风控引擎的写法
*)

open Core
open Async

(* === 业务类型 === *)
type raw_record   = string                          (* 来自上游的"脏"数据 *)
type parsed       = { id : int; price : float }     (* parse 后的结构 *)
type enriched     = { id : int; price : float; tax : float; total : float }

(* === 步骤 1: parse ── 可能失败 === *)
let parse line : (parsed, string) Result.t =
  match String.split line ~on:',' with
  | [a; b] ->
      (try Ok { id = Int.of_string a; price = Float.of_string b }
       with _ -> Error (sprintf "parse fail: %s" line))
  | _ -> Error (sprintf "format fail: %s" line)

(* === 步骤 2: validate ── 业务规则 === *)
let validate (p : parsed) =
  if Float.( <= ) p.price 0.0 then Error (sprintf "id=%d 价格非正: %f" p.id p.price)
  else if p.id < 0 then Error (sprintf "id=%d 负数 id" p.id)
  else Ok p

(* === 步骤 3: enrich ── 加税总价 === *)
let enrich (p : parsed) : enriched =
  let tax = p.price *. 0.13 in
  { id = p.id; price = p.price; tax; total = p.price +. tax }

(* === 4: 把上面拼成一条 pipe-based 流水线 === *)
let run_pipeline (source : raw_record list) =
  (* 把 list 灌进 source pipe *)
  let src_r, src_w = Pipe.create () in
  List.iter source ~f:(fun line -> Pipe.write_without_pushback src_w line);
  Pipe.close src_w;

  (* 错误支线 *)
  let err_r, err_w = Pipe.create () in
  let bad_count = ref 0 in
  don't_wait_for (
    Pipe.iter err_r ~f:(fun e ->
      incr bad_count;
      printf "  ✗ %s\n" e;
      return ())
  );

  (* parse + validate + enrich，三步合一 *)
  let%bind enriched_list =
    Pipe.fold src_r ~init:[] ~f:(fun acc line ->
      match parse line with
      | Error e -> Pipe.write_without_pushback err_w e; return acc
      | Ok p ->
          match validate p with
          | Error e -> Pipe.write_without_pushback err_w e; return acc
          | Ok p ->
              let e = enrich p in
              return (e :: acc))
  in
  Pipe.close err_w;
  return (List.rev enriched_list, !bad_count)

let main () =
  (* 6 行：3 行正常、1 行格式错、1 行解析错、1 行业务错 *)
  let raw = [
    "1,100.0";
    "2,50.5";
    "3,200.0";
    "bad-line";              (* 格式错（无逗号）*)
    "4,not-a-number";        (* 解析错 *)
    "5,-10.0";               (* 业务错（负价）*)
  ] in
  printf "── 输入 %d 行 ──\n" (List.length raw);
  let%bind (good, bad) = run_pipeline raw in
  printf "── 成功 %d 条 ──\n" (List.length good);
  List.iter good ~f:(fun e ->
    printf "  ✓ id=%d price=%.2f tax=%.2f total=%.2f\n"
      e.id e.price e.tax e.total);
  printf "── 失败 %d 条（已走错误支线）──\n" bad;
  shutdown 0;
  return ()

let () =
  don't_wait_for (main ());
  never_returns (Scheduler.go ())
