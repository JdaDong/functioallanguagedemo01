#lang racket
;; ============================================================
;; Demo 07: ⭐ contract —— Racket 的一等公民契约系统
;; ------------------------------------------------------------
;; Racket 的 racket/contract 不是装饰器、不是注释，是真正的语言级
;; 一等公民。它做三件事：
;;   1. 在模块边界处自动校验
;;   2. 出错时**精确指出 blame 责任方**（调用者还是被调者）
;;   3. 与 Typed Racket（demo 08）形成"动态/静态"两条线
;;
;; 本 demo 涵盖：
;;   1. -> 简单契约
;;   2. ->* 可选参数 / ->i 依赖契约
;;   3. flat-contract / and/c / or/c
;;   4. blame 演示：故意触发，看错误消息精确性
;;   5. 在递归数据结构上加契约
;; ============================================================

(require racket/contract)

;; --- 1. -> 简单契约 -------------------------------------------
;; 用 define/contract 给函数加契约
(define/contract (safe-div a b)
  (-> number? (and/c number? (not/c zero?)) number?)
  (/ a b))

;; --- 2. ->* 可选参数 ------------------------------------------
;; (->* (必需参数...) (可选参数...) 返回)
(define/contract (greet name [greeting "Hello"])
  (->* (string?) (string?) string?)
  (string-append greeting ", " name "!"))

;; --- 3. ->i 依赖契约 ------------------------------------------
;; ->i 让返回值或后续参数依赖前面的参数
;; 这里：第二个参数必须是 0..len(lst)-1
(define/contract (safe-list-ref lst i)
  (->i ([lst list?]
        [i (lst) (and/c exact-nonnegative-integer? (</c (length lst)))])
       [_ any/c])
  (list-ref lst i))

;; --- 4. flat-contract / and/c / or/c --------------------------
(define positive-even/c (and/c integer? positive? even?))
(define string-or-number/c (or/c string? number?))

(define/contract (double x)
  (-> positive-even/c integer?)
  (* x 2))

(define/contract (stringify x)
  (-> string-or-number/c string?)
  (cond
    [(string? x) x]
    [else (number->string x)]))

;; --- 5. 在递归数据结构上加契约 -------------------------------
;; 树节点：(node val (listof node))
(define (tree/c)
  (or/c '()
        (list/c any/c (recursive-contract (listof (recursive-contract (tree/c)))))))

;; flat 列表的契约
(define non-empty-list-of-numbers/c
  (and/c (listof number?)
         (lambda (lst) (not (null? lst)))))

(define/contract (avg lst)
  (-> non-empty-list-of-numbers/c real?)
  (/ (apply + lst) (length lst)))

;; --- 6. 自定义契约：positive-prime/c ---------------------------
(define (prime? n)
  (and (> n 1)
       (for/and ([d (in-range 2 (add1 (integer-sqrt n)))])
         (not (zero? (modulo n d))))))

(define positive-prime/c
  (flat-named-contract 'positive-prime?
                       (and/c integer? positive? prime?)))

(define/contract (next-prime n)
  (-> positive-prime/c positive-prime/c)
  (let loop ([k (+ n 1)])
    (if (prime? k) k (loop (+ k 1)))))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 07: 一等公民契约 =====~n")

  (printf "~n--- 1. 正常调用 ---~n")
  (printf "  (safe-div 10 3)        = ~a~n" (~r (safe-div 10 3) #:precision 3))
  (printf "  (greet \"小明\")          = ~a~n" (greet "小明"))
  (printf "  (greet \"小明\" \"早\")     = ~a~n" (greet "小明" "早"))
  (printf "  (safe-list-ref '(a b c d) 2) = ~a~n" (safe-list-ref '(a b c d) 2))
  (printf "  (double 10)            = ~a~n" (double 10))
  (printf "  (stringify 42)         = ~a~n" (stringify 42))
  (printf "  (stringify \"hi\")       = ~a~n" (stringify "hi"))
  (printf "  (avg '(1 2 3 4 5))     = ~a~n" (avg '(1 2 3 4 5)))
  (printf "  (next-prime 7)         = ~a~n" (next-prime 7))

  (printf "~n--- 2. blame 演示：故意触发，看错误消息 ---~n")
  ;; 用 with-handlers 捕获契约错误，让 demo 不会崩溃，且能看到完整 blame
  (define (try-and-show name thunk)
    (with-handlers ([exn:fail:contract? (lambda (e)
      (printf "  ❌ ~a:~n" name)
      ;; 只展示前 3 行，避免太长
      (define lines (regexp-split #rx"\n" (exn-message e)))
      (for ([line (in-list (take lines (min 3 (length lines))))])
        (printf "     ~a~n" line)))])
      (thunk)))

  (try-and-show "(safe-div 10 0) - 除零"
                (lambda () (safe-div 10 0)))
  (try-and-show "(greet 42) - 类型错"
                (lambda () (greet 42)))
  (try-and-show "(safe-list-ref '(a b c) 100) - 越界"
                (lambda () (safe-list-ref '(a b c) 100)))
  (try-and-show "(double 7) - 非偶数"
                (lambda () (double 7)))
  (try-and-show "(avg '()) - 空列表"
                (lambda () (avg '())))

  (printf "~n--- 3. 契约的关键价值：blame ---~n")
  (printf "  注意上方错误消息里 \"blaming: top-level\" 字样—~n")
  (printf "  契约系统能精确说出：是 *谁* 违反了契约（调用者 vs 实现者）~n")
  (printf "  这是单纯类型系统给不了的：类型错误只说\"哪里类型不对\"，~n")
  (printf "  契约错误能说\"哪里 + 谁的责任 + 哪条具体规则\"~n")

  (printf "~n✅ Demo 07 完成~n"))
