#lang racket
;; ============================================================
;; Demo 06: ⭐ syntax-parse —— 工业级宏
;; ------------------------------------------------------------
;; syntax-rules 在错误处理上很弱：写错语法只会得到一句模糊的
;; "no matching template"。syntax-parse 是 Racket 的工业级宏框架：
;;   1. 内建 syntax class（id、expr、number、str、...）
;;   2. 自定义 syntax class（如"非空标识符列表"）
;;   3. 完整的错误消息：能说出"在位置 X，期待 Y 得到 Z"
;;   4. ~optional / ~seq / ~or* / ~and 灵活模式
;;   5. 可以混用 syntax-parse + syntax-case + syntax-rules
;;
;; 本 demo 用 4 个例子：
;;   1. swap-typed!     —— 强制要求两个参数都是 identifier
;;   2. for/list-with-index —— 自定义带索引的 for/list
;;   3. defmemo         —— 定义带 memo 的函数（自定义 syntax class）
;;   4. struct-out      —— 演示 ~optional 与 ~seq
;; ============================================================

(require (for-syntax syntax/parse))

;; --- 1. swap-typed! 强制两个参数都是 identifier ---------------
;; 与 demo 05 的 (swap! a b) 不同，这次我们用 syntax-parse 校验
;; 参数必须是 id（不能是 (swap! 1 2) 这种）
(define-syntax (swap-typed! stx)
  (syntax-parse stx
    [(_ a:id b:id)
     #'(let ([tmp a]) (set! a b) (set! b tmp))]))

;; --- 2. for/list-with-index ------------------------------------
;; (for/list-with-index ([i x] (in-list lst))  body ...)
;; 同时拿到 index 和元素
(define-syntax (for/list-with-index stx)
  (syntax-parse stx
    [(_ ([i:id x:id] seq) body ...+)
     #'(for/list ([i (in-naturals)]
                  [x seq])
         body ...)]))

;; --- 3. 自定义 syntax class：non-empty-id-list ----------------
(begin-for-syntax
  (define-syntax-class non-empty-id-list
    #:description "非空标识符列表"
    (pattern (id:id ...+))))

;; defmemo: 类似 define，但生成的函数自动带 hash 缓存
(define-syntax (defmemo stx)
  (syntax-parse stx
    [(_ (name:id args:non-empty-id-list) body ...+)
     #'(define name
         (let ([cache (make-hash)])
           (lambda args
             (hash-ref! cache (list . args)
                        (lambda () body ...)))))]))

;; --- 4. ~optional / ~seq 灵活模式 ------------------------------
;; (greet name) 或 (greet name #:greeting "你好")
(define-syntax (greet stx)
  (syntax-parse stx
    [(_ name:str (~optional (~seq #:greeting g:str) #:defaults ([g #'"Hello"])))
     #'(string-append g ", " name "!")]))

;; --- 5. 错误消息对比 -----------------------------------------
;; 故意写错让你看错误消息（main 段会注释掉）：
;;   (swap-typed! 1 2)   -> "expected identifier; given 1 (位置精确指出)"
;;   (defmemo (foo) 1)   -> "expected non-empty-id-list"
;; 这些是 syntax-rules 给不出的体验

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 06: syntax-parse 工业级宏 =====~n")

  (printf "~n--- 1. swap-typed! ---~n")
  (define a 100)
  (define b 200)
  (printf "  before: a=~a b=~a~n" a b)
  (swap-typed! a b)
  (printf "  after:  a=~a b=~a~n" a b)
  (printf "  写 (swap-typed! 1 2) 会在编译期报错（非 id）~n")

  (printf "~n--- 2. for/list-with-index ---~n")
  (define result
    (for/list-with-index ([i x] (in-list '(apple banana cherry date)))
      (format "[~a]=~a" i x)))
  (for ([line result]) (printf "  ~a~n" line))

  (printf "~n--- 3. defmemo ---~n")
  (defmemo (slow-fib n)
    (if (< n 2)
        n
        (+ (slow-fib (- n 1)) (slow-fib (- n 2)))))
  ;; 没 memo 时 (slow-fib 35) 要 ~5 秒；有 memo 后毫秒级
  (define start (current-inexact-milliseconds))
  (define f30 (slow-fib 30))
  (define elapsed (- (current-inexact-milliseconds) start))
  (printf "  (slow-fib 30) = ~a，用时 ~a ms (memo 起作用)~n"
          f30 (~r elapsed #:precision 1))

  (printf "~n--- 4. ~~optional + ~~seq ---~n")
  (printf "  (greet \"小明\")                 = ~a~n"
          (greet "小明"))
  (printf "  (greet \"小明\" #:greeting \"早上好\") = ~a~n"
          (greet "小明" #:greeting "早上好"))

  (printf "~n--- 5. 错误消息体验 ---~n")
  (printf "  把下行注释打开会得到 syntax-parse 给的精确错误：~n")
  (printf "  ;; (swap-typed! 1 2)  ;; -> expected identifier~n")
  (printf "  ;; (greet 42)         ;; -> expected str~n")

  (printf "~n✅ Demo 06 完成~n"))
