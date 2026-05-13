#lang typed/racket
;; ============================================================
;; Demo 08: ⭐ Typed Racket —— 渐进类型
;; ------------------------------------------------------------
;; #lang typed/racket 让你在普通 Racket 上加静态类型。它的特点：
;;   1. 类型在编译期检查（不是运行期 contract）
;;   2. 与无类型 Racket 模块可以互操作（require/typed 桥接）
;;   3. 类型推断 + 显式注解，比 Haskell 更灵活
;;   4. 内建丰富的类型构造器：U（union）、Listof、Pair、HashTable
;;   5. occurrence typing —— 在条件里自动 narrow 类型
;;
;; 本 demo 涵盖：
;;   1. 基础类型：Number / String / Boolean / Symbol
;;   2. 列表类型：(Listof X) / (Pair X Y)
;;   3. 函数类型：(-> X Y)
;;   4. Union 类型：U + occurrence typing
;;   5. 自定义 struct + 多态
;;   6. 与无类型代码互操作（仅文档说明）
;; ============================================================

;; --- 1. 基础类型注解 ------------------------------------------
;; (: 名字 类型) 然后 (define ...)
(: square : (-> Real Real))
(define (square x) (* x x))

(: greet : (->* (String) (String) String))
(define (greet name [greeting "Hello"])
  (string-append greeting ", " name "!"))

;; --- 2. 列表类型 ----------------------------------------------
(: sum-list : (-> (Listof Real) Real))
(define (sum-list lst)
  (cond
    [(null? lst) 0]
    [else (+ (car lst) (sum-list (cdr lst)))]))

(: zip : (All (A B) (-> (Listof A) (Listof B) (Listof (Pair A B)))))
(define (zip xs ys)
  (cond
    [(or (null? xs) (null? ys)) '()]
    [else (cons (cons (car xs) (car ys))
                (zip (cdr xs) (cdr ys)))]))

;; --- 3. Union 类型 + occurrence typing ------------------------
;; 这是 Typed Racket 的招牌：
;; 在 (if (number? x) ...) 分支里，TR 自动 narrow x 的类型为 Number

(: stringify : (-> (U Number String Boolean) String))
(define (stringify v)
  (cond
    [(number? v) (number->string v)]      ; 这里 v 已被 narrow 成 Number
    [(string? v) v]                        ; 这里 v 已被 narrow 成 String
    [else (if v "true" "false")]))         ; 剩下只可能是 Boolean

;; --- 4. 自定义 struct 与多态 ---------------------------------
;; struct 在 typed/racket 里需要类型注解
(struct point ([x : Real] [y : Real]) #:transparent)

(: distance : (-> point point Real))
(define (distance p1 p2)
  (sqrt (+ (square (- (point-x p1) (point-x p2)))
           (square (- (point-y p1) (point-y p2))))))

;; 多态二叉树
(define-type (BTree A) (U 'leaf (Node A)))
(struct (A) Node ([value : A] [left : (BTree A)] [right : (BTree A)])
  #:transparent)

(: tree-size : (All (A) (-> (BTree A) Integer)))
(define (tree-size t)
  (cond
    [(eq? t 'leaf) 0]
    [else (+ 1 (tree-size (Node-left t)) (tree-size (Node-right t)))]))

;; --- 5. 类型驱动的 fold ---------------------------------------
(: foldr-typed : (All (A B) (-> (-> A B B) B (Listof A) B)))
(define (foldr-typed f init lst)
  (cond
    [(null? lst) init]
    [else (f (car lst) (foldr-typed f init (cdr lst)))]))

;; --- 6. 类型错误演示（注释掉，否则编译失败） -----------------
;; 取消下面注释看 TR 报错：
;;   (square "hello")          ;; expected Real, given String
;;   (sum-list '(1 2 "three")) ;; expected (Listof Real), given (Listof (U Integer String))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 08: Typed Racket 渐进类型 =====~n")

  (printf "~n--- 1. 基础类型 ---~n")
  (printf "  (square 5)         = ~a~n" (square 5))
  (printf "  (square 3.14)      = ~a~n" (square 3.14))
  (printf "  (greet \"小明\")      = ~a~n" (greet "小明"))

  (printf "~n--- 2. 列表类型 ---~n")
  (printf "  (sum-list '(1 2 3 4 5)) = ~a~n" (sum-list '(1 2 3 4 5)))
  (printf "  (zip '(a b c) '(1 2 3)) = ~a~n" (zip '(a b c) '(1 2 3)))

  (printf "~n--- 3. Union + occurrence typing ---~n")
  (printf "  (stringify 42)     = ~a~n" (stringify 42))
  (printf "  (stringify \"hi\")   = ~a~n" (stringify "hi"))
  (printf "  (stringify #t)     = ~a~n" (stringify #t))

  (printf "~n--- 4. point + distance ---~n")
  (define p1 (point 0 0))
  (define p2 (point 3 4))
  (printf "  distance (0,0) (3,4) = ~a~n" (distance p1 p2))

  (printf "~n--- 5. 多态二叉树 ---~n")
  (: t (BTree Integer))
  (define t (Node 1 (Node 2 'leaf 'leaf) (Node 3 'leaf (Node 4 'leaf 'leaf))))
  (printf "  tree-size t = ~a~n" (tree-size t))

  (printf "~n--- 6. 类型驱动的 fold ---~n")
  (printf "  (foldr-typed + 0 '(1 2 3 4 5))   = ~a~n"
          (foldr-typed + 0 '(1 2 3 4 5)))
  (printf "  (foldr-typed cons '() '(1 2 3))  = ~a~n"
          (foldr-typed (lambda ([x : Integer] [acc : (Listof Integer)])
                         (cons x acc))
                       '()
                       '(1 2 3)))

  (printf "~n--- TR 杀手锏：occurrence typing ---~n")
  (printf "  在 (if (number? x) ...) 的两个分支里，~n")
  (printf "  Typed Racket 自动把 x 的类型 narrow 成更具体的~n")
  (printf "  (Number 或 非Number)，这是 Java/C# 的 instanceof 都做不到的~n")
  (printf "  (它们要写显式 cast)。这个能力来自 TR 的 PhD 论文 (Tobin-Hochstadt 2010)~n")

  (printf "~n✅ Demo 08 完成~n"))
