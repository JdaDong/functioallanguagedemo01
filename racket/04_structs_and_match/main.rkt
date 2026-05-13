#lang racket
;; ============================================================
;; Demo 04: struct + match —— Racket 的 ADT 风格
;; ------------------------------------------------------------
;; Racket 没有 OCaml/Haskell 那样的 sum type 语法，但用
;; struct + match 完全可以表达 ADT。本 demo 用三个例子：
;;   1. 表达式树（解释器风格）
;;   2. 形状（圆/矩形/三角形）+ 多分支处理
;;   3. 二叉树插入/查找/中序遍历
;; ============================================================

;; --- 1. 表达式树 -----------------------------------------------
;; struct 默认不可序列化、不可比较；加 #:transparent 才能让
;; equal? / pretty-print 工作正常（重要！）
(struct lit  (n)         #:transparent)        ; 字面量
(struct add  (l r)       #:transparent)        ; +
(struct mul  (l r)       #:transparent)        ; *
(struct neg  (e)         #:transparent)        ; - (单目)

;; 求值器：用 match 分派
(define (evaluate expr)
  (match expr
    [(lit n)     n]
    [(add l r)   (+ (evaluate l) (evaluate r))]
    [(mul l r)   (* (evaluate l) (evaluate r))]
    [(neg e)     (- (evaluate e))]))

;; 打印器：用 match 转中缀
(define (show expr)
  (match expr
    [(lit n)     (number->string n)]
    [(add l r)   (format "(~a + ~a)" (show l) (show r))]
    [(mul l r)   (format "(~a * ~a)" (show l) (show r))]
    [(neg e)     (format "(-~a)" (show e))]))

;; 简化器：常量折叠（编译器入门）
(define (simplify expr)
  (match expr
    [(add (lit 0) x)         (simplify x)]      ; 0 + x = x
    [(add x (lit 0))         (simplify x)]
    [(mul (lit 0) _)         (lit 0)]           ; 0 * _ = 0
    [(mul _ (lit 0))         (lit 0)]
    [(mul (lit 1) x)         (simplify x)]      ; 1 * x = x
    [(mul x (lit 1))         (simplify x)]
    [(neg (neg x))           (simplify x)]      ; --x = x
    [(add l r)               (add (simplify l) (simplify r))]
    [(mul l r)               (mul (simplify l) (simplify r))]
    [(neg e)                 (neg (simplify e))]
    [_                       expr]))

;; --- 2. 形状 ---------------------------------------------------
(struct circle    (r)        #:transparent)
(struct rect      (w h)      #:transparent)
(struct triangle  (a b c)    #:transparent)

(define (area s)
  (match s
    [(circle r)        (* 3.14159 r r)]
    [(rect w h)        (* w h)]
    [(triangle a b c)
     ;; 海伦公式
     (define p (/ (+ a b c) 2))
     (sqrt (* p (- p a) (- p b) (- p c)))]))

;; 谓词组合：可以用 #:guard 子句
(define (describe-shape s)
  (match s
    [(circle r) #:when (< r 1)         "迷你圆"]
    [(circle r)                         (format "圆 r=~a" r)]
    [(rect w h) #:when (= w h)          (format "正方形 ~ax~a" w h)]
    [(rect w h)                         (format "矩形 ~ax~a" w h)]
    [(triangle a b c) #:when (= a b c)  (format "等边三角形 边=~a" a)]
    [(triangle _ _ _)                   "一般三角形"]))

;; --- 3. 二叉搜索树 ---------------------------------------------
(struct empty-tree () #:transparent)
(struct tnode (val left right) #:transparent)

(define empty (empty-tree))

(define (insert t x)
  (match t
    [(empty-tree)         (tnode x empty empty)]
    [(tnode v l r)
     (cond
       [(< x v)  (tnode v (insert l x) r)]
       [(> x v)  (tnode v l (insert r x))]
       [else     t])]))                          ; 重复值不插入

(define (contains? t x)
  (match t
    [(empty-tree)         #f]
    [(tnode v l r)
     (cond
       [(< x v)  (contains? l x)]
       [(> x v)  (contains? r x)]
       [else     #t])]))

;; 中序遍历 = 升序输出
(define (in-order t)
  (match t
    [(empty-tree)         '()]
    [(tnode v l r)        (append (in-order l) (list v) (in-order r))]))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 04: struct + match (ADT 风格) =====~n")

  (printf "~n--- 1. 表达式树 ---~n")
  (define e1 (add (lit 1) (mul (lit 2) (lit 3))))   ; 1 + 2*3
  (define e2 (mul (add (lit 0) (lit 5)) (lit 4)))   ; (0+5) * 4
  (printf "  e1 = ~a~n" (show e1))
  (printf "  evaluate e1 = ~a~n" (evaluate e1))
  (printf "  simplify e1 = ~a (= ~a)~n"
          (show (simplify e1)) (evaluate (simplify e1)))
  (printf "  e2 = ~a~n" (show e2))
  (printf "  simplify e2 = ~a~n" (show (simplify e2)))
  (printf "    ↑ 注意 (0+5) 被折叠成 5，整体变成 5*4~n")

  (printf "~n--- 2. 形状 ---~n")
  (define shapes
    (list (circle 0.5)
          (circle 2)
          (rect 3 4)
          (rect 5 5)
          (triangle 3 4 5)
          (triangle 6 6 6)))
  (for ([s shapes])
    (printf "  ~a (面积=~a)~n" (describe-shape s) (~r (area s) #:precision 2)))

  (printf "~n--- 3. 二叉搜索树 ---~n")
  (define t
    (foldl (lambda (x acc) (insert acc x))
           empty
           '(5 3 8 1 4 7 9 6 2)))
  (printf "  插入 5 3 8 1 4 7 9 6 2 后中序遍历: ~a~n" (in-order t))
  (printf "  (contains? t 4) = ~a~n" (contains? t 4))
  (printf "  (contains? t 100) = ~a~n" (contains? t 100))

  (printf "~n✅ Demo 04 完成~n"))
