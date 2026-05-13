#lang racket
;; ============================================================
;; Demo 05: 宏入门 —— define-syntax + syntax-rules
;; ------------------------------------------------------------
;; Racket 宏的"由浅入深"三步：
;;   syntax-rules    -> 模板宏，最简单，纯模式替换
;;   syntax-parse    -> 工业级，可校验语法类、自定义错误（Demo 06）
;;   编译期任意 Racket 代码 (begin-for-syntax) -> 终极武器
;;
;; 本 demo 只讲 syntax-rules：
;;   1. 第一个宏：unless
;;   2. 省略号模式 ...：可变参数
;;   3. 卫生（hygiene）—— 自动避免变量捕获
;;   4. 实用宏：swap! / while / my-let
;;   5. 与函数的关键区别：宏是编译期重写
;; ============================================================

;; --- 1. 第一个宏：unless ---------------------------------------
;; (unless test body ...) ≡ (if test (void) (begin body ...))
(define-syntax-rule (unless test body ...)
  (if test (void) (begin body ...)))

;; --- 2. 省略号模式：my-and ------------------------------------
;; 注意：Racket 内建 and 是宏，因为它需要短路求值（普通函数不行）。
;; 我们这里手写一个，体会"宏 = 短路 / 控制流"的关键能力。
;;
;; 重点：(args ...) 是模式变量，匹配 0 个或多个；模板里 (args ...) 重新展开
(define-syntax my-and
  (syntax-rules ()
    [(_)              #t]                                ; 0 参：默认 #t
    [(_ x)            x]                                 ; 1 参：直接返回
    [(_ x rest ...)   (if x (my-and rest ...) #f)]))     ; 多参：递归短路

;; --- 3. 卫生（hygiene）演示 -----------------------------------
;; 关键例子：swap! 内部用 tmp 中转。Racket 的 syntax-rules 自动卫生，
;; 即使外部恰好有 tmp，也不会冲突。
(define-syntax-rule (swap! a b)
  (let ([tmp a])
    (set! a b)
    (set! b tmp)))

;; --- 4. while 循环 --------------------------------------------
;; Racket 没有内建 while（鼓励 named let），但我们可以用宏实现一个
(define-syntax-rule (while cond body ...)
  (let loop ()
    (when cond
      body ...
      (loop))))

;; --- 5. 自己实现 my-let（理解 let 的本质：lambda 的语法糖） ---
;; (my-let ([x 1] [y 2]) body) ≡ ((lambda (x y) body) 1 2)
(define-syntax my-let
  (syntax-rules ()
    [(_ ([name val] ...) body ...)
     ((lambda (name ...) body ...) val ...)]))

;; --- 6. 关键区别：宏 vs 函数 ----------------------------------
;; 函数版本的"unless"为什么不行？
;;   (define (unless-fn test body) (if test (void) body))
;;   (unless-fn (file-exists? "x") (delete-file "x"))
;;   会先求值 (delete-file "x")（不管文件是否存在），再传给 unless-fn！
;; 宏不会，因为宏在求值前就已经把代码重写成 if。
(define-syntax-rule (debug-print x)
  ;; 把表达式以源码形式打出来，再求值
  ;; '(quote x) 在宏里，模式变量 x 会被展开成实际传入的 syntax，
  ;; 所以 (debug-print (+ 1 2)) 会变成 (begin (display '(+ 1 2)) ...)
  (begin
    (display "debug: ")
    (write 'x)
    (display " = ")
    (writeln x)))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 05: 宏入门 syntax-rules =====~n")

  (printf "~n--- 1. unless ---~n")
  (define x 5)
  (unless (negative? x)
    (printf "  ~a 不是负数~n" x))
  (unless (zero? 0)
    (printf "  ← 这条不会打印~n"))
  (printf "  ↑ 上一条没打印就对了~n")

  (printf "~n--- 2. my-and 短路求值 ---~n")
  (printf "  (my-and)              = ~a~n" (my-and))
  (printf "  (my-and 1 2 3)        = ~a~n" (my-and 1 2 3))
  (printf "  (my-and 1 #f 99999)   = ~a~n" (my-and 1 #f 99999))
  ;; 验证短路：第三个参数应该不被求值
  (define eval-counter 0)
  (define (track x) (set! eval-counter (+ eval-counter 1)) x)
  (my-and #t #f (track 'never-evaluated))
  (printf "  短路验证：(track) 调用次数 = ~a (应为 0)~n" eval-counter)

  (printf "~n--- 3. swap! 卫生 ---~n")
  (define a 1)
  (define b 2)
  (printf "  before: a=~a b=~a~n" a b)
  (swap! a b)
  (printf "  after:  a=~a b=~a~n" a b)
  ;; 卫生测试：让外部也有名为 tmp 的变量
  (define tmp 999)
  (define p 10)
  (define q 20)
  (swap! p q)
  (printf "  外部 tmp=~a 不被宏覆盖：p=~a q=~a~n" tmp p q)

  (printf "~n--- 4. while ---~n")
  (define i 0)
  (define result '())
  (while (< i 5)
    (set! result (cons i result))
    (set! i (+ i 1)))
  (printf "  while 收集 0..4: ~a~n" (reverse result))

  (printf "~n--- 5. my-let ---~n")
  (printf "  (my-let ([x 10] [y 20]) (+ x y)) = ~a~n"
          (my-let ([x 10] [y 20]) (+ x y)))

  (printf "~n--- 6. 宏的能力：debug-print ---~n")
  (debug-print (+ 1 2 3))
  (debug-print (* 6 7))
  (printf "  ↑ 注意：源码形式的表达式被打出来了。~n")
  (printf "    这是函数做不到的（函数收到的是 6，看不到 (+ 1 2 3)）~n")

  (printf "~n✅ Demo 05 完成~n"))
