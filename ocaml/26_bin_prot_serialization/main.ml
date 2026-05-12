(* dune exec ./main.exe
   demo 26: bin_prot ── Jane Street 的高性能二进制序列化
   - 给类型加 [@@deriving bin_io] 即可获得 bin_writer / bin_reader
   - 比 sexp / json 快 5-10 倍，体积也更紧凑
   - 内部用 Bigstring（off-heap），适合网络 RPC（demo 25 的底层格式就是它）
   - 对标 Protobuf / FlatBuffers，但和 OCaml 类型系统天然一体
*)

open Core

(* === 1. 一个嵌套类型，自动派生 bin_io === *)
type address = {
  city : string;
  zip  : int;
} [@@deriving bin_io, sexp]

type person = {
  name    : string;
  age     : int;
  emails  : string list;
  address : address;
} [@@deriving bin_io, sexp]

(* === 2. 一次完整的序列化-反序列化往返 === *)
let demo_round_trip () =
  let p = {
    name = "Alice";
    age = 30;
    emails = ["alice@x.com"; "alice@y.com"];
    address = { city = "Shanghai"; zip = 200000 };
  } in

  (* 用 bin_writer / bin_reader 写到 Bigstring *)
  let writer = bin_writer_person in
  let reader = bin_reader_person in
  let size = writer.size p in
  let buf  = Bin_prot.Common.create_buf size in
  let written = writer.write buf ~pos:0 p in
  let pos_ref = ref 0 in
  let p2 = reader.read buf ~pos_ref in

  printf "encode size = %d bytes\n" size;
  printf "written     = %d bytes\n" written;
  printf "decoded pos = %d\n" !pos_ref;
  printf "原始: %s\n" (Sexp.to_string (sexp_of_person p));
  printf "解码: %s\n" (Sexp.to_string (sexp_of_person p2));
  assert ([%equal: string] p.name p2.name);
  assert (p.age = p2.age);
  assert ([%equal: string list] p.emails p2.emails);
  printf "✓ round-trip 一致\n"

(* === 3. 体积对比：bin_prot vs sexp === *)
let demo_size_compare () =
  let p = {
    name = "Bob";
    age = 25;
    emails = ["bob@example.com"];
    address = { city = "Beijing"; zip = 100000 };
  } in
  let bin_size = bin_writer_person.size p in
  let sexp_size = String.length (Sexp.to_string (sexp_of_person p)) in
  printf "bin_prot  = %d bytes\n" bin_size;
  printf "sexp 文本 = %d bytes\n" sexp_size;
  printf "bin_prot 体积是 sexp 的 %.1f%%\n"
    (Float.of_int bin_size /. Float.of_int sexp_size *. 100.)

let () =
  printf "── round trip ──\n"; demo_round_trip ();
  printf "── 体积对比 ──\n";   demo_size_compare ()
