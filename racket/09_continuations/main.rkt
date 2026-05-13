#lang racket
;; ============================================================
;; Demo 09: ⭐ call/cc —— 第一类延续 (continuation)
;; ------------------------------------------------------------
;; 这是 Scheme/Racket 最独特的 feature 之一。它把"程序当前的剩余
;; 计算"变成一个一等公民值，可以保存、调用、多次激活。
;;
;; (call/cc proc) 把"自己被调用之后的整个剩余程序"打包成一个函数
;; k，然后调用 (proc k)。如果 proc 在内部调用 (k v)，整个程序就
;; 跳回到 call/cc 那个位置，并把 v 作为它的返回值。
;;
;; 听起来很玄乎，但本质上就是：**call/cc 把"return"语句的捕获位置
;; 暴露给程序员**。
;;
;; 三大经典应用：
;;   1. early return：从深度递归中提前返回
;;   2. generator：递归遍历变成可暂停/恢复的迭代器
;;   3. amb：非确定性求值 + 自动回溯（八皇后/逻辑谜题）
;; ============================================================

;; --- 1. early return：从循环中提前返回 -----------------------
;; 没有 break 关键字？用 call/cc 模拟
(define (find-first pred lst)
  (call/cc
   (lambda (return)
     (for ([x (in-list lst)])
       (when (pred x)
         (return x)))         ; 跳出整个 call/cc，返回 x
     #f)))                    ; 找不到则返回 #f

;; --- 2. generator：用 call/cc 实现 ----------------------------
;; 把一个递归过程变成可暂停的迭代器
(define (make-generator gen-fn)
  ;; gen-fn 接受一个 yield 函数。
  ;; 调用返回的 generator 会推进到下一个 yield 点，并返回那个值。
  (define saved-k #f)        ; 保存生成器内部的延续
  (define (resume v)
    (if saved-k
        (saved-k v)
        (gen-fn yield)))
  (define (yield v)
    (call/cc
     (lambda (k)
       (set! saved-k k)
       (return v))))
  (define return #f)         ; 保存外部的延续
  (lambda ()
    (call/cc
     (lambda (k)
       (set! return k)
       (resume #f)))))

;; 用 generator 把 (1..N) 变成迭代器
(define (range-gen n)
  (make-generator
   (lambda (yield)
     (for ([i (in-range n)])
       (yield i))
     'done)))

;; --- 3. amb：非确定性选择 -------------------------------------
;; 经典 SICP 例子：amb 让你"假装"程序能同时尝试多个分支
(define fail-stack '())                  ; 失败时的回溯栈

(define (amb-fail)
  (cond
    [(null? fail-stack) (error 'amb "没有更多选择")]
    [else
     (define top (car fail-stack))
     (set! fail-stack (cdr fail-stack))
     (top)]))                              ; 回到上一个选择点继续尝试

(define (amb . choices)
  (call/cc
   (lambda (k)
     (define (try-rest cs)
       (cond
         [(null? cs) (amb-fail)]
         [else
          ;; 把"尝试剩下的选择"压栈
          (set! fail-stack
                (cons (lambda () (k (try-rest (cdr cs))))
                      fail-stack))
          (car cs)]))
     (try-rest choices))))

(define (assert-amb c)
  (unless c (amb-fail)))

;; 经典：用 amb 解 a + b = 5, a >= b, a,b ∈ {1..4}
(define (amb-find-sum-pair)
  (define a (amb 1 2 3 4))
  (define b (amb 1 2 3 4))
  (assert-amb (= (+ a b) 5))
  (assert-amb (>= a b))
  (list a b))

;; --- 4. 协程：用 call/cc 实现 ping-pong -----------------------
;; 两个协程互相 yield 控制权
(define (coroutine-demo)
  (define ping-k #f)
  (define pong-k #f)
  (define output '())
  (define (push! x) (set! output (cons x output)))

  (define (ping n)
    (when (positive? n)
      (push! `(ping ,n))
      (call/cc (lambda (k)
                 (set! ping-k k)
                 (cond [pong-k (pong-k 'continue)]
                       [else (pong (- n 1))])))
      (ping (- n 1))))

  (define (pong n)
    (when (positive? n)
      (push! `(pong ,n))
      (call/cc (lambda (k)
                 (set! pong-k k)
                 (ping-k 'continue)))
      (pong (- n 1))))

  (ping 3)
  (reverse output))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 09: call/cc 第一类延续 =====~n")

  (printf "~n--- 1. early return ---~n")
  (printf "  (find-first odd? '(2 4 6 7 8 9)) = ~a~n"
          (find-first odd? '(2 4 6 7 8 9)))                    ; 7
  (printf "  (find-first negative? '(1 2 3))  = ~a~n"
          (find-first negative? '(1 2 3)))                      ; #f
  (printf "  ↑ 注意 find-first 用 (return x) 直接跳出 for，~n")
  (printf "    无需 break/break/break，也无需异常机制~n")

  (printf "~n--- 2. generator ---~n")
  (define g (range-gen 5))
  (printf "  range-gen(5) 调用 6 次：")
  (for ([_ (in-range 6)])
    (printf "~a " (g)))
  (printf "~n  ↑ 注意：值是被一个一个 *暂停/恢复* 拿出来的~n")
  (printf "    递归循环被 \"切片\"，每次只推进到下一个 yield~n")

  (printf "~n--- 3. amb 非确定性 ---~n")
  (printf "  amb-find-sum-pair: ~a~n" (amb-find-sum-pair))
  (printf "  ↑ 这相当于让 Racket 自动尝试所有 (a, b) 组合，~n")
  (printf "    遇到 assert 失败就回溯到上一个 amb 选择点。~n")
  (printf "    这是 Prolog 的核心机制，但用 call/cc 用 80 行代码实现了~n")

  (printf "~n--- 4. 协程 ping-pong ---~n")
  (printf "  ~a~n" (coroutine-demo))
  (printf "  ↑ 两个递归过程互相 yield 控制权，没有线程，没有锁~n")

  (printf "~n--- 心智模型 ---~n")
  (printf "  call/cc 把 \"return 语句的目标位置\" 暴露给程序员。~n")
  (printf "  在 OCaml/Haskell 里你需要 monad（continuation monad）~n")
  (printf "  在 Java/Python 里你只能模拟（generator/async 都是受限版本的 call/cc）~n")
  (printf "  在 Scheme/Racket 里它就是一行 (call/cc proc)~n")

  (printf "~n✅ Demo 09 完成~n"))
