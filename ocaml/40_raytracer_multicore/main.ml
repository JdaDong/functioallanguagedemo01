(* dune exec ./main.exe
   demo 40: 多核光线追踪 ── OCaml 5 Domain 并行 + PPM 输出
   - 场景：3 个球（红/绿/蓝）+ 1 平面地板 + 1 个点光源
   - 着色：Lambert 漫反射（最简）
   - 并行：把图像按行切块，每块丢给一个 Domain
   - 验证：单核 vs 4 核加速比 + 写出 PPM 文件
*)

(* === 三维向量 === *)
type vec = { x : float; y : float; z : float }
let v x y z = { x; y; z }
let ( +! ) a b = v (a.x +. b.x) (a.y +. b.y) (a.z +. b.z)
let ( -! ) a b = v (a.x -. b.x) (a.y -. b.y) (a.z -. b.z)
let ( *! ) a s = v (a.x *. s) (a.y *. s) (a.z *. s)
let dot a b = a.x *. b.x +. a.y *. b.y +. a.z *. b.z
let len2 a = dot a a
let norm a = let l = sqrt (len2 a) in a *! (1.0 /. l)

(* === 几何 === *)
type sphere = { center : vec; radius : float; color : vec }
type plane  = { point : vec; normal : vec; color : vec }

(* 射线-球：返回 None 或 Some (t, normal) ── 最近正向命中点 *)
let hit_sphere ray_o ray_d s =
  let oc = ray_o -! s.center in
  let b = dot oc ray_d in
  let c = len2 oc -. s.radius *. s.radius in
  let disc = b *. b -. c in
  if disc < 0.0 then None
  else
    let t = -. b -. sqrt disc in
    if t > 1e-3 then
      let p = ray_o +! ray_d *! t in
      let n = norm (p -! s.center) in
      Some (t, p, n)
    else None

let hit_plane ray_o ray_d p =
  let denom = dot p.normal ray_d in
  if abs_float denom < 1e-6 then None
  else
    let t = (dot (p.point -! ray_o) p.normal) /. denom in
    if t > 1e-3 then
      let pos = ray_o +! ray_d *! t in
      Some (t, pos, p.normal)
    else None

(* === 场景 === *)
let spheres = [
  { center = v (-1.0)  0.0  (-3.0); radius = 0.7; color = v 1.0 0.2 0.2 };
  { center = v   0.0   0.0  (-2.5); radius = 0.5; color = v 0.2 1.0 0.2 };
  { center = v   1.2   0.2  (-3.5); radius = 0.8; color = v 0.2 0.4 1.0 };
]
let floor = { point = v 0.0 (-0.7) 0.0; normal = v 0.0 1.0 0.0;
              color = v 0.7 0.7 0.7 }
let light_pos = v 3.0 5.0 0.0

(* 找最近命中（球 + 平面） *)
let trace_closest ray_o ray_d =
  let best = ref None in
  let consider hit color =
    match hit with
    | None -> ()
    | Some (t, p, n) ->
        match !best with
        | Some (t', _, _, _) when t' <= t -> ()
        | _ -> best := Some (t, p, n, color)
  in
  List.iter (fun s -> consider (hit_sphere ray_o ray_d s) s.color) spheres;
  consider (hit_plane ray_o ray_d floor) floor.color;
  !best

(* === 着色：Lambert + 简单阴影 === *)
let shade ray_d hit =
  match hit with
  | None ->
      (* 背景：根据 ray_d.y 渐变 *)
      let t = 0.5 *. (ray_d.y +. 1.0) in
      v ((1.0 -. t) +. t *. 0.5) ((1.0 -. t) +. t *. 0.7) 1.0
  | Some (_, p, n, color) ->
      let l = norm (light_pos -! p) in
      let diffuse = max 0.0 (dot n l) in
      let amb = 0.2 in
      color *! (amb +. (1.0 -. amb) *. diffuse)

(* === 渲染单个像素 === *)
let width = 320
let height = 240
let aspect = float_of_int width /. float_of_int height
let cam_origin = v 0.0 0.5 1.0

let render_pixel x y =
  (* 屏幕空间 → 视空间（FOV ≈ 60°） *)
  let u = (float_of_int x +. 0.5) /. float_of_int width  *. 2.0 -. 1.0 in
  let vv = 1.0 -. (float_of_int y +. 0.5) /. float_of_int height *. 2.0 in
  let dir = norm (v (u *. aspect) vv (-1.5)) in
  shade dir (trace_closest cam_origin dir)

(* === 渲染整图，可注入"对哪些行渲染" === *)
let render_rows row_lo row_hi (img : vec array) =
  for y = row_lo to row_hi - 1 do
    for x = 0 to width - 1 do
      img.(y * width + x) <- render_pixel x y
    done
  done

(* === 单核 === *)
let render_single () =
  let img = Array.make (width * height) (v 0.0 0.0 0.0) in
  render_rows 0 height img;
  img

(* === 多核：把行切 N 块，开 N 个 Domain === *)
let render_parallel n_domains =
  let img = Array.make (width * height) (v 0.0 0.0 0.0) in
  let chunk = (height + n_domains - 1) / n_domains in
  let domains = Array.init n_domains (fun i ->
    let lo = i * chunk in
    let hi = min height (lo + chunk) in
    Domain.spawn (fun () -> render_rows lo hi img))
  in
  Array.iter Domain.join domains;
  img

(* === 写 PPM === *)
let to_byte c = int_of_float (max 0.0 (min 1.0 c) *. 255.0)
let write_ppm path img =
  let oc = open_out path in
  Printf.fprintf oc "P3\n%d %d\n255\n" width height;
  Array.iter (fun c ->
    Printf.fprintf oc "%d %d %d " (to_byte c.x) (to_byte c.y) (to_byte c.z))
    img;
  close_out oc

let time_it name f =
  let t0 = Unix.gettimeofday () in
  let r = f () in
  let dt = Unix.gettimeofday () -. t0 in
  Printf.printf "  %-20s 耗时 %.3fs\n" name dt;
  dt, r

let () =
  Printf.printf "── 渲染 %dx%d 三球场景 ──\n" width height;
  let dt1, img1 = time_it "single core" render_single in
  let dt4, img4 = time_it "4 domains  " (fun () -> render_parallel 4) in
  Printf.printf "  加速比 = %.2fx\n" (dt1 /. dt4);

  (* 校验：两种渲染应得到相同图像 *)
  let same = ref true in
  Array.iter2 (fun a b ->
    if abs_float (a.x -. b.x) +. abs_float (a.y -. b.y)
       +. abs_float (a.z -. b.z) > 1e-9
    then same := false) img1 img4;
  Printf.printf "  单核 / 多核结果一致: %b\n" !same;

  let path = "out.ppm" in
  write_ppm path img4;
  Printf.printf "  已写出 %s（%d 像素）\n" path (width * height)
