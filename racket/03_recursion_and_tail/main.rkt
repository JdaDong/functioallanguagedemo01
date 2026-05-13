#lang racket
;; ============================================================
;; Demo 03: 真尾递归 + named let + 互递归
;; ------------------------------------------------------------
;; Racket（与 Scheme/Standard ML/Erlang 一样）保证**所有**尾调用
;; 不增长栈。这是语言层面的承诺，不是编译器优化。
;;
;; 主题：
;;   1. 累加器 trick：从非尾递归改写成尾递归
;;   2. named let：写循环的最地道方式
;;   3. for / for/list / for/fold：Racket 的 for 不是命令式 for
;;   4. 互递归（mutual recursion）—— even?/odd?
;;   5. 树形递归 vs 尾递归对比（fib 的两种写法）
;; ============================================================

;; --- 1. 经典对比：sum-list 的两种写法 -------------------------
;; 非尾递归：返回前还要做一次加法（栈会增长 N 层）
(define (sum-naive lst)
  (if (null? lst)
      0
      (+ (car lst) (sum-naive (cdr lst)))))

;; 尾递归：用累加器，递归调用是函数体的最后一个动作
(define (sum-tail lst)
  (define (helper xs acc)
    (if (null? xs)
        acc
        (helper (cdr xs) (+ acc (car xs)))))
  (helper lst 0))

;; --- 2. named let：上面的 helper 可以拍扁 ---------------------
;; 写法上更简洁，语义上完全等价
(define (sum-named-let lst)
  (let loop ([xs lst] [acc 0])
    (if (null? xs)
        acc
        (loop (cdr xs) (+ acc (car xs))))))

;; reverse 也用 named let 写得很自然
(define (my-reverse lst)
  (let loop ([xs lst] [acc '()])
    (if (null? xs)
        acc
        (loop (cdr xs) (cons (car xs) acc)))))

;; --- 3. Racket 的 for 家族 ------------------------------------
;; Racket 的 for 不是 C 语言的命令式 for，而是用宏实现的
;; "迭代+累加" 的 DSL，本质上展开成 named let / foldl
;;
;; for/list      — 收集成列表
;; for/fold      — 自定义累加器
;; for/sum       — 求和
;; for/and / for/or — 短路逻辑
;; for*/...      — 嵌套（笛卡尔积）

(define squares-1-to-10
  (for/list ([i (in-range 1 11)]) (* i i)))
;; '(1 4 9 16 25 36 49 64 81 100)

(define sum-of-squares-100
  (for/sum ([i (in-range 1 101)]) (* i i)))
;; 338350

;; for/fold 是最通用的，可以用它实现 map / filter / sum 等
(define rev-via-fold
  (for/fold ([acc '()])
            ([x (in-list '(1 2 3 4 5))])
    (cons x acc)))
;; '(5 4 3 2 1)

;; --- 4. 互递归 -------------------------------------------------
;; Racket 中顶层 define 自动支持互递归（不需要 letrec）
(define (my-even? n)
  (if (zero? n) #t (my-odd? (- n 1))))

(define (my-odd? n)
  (if (zero? n) #f (my-even? (- n 1))))

;; 关键：(my-odd? 100000) 不会爆栈，因为 my-even? 与 my-odd?
;; 都是尾调用对方。Racket 保证尾调用不长栈。

;; --- 5. 树形递归 vs 尾递归（Fibonacci） -----------------------

;; 朴素树形递归：指数复杂度 O(2^n)，n=35 就慢得肉眼可见
(define (fib-naive n)
  (if (< n 2) n (+ (fib-naive (- n 1)) (fib-naive (- n 2)))))

;; 尾递归累加：O(n) + 不长栈
(define (fib-tail n)
  (let loop ([i 0] [a 0] [b 1])
    (if (= i n) a (loop (+ i 1) b (+ a b)))))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 03: 真尾递归 + named let =====~n")

  (printf "~n--- 1. sum 三种写法对比 ---~n")
  (define big-list (build-list 100000 values))
  (printf "  100000 个数求和 (尾递归):       ~a~n" (sum-tail big-list))
  (printf "  100000 个数求和 (named let):    ~a~n" (sum-named-let big-list))
  (printf "  注：sum-naive 在 100000 上会爆栈（这里跳过）~n")
  (printf "  小列表 sum-naive '(1 2 3): ~a~n" (sum-naive '(1 2 3)))

  (printf "~n--- 2. named let 写 reverse ---~n")
  (printf "  (my-reverse '(1 2 3 4 5)) = ~a~n" (my-reverse '(1 2 3 4 5)))

  (printf "~n--- 3. for 家族 ---~n")
  (printf "  for/list squares 1..10  = ~a~n" squares-1-to-10)
  (printf "  for/sum  squares 1..100 = ~a~n" sum-of-squares-100)
  (printf "  for/fold rev '(1..5)    = ~a~n" rev-via-fold)
  (printf "  for*/list cartesian (1..3)x(a,b) = ~a~n"
          (for*/list ([i '(1 2 3)] [c '(a b)]) (list i c)))

  (printf "~n--- 4. 互递归 ---~n")
  (printf "  (my-even? 100000) = ~a (尾调用不爆栈)~n" (my-even? 100000))
  (printf "  (my-odd?  100001) = ~a~n" (my-odd? 100001))

  (printf "~n--- 5. Fibonacci ---~n")
  (printf "  (fib-naive 25)  = ~a (慢，O(2^n))~n" (fib-naive 25))
  (printf "  (fib-tail 1000) = ~a (快，O(n)，长整数)~n" (fib-tail 1000))

  (printf "~n✅ Demo 03 完成~n"))
