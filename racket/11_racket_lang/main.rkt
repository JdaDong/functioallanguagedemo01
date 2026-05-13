#lang racket
;; ============================================================
;; Demo 11: ⭐ #lang —— Racket "语言工作台"杀手锏
;; ------------------------------------------------------------
;; Racket 的 #lang 不是注释、不是版本声明，而是真正的"用什么语言"
;; 指令。每个 .rkt 文件第一行的 #lang 决定它使用哪一套：
;;   - reader（如何把字符串解析成 syntax 对象）
;;   - expander（如何展开宏 / 顶层语义）
;;   - 默认 require 哪些标准库
;;
;; 这是 Clojure / OCaml / Haskell 都做不到的事：
;;   Clojure 改语法只能改 ClojureScript（要造另一个项目）
;;   OCaml 的 PPX 只能改宏不能改语法
;;   Haskell 的 QuasiQuoter 只能在 [| ... |] 内有效
;;   Racket：换行就换语言。
;;
;; 实际产品级例子：
;;   #lang scribble/manual    Racket 自己的文档
;;   #lang pollen             Matthew Butterick 的出版语言
;;   #lang htdp/bsl           教材"How to Design Programs"专用
;;   #lang typed/racket       渐进类型（demo 08 用过）
;;   #lang racket/base        最小核心
;;   #lang lazy               懒求值 Racket
;;
;; 本 demo 不能创建一个真正可被 #lang foo 引用的语言（那需要在
;; collection root 安装），但我们演示**reader 拦截**与**自定义
;; module-begin** 这两个核心技术。
;; ============================================================

;; ------------------------------------------------------------
;; 第一部分：用 syntax/module-reader 风格手动模拟 reader
;; 把任意"逗号分隔表达式"变成 racket 的 begin
;; ------------------------------------------------------------

;; 一个"自定义 reader"会把 "1, 2, 3" 这样的输入翻译成 (begin 1 2 3)
;; 这里我们手写这个翻译，模拟 #lang 的工作方式：
(define (custom-read-string str)
  ;; 把 "1, 2, 3" -> (begin 1 2 3)
  (define parts (regexp-split #px"\\s*,\\s*" str))
  (cons 'begin (map (lambda (s) (read (open-input-string s))) parts)))

;; ------------------------------------------------------------
;; 第二部分：自定义 module-begin，演示语义层介入
;; ------------------------------------------------------------

;; 一个 "auto-print" 语言：每条顶层表达式都自动 println
(define-syntax auto-print-module-begin
  (syntax-rules ()
    [(_ expr ...)
     (#%module-begin
      (begin
        (let ([v expr])
          (printf "↳ ~v~n" v))
        ...
        (void)))]))

;; ------------------------------------------------------------
;; 第三部分：演示一个"假语言"：reverse-polish-lang
;; 把 "(3 4 +)" 解释成 (+ 3 4)
;; ------------------------------------------------------------
(define (rpn-eval tokens)
  ;; tokens 是 list，比如 '(3 4 +)
  ;; 用栈解释 RPN
  (let loop ([stack '()] [ts tokens])
    (cond
      [(null? ts)
       (cond
         [(= (length stack) 1) (car stack)]
         [else (error 'rpn "栈结束时不止一个元素")])]
      [(number? (car ts))
       (loop (cons (car ts) stack) (cdr ts))]
      [(memv (car ts) '(+ - * /))
       (cond
         [(< (length stack) 2)
          (error 'rpn "栈不够 2 个元素，无法运算 ~v" (car ts))]
         [else
          (define b (car stack))
          (define a (cadr stack))
          (define op (car ts))
          (define r (case op
                      [(+) (+ a b)] [(-) (- a b)]
                      [(*) (* a b)] [(/) (/ a b)]))
          (loop (cons r (cddr stack)) (cdr ts))])]
      [else (error 'rpn "未知 token ~v" (car ts))])))

;; ------------------------------------------------------------
;; 第四部分：演示"如何打包成真正的 #lang"（仅文档说明）
;; ------------------------------------------------------------
;; 真正打包一个 #lang foo 需要：
;;   1. 创建 collection 目录 collects/foo/
;;   2. 在 foo/lang/reader.rkt 写 reader：
;;        #lang s-exp syntax/module-reader
;;        foo/main
;;   3. 在 foo/main.rkt 写 expander，用 (provide ... #%module-begin)
;;      暴露你想让用户写的语法
;;   4. raco link 注册到本地包系统
;;   5. 之后任何 .rkt 文件首行写 "#lang foo" 就能用
;;
;; 这是 Beautiful Racket 那本书前 3 章的内容。

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 11: #lang 自定义语言基础 =====~n")

  (printf "~n--- 1. reader 拦截示意 ---~n")
  (printf "  原始输入字符串: \"1, 2, 3\"~n")
  (define parsed (custom-read-string "1, 2, 3"))
  (printf "  parsed s-expr   = ~v~n" parsed)
  (printf "  eval 结果       = ~a~n" (eval parsed (make-base-namespace)))
  (printf "  ↑ 这就是 reader 的本质：把字节流翻译成 s-expression~n")

  (printf "~n--- 2. RPN（逆波兰）求值 ---~n")
  ;; 演示一个最小"语言"：RPN
  (for ([test '((3 4 +)               ; 7
                (3 4 + 5 *)            ; 35
                (10 2 / 3 +)           ; 8
                (1 2 3 4 + + +))])     ; 10
    (printf "  ~a → ~a~n" test (rpn-eval test)))

  (printf "~n--- 3. 一个真正的 #lang 怎么打包 ---~n")
  (printf "  - 创建目录 collects/my-lang/~n")
  (printf "  - my-lang/lang/reader.rkt 写 reader（如何 parse）~n")
  (printf "  - my-lang/main.rkt 写 expander（提供 #%module-begin）~n")
  (printf "  - raco link my-lang/ 注册~n")
  (printf "  - 之后任何 .rkt 文件首行 \"#lang my-lang\" 就能用~n")
  (printf "  - 详见 *Beautiful Racket* by Matthew Butterick~n")

  (printf "~n--- 真正的 power ---~n")
  (printf "  Pollen（出版语言）、Scribble（文档）、HtDP（教学）、~n")
  (printf "  Pie（依值类型教学）等 \"工业级\" Racket 项目，~n")
  (printf "  全都是用同一个 #lang 机制，共享 IDE/包系统/REPL~n")
  (printf "  这是 Racket 团队 30 年 PL 研究攒下的工程红利~n")

  (printf "~n✅ Demo 11 完成~n"))
