#lang racket
;; ============================================================
;; Demo 14: Property Testing —— 手写 mini quickcheck + rackcheck 对照
;; ------------------------------------------------------------
;; "性质测试" = 不写具体输入，而写"对任意输入应当成立的性质"，
;; 让框架随机生成输入来检验。Haskell QuickCheck 是开山鼻祖，
;; 各语言都有自己的版本（Erlang/Elixir StreamData、Scala
;; ScalaCheck、Rust proptest、Clojure test.check）。
;;
;; 本 demo 分两部分：
;;   1. 手写 mini-quickcheck：~80 行，理解原理
;;   2. 真 rackcheck（如果装了）的对照（用 dynamic-require 不失败）
;; ============================================================

;; --- Part 1: mini-quickcheck ----------------------------------
;; 核心三个概念：
;;   gen :: () -> a              生成器（每次调用产生一个随机值）
;;   property :: a -> bool       性质（对该值应当成立的断言）
;;   check :: gen, prop, n -> result    跑 n 次，找反例

;; 生成器
(define (gen-int low high)
  (lambda () (+ low (random (+ 1 (- high low))))))

(define (gen-list elem-gen)
  (lambda ()
    (define n (random 10))                      ; 0..9 个元素
    (for/list ([_ (in-range n)]) (elem-gen))))

(define (gen-bool)
  (lambda () (zero? (random 2))))

(define (gen-pair g1 g2)
  (lambda () (cons (g1) (g2))))

;; check 函数：跑 n 次，遇到反例就返回它
(struct check-result (passed? counterexample) #:transparent)

(define (check gen prop [n 100])
  (let loop ([i 0])
    (cond
      [(= i n) (check-result #t #f)]
      [else
       (define x (gen))
       (cond
         [(prop x) (loop (+ i 1))]
         [else (check-result #f x)])])))

;; --- 经典性质 1：reverse 自反 ---------------------------------
;; reverse(reverse(xs)) == xs
(define (prop-reverse-involutive xs)
  (equal? (reverse (reverse xs)) xs))

;; --- 经典性质 2：append 长度可加 ------------------------------
(define (prop-append-length pair)
  (define xs (car pair))
  (define ys (cdr pair))
  (= (length (append xs ys))
     (+ (length xs) (length ys))))

;; --- 经典性质 3：sort 后是单调递增 ----------------------------
(define (prop-sort-monotonic xs)
  (define sorted (sort xs <))
  (let loop ([lst sorted])
    (cond
      [(or (null? lst) (null? (cdr lst))) #t]
      [(> (car lst) (cadr lst)) #f]
      [else (loop (cdr lst))])))

;; --- 经典性质 4：max 是结合律 ---------------------------------
;; (max (max a b) c) == (max a (max b c))
(define (prop-max-associative triple)
  (define a (car triple))
  (define bc (cdr triple))
  (define b (car bc))
  (define c (cdr bc))
  (= (max (max a b) c)
     (max a (max b c))))

;; --- 故意错的性质（用来演示反例） -----------------------------
;; "所有列表都至少有一个元素"——明显错误
(define (prop-fake xs)
  (> (length xs) 0))

;; --- 报告 ------------------------------------------------------
(define (run-prop name gen prop)
  (define result (check gen prop 200))
  (cond
    [(check-result-passed? result)
     (printf "  ✅ ~a (200 次随机测试通过)~n" name)]
    [else
     (printf "  ❌ ~a 反例: ~v~n" name (check-result-counterexample result))]))

;; --- Part 2: 检查 rackcheck 是否可用 ---------------------------
(define (try-rackcheck)
  (with-handlers ([exn:fail? (lambda (e) #f)])
    (dynamic-require 'rackcheck #f)
    #t))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 14: Property Testing =====~n")

  (printf "~n--- 手写 mini-quickcheck ---~n")
  (define int-gen (gen-int -100 100))
  (define list-gen (gen-list int-gen))
  (define bool-gen (gen-bool))

  (run-prop "reverse(reverse(xs)) == xs" list-gen prop-reverse-involutive)
  (run-prop "length(xs ++ ys) == length(xs) + length(ys)"
            (gen-pair list-gen list-gen)
            prop-append-length)
  (run-prop "sort 后单调递增" list-gen prop-sort-monotonic)
  (run-prop "max 结合律"
            (gen-pair int-gen (gen-pair int-gen int-gen))
            prop-max-associative)

  (printf "~n--- 故意错的性质 ---~n")
  (run-prop "所有列表都非空（错误！）" list-gen prop-fake)

  (printf "~n--- rackcheck 对照 ---~n")
  (cond
    [(try-rackcheck)
     (printf "  ✓ rackcheck 已安装，可以这样用：~n")
     (printf "    (require rackcheck)~n")
     (printf "    (define-property reverse-involutive ~n")
     (printf "      ([xs (gen:list gen:integer)])~n")
     (printf "      (equal? xs (reverse (reverse xs))))~n")
     (printf "    (check-property reverse-involutive)~n")]
    [else
     (printf "  ⚠ 未安装 rackcheck（可选）~n")
     (printf "    安装：raco pkg install rackcheck~n")
     (printf "    rackcheck 比手写版本多了：~n")
     (printf "    - shrinking（自动把反例缩小到最小）~n")
     (printf "    - 类型化生成器组合子~n")
     (printf "    - 与 rackunit 集成~n")])

  (printf "~n--- 心智模型 ---~n")
  (printf "  Property Testing = 把「测试」重新定义为「对任意输入成立的性质」~n")
  (printf "  框架做的事：随机生成输入 + 找反例 + shrink 反例~n")
  (printf "  关键设计：~n")
  (printf "  1. Generator combinators（gen-list、gen-pair 这样组合）~n")
  (printf "  2. Counterexample shrinking（找到反例后缩小成最小例）~n")
  (printf "  对应：Haskell QuickCheck / Elixir StreamData / Scala ScalaCheck~n")

  (printf "~n✅ Demo 14 完成~n"))
