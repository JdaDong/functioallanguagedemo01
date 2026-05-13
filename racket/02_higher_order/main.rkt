#lang racket
;; ============================================================
;; Demo 02: 高阶函数 + 闭包 + 函数组合 + 部分应用
;; ------------------------------------------------------------
;; 主题：
;;   1. 函数即一等公民：作为参数 / 作为返回值
;;   2. 闭包：lambda 捕获词法环境
;;   3. compose：函数组合
;;   4. curry / curryr：柯里化与部分应用
;;   5. apply：把列表展开成参数
;; ============================================================

;; --- 1. 函数作为参数 -------------------------------------------
;; apply-twice 是高阶：第一个参数是函数
(define (apply-twice f x)
  (f (f x)))

;; --- 2. 函数作为返回值（闭包工厂） ----------------------------
;; (make-multiplier 3) 返回一个函数，这个函数把入参乘以 3
(define (make-multiplier factor)
  (lambda (x) (* x factor)))

(define triple (make-multiplier 3))
(define quintuple (make-multiplier 5))

;; 闭包持有状态（用 set! 模拟可变计数器，演示用，非纯）
(define (make-counter)
  (let ([n 0])
    (lambda ()
      (set! n (+ n 1))
      n)))

;; --- 3. 函数组合 -----------------------------------------------
;; (compose f g) 返回 (lambda (x) (f (g x)))
;; Racket 的 compose 接受任意多个函数，从右到左应用
(define add1-then-square (compose square add1))
;;  add1: x -> x+1
;;  square: x -> x*x
;;  add1-then-square: x -> (x+1)*(x+1)
(define (square x) (* x x))   ; 重新定义一份（避免依赖 demo 01）

;; 三函数组合
(define neg-square-add1 (compose - square add1))
;; x -> -((x+1)^2)

;; --- 4. curry / curryr 部分应用 -------------------------------
;; Racket 不像 Haskell 那样默认柯里化，但提供了显式 curry：
;;   (curry  f a)  =>  返回一个函数，等价于 (lambda (b ...) (f a b ...))
;;   (curryr f a)  =>  把 a 放到最右

(define add (lambda (a b) (+ a b)))
(define add-10 (curry add 10))            ; 部分应用第一个参数
(define sub (lambda (a b) (- a b)))
(define sub-10 (curryr sub 10))           ; 部分应用最后一个参数：x -> x - 10

;; 与 map 配合：把列表里每个数都加 100
(define plus-100 (curry + 100))           ; +接受可变参数，curry 也能用
(define lst-plus-100 (map plus-100 '(1 2 3 4)))   ; '(101 102 103 104)

;; --- 5. apply：列表 → 多参数 ----------------------------------
;; (apply f '(1 2 3)) 等价于 (f 1 2 3)
(define numbers-args '(1 2 3 4 5))
(define max-of-list (apply max numbers-args))     ; 5
(define sum-via-apply (apply + numbers-args))     ; 15

;; --- 6. 实战：函数管道 ----------------------------------------
;; Racket 没有内建 |>，但用 compose 可以达到同样效果。
;; 也可以手写一个 pipeline：

(define (pipeline x . fs)
  ;; (pipeline 5 add1 square -)  ===>  -(((5)+1)^2) = -36
  (foldl (lambda (f acc) (f acc)) x fs))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 02: 高阶函数 + 闭包 + 组合 =====~n")

  (printf "~n--- 1. 函数作为参数 ---~n")
  (printf "  (apply-twice add1 5)        = ~a~n" (apply-twice add1 5))         ; 7
  (printf "  (apply-twice (λ (x) (* x 2)) 3) = ~a~n" (apply-twice (λ (x) (* x 2)) 3))   ; 12

  (printf "~n--- 2. 闭包工厂 ---~n")
  (printf "  (triple 4)     = ~a~n" (triple 4))                                 ; 12
  (printf "  (quintuple 4)  = ~a~n" (quintuple 4))                              ; 20
  (let ([c (make-counter)])
    (printf "  counter:    ~a ~a ~a ~a~n" (c) (c) (c) (c)))                     ; 1 2 3 4

  (printf "~n--- 3. 函数组合 (compose) ---~n")
  (printf "  (add1-then-square 4) = ~a (=(4+1)^2)~n" (add1-then-square 4))      ; 25
  (printf "  (neg-square-add1 4)  = ~a (=-(4+1)^2)~n" (neg-square-add1 4))      ; -25

  (printf "~n--- 4. curry / curryr ---~n")
  (printf "  (add-10 5)    = ~a (= 10+5)~n" (add-10 5))                         ; 15
  (printf "  (sub-10 50)   = ~a (= 50-10)~n" (sub-10 50))                       ; 40
  (printf "  (map plus-100 '(1 2 3 4)) = ~a~n" lst-plus-100)

  (printf "~n--- 5. apply ---~n")
  (printf "  (apply max '(1 2 3 4 5)) = ~a~n" max-of-list)
  (printf "  (apply +   '(1 2 3 4 5)) = ~a~n" sum-via-apply)

  (printf "~n--- 6. pipeline 手写 ---~n")
  (printf "  (pipeline 5 add1 square -) = ~a~n" (pipeline 5 add1 square -))     ; -36
  (printf "  pipeline 把 (foldl) 当胶水，逐步把 x 喂给后续函数~n")

  (printf "~n✅ Demo 02 完成~n"))
