#lang racket
;; ============================================================
;; Demo 01: 基础语法 + 列表 + cond / match
;; ------------------------------------------------------------
;; 这一组 demo 假设你看过 Scheme/Lisp 的最基本概念（s-expr / quote），
;; 这里的目的是用 Racket 的工业标准库把 5 个最常用的工具串起来：
;;   1. (define) 定义函数
;;   2. (cond ...) / (case ...) / (if ...) 三种分支
;;   3. 列表三剑客 map / filter / foldl
;;   4. (match ...) 模式匹配（Racket 的 match 比 Scheme 的更工业化）
;;   5. let / let* / letrec / named let 四种局部绑定
;; ============================================================

;; --- 1. 定义函数 -----------------------------------------------
;; (define (函数名 参数 ...) 函数体)
(define (square x) (* x x))
(define (sum-of-squares a b) (+ (square a) (square b)))

;; 多参数 + 默认参数（用 [name default] 写法）
(define (greet name [greeting "你好"])
  (string-append greeting "，" name "！"))

;; --- 2. 三种分支 -----------------------------------------------
;; if 只能一个分支（不像 cond 可以多分支）
(define (abs-of x)
  (if (negative? x) (- x) x))

;; cond 像 if-elif-else
(define (sign-of x)
  (cond
    [(positive? x) '正]
    [(negative? x) '负]
    [else '零]))

;; case 适合枚举值匹配（与 Java switch 像）
(define (day-name n)
  (case n
    [(1) "周一"] [(2) "周二"] [(3) "周三"]
    [(4) "周四"] [(5) "周五"]
    [(6 7) "周末"]
    [else "未知"]))

;; --- 3. 列表三剑客 ---------------------------------------------
(define numbers '(1 2 3 4 5 6 7 8 9 10))

;; map：对每个元素应用函数
(define squares (map square numbers))      ; '(1 4 9 16 25 36 49 64 81 100)

;; filter：保留谓词为真的元素
(define evens (filter even? numbers))      ; '(2 4 6 8 10)

;; foldl：从左折叠（Racket 中 foldl 与 SRFI/Haskell 略不同：
;;        Racket 的 foldl 把累加器放在最右一个参数，
;;        即 (foldl proc init lst) 中 proc 是 (lambda (elem acc) ...)）
(define total (foldl + 0 numbers))         ; 55

;; 链式：求所有偶数的平方和
(define sum-even-squares
  (foldl + 0 (map square (filter even? numbers))))
;; = 4 + 16 + 36 + 64 + 100 = 220

;; --- 4. 模式匹配 -----------------------------------------------
;; Racket 的 match 是宏，不是核心语法（需要 (require racket/match)，
;; 但 #lang racket 默认已经 require 了）

(define (describe-list lst)
  (match lst
    ['() "空列表"]
    [(list x) (format "单元素：~a" x)]
    [(list x y) (format "两元素：~a 和 ~a" x y)]
    [(list x y z ...) (format "首两个：~a、~a；剩下 ~a 个" x y (length z))]))

;; 嵌套模式 + 谓词守卫
(define (categorize p)
  (match p
    [(list x y) #:when (and (number? x) (number? y))
     (format "数对：~a × ~a" x y)]
    [(list (? string? s) _ ...)
     (format "字符串开头：~a" s)]
    [_ "未知形态"]))

;; --- 5. 局部绑定 ----------------------------------------------
;; let：所有绑定并行（不能引用同 let 中的其它绑定）
(define (let-demo)
  (let ([x 10] [y 20])
    (+ x y)))

;; let*：顺序绑定，后面的可以引用前面的
(define (let*-demo)
  (let* ([x 10] [y (* x 2)] [z (+ x y)])
    (list x y z)))                          ; '(10 20 30)

;; letrec：互递归（用于互相引用的局部函数）
(define (parity-via-letrec n)
  (letrec ([even? (lambda (k) (if (zero? k) #t (odd? (- k 1))))]
           [odd?  (lambda (k) (if (zero? k) #f (even? (- k 1))))])
    (if (even? n) '偶 '奇)))

;; named let：写循环的最 Schemey 风格（同时是递归 + 命名 + 入口调用）
(define (factorial n)
  (let loop ([i n] [acc 1])
    (if (zero? i)
        acc
        (loop (- i 1) (* acc i)))))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 01: 基础语法 + cond + match =====~n")
  (printf "~n--- 1. 函数定义 ---~n")
  (printf "  (square 5) = ~a~n" (square 5))
  (printf "  (sum-of-squares 3 4) = ~a~n" (sum-of-squares 3 4))
  (printf "  (greet \"小明\") = ~a~n" (greet "小明"))
  (printf "  (greet \"小明\" \"早上好\") = ~a~n" (greet "小明" "早上好"))

  (printf "~n--- 2. 三种分支 ---~n")
  (for ([x '(-3 0 7)])
    (printf "  ~a: abs=~a, sign=~a~n" x (abs-of x) (sign-of x)))
  (printf "  day-name 1/3/6/9 -> ~a / ~a / ~a / ~a~n"
          (day-name 1) (day-name 3) (day-name 6) (day-name 9))

  (printf "~n--- 3. 列表三剑客 ---~n")
  (printf "  numbers = ~a~n" numbers)
  (printf "  squares = ~a~n" squares)
  (printf "  evens   = ~a~n" evens)
  (printf "  total   = ~a~n" total)
  (printf "  sum of even squares = ~a~n" sum-even-squares)

  (printf "~n--- 4. match 模式 ---~n")
  (for ([lst '(() (1) (1 2) (1 2 3 4 5))])
    (printf "  ~a -> ~a~n" lst (describe-list lst)))
  (printf "  (categorize '(3 4))            -> ~a~n" (categorize '(3 4)))
  (printf "  (categorize '(\"hi\" 1 2 3))     -> ~a~n" (categorize '("hi" 1 2 3)))
  (printf "  (categorize 'random)           -> ~a~n" (categorize 'random))

  (printf "~n--- 5. 局部绑定 ---~n")
  (printf "  let-demo  = ~a~n" (let-demo))
  (printf "  let*-demo = ~a~n" (let*-demo))
  (printf "  parity 7  = ~a~n" (parity-via-letrec 7))
  (printf "  10! = ~a~n" (factorial 10))

  (printf "~n✅ Demo 01 完成~n"))
