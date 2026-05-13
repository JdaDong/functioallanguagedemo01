#lang racket
;; ============================================================
;; Demo 15: ⭐ 综合实战 —— 用宏写一个状态机 DSL
;; ------------------------------------------------------------
;; 这是 Racket 宏的"毕业设计"：把 demo 05 (syntax-rules 入门)、
;; demo 06 (syntax-parse 工业级) 综合起来，定义一个领域专用语言
;; 来描述状态机，然后让宏把它编译成普通 Racket 函数。
;;
;; DSL 语法（自定义）：
;;   (define-state-machine traffic-light
;;     #:initial 'red
;;     [red    (timeout) -> 'green]
;;     [green  (timeout) -> 'yellow]
;;     [yellow (timeout) -> 'red]
;;     [yellow (emergency) -> 'red])
;;
;; 编译目标（宏展开后等价的 Racket 代码）：
;;   (define (traffic-light)
;;     (let ([state 'red])
;;       (lambda (event)
;;         (set! state
;;               (case state
;;                 [(red)
;;                  (case event [(timeout) 'green] [else state])]
;;                 [(green)
;;                  (case event [(timeout) 'yellow] [else state])]
;;                 [(yellow)
;;                  (case event [(timeout) 'red] [(emergency) 'red] [else state])]
;;                 [else state]))
;;         state)))
;;
;; 这个例子能学到 4 件事：
;;   1. 用 syntax-parse 接收复杂 DSL 语法
;;   2. 在编译期收集和重组信息（按 from-state 分组）
;;   3. 生成 case 嵌套表达式
;;   4. DSL 出错时给出有意义的报错（用 syntax/loc）
;; ============================================================

(require (for-syntax syntax/parse
                     racket/list
                     racket/syntax))

;; --- 1. 用 syntax-parse 定义 DSL --------------------------------

;; 一个 transition：(from-state (event) -> to-state)
(begin-for-syntax
  (define-syntax-class transition
    #:description "一条状态转移：[from (event) -> to]"
    (pattern [from:id (event:id) (~datum ->) to:expr])))

;; 主宏
(define-syntax (define-state-machine stx)
  (syntax-parse stx
    [(_ name:id
        (~optional (~seq #:initial init:expr) #:defaults ([init #'(error 'name "缺少 #:initial")]))
        t:transition ...)
     ;; t.from / t.event / t.to 都是 syntax 列表
     ;; 我们要按 t.from 分组：每个 from-state 对应一个 inner case
     #:with init-stx (attribute init)
     (define ts (syntax->list #'(t ...)))
     ;; 每个 transition 形如 [from (event) -> to]，syntax->list 给 (from (event) -> to)
     ;; 所以 (car ...) 取 from
     (define froms (map (lambda (s) (syntax->datum (car (syntax->list s)))) ts))
     ;; 用 unique froms 收集所有 from-state
     (define unique-froms (remove-duplicates froms))
     ;; 给每个 from-state 生成 (case from-state-name [(event) to] ...)
     (define inner-cases
       (for/list ([f (in-list unique-froms)])
         (define f-stx (datum->syntax stx f))
         (define matching
           (filter (lambda (t)
                     (eq? (syntax->datum (car (syntax->list t))) f))
                   ts))
         ;; 收集这个 from 的所有 (event -> to)
         (define event-clauses
           (for/list ([m (in-list matching)])
             (define parts (syntax->list m))
             ;; parts: (from (event) -> to)  即 4 个元素
             ;; (cadr parts) = (event)，再 car 拿到 event 标识符
             (define event (car (syntax->list (cadr parts))))
             (define to    (list-ref parts 3))
             #`[(#,event) #,to]))
         ;; 加 [else state] 作为兜底
         #`[(#,f-stx)
            (case event #,@event-clauses [else state])]))

     #`(define (name)
         (let ([state init])
           (lambda (event)
             (set! state
                   (case state
                     #,@inner-cases
                     [else state]))
             state)))]))

;; --- 2. 用 DSL 定义两个状态机 ---------------------------------

(define-state-machine traffic-light
  #:initial 'red
  [red    (timeout) -> 'green]
  [green  (timeout) -> 'yellow]
  [yellow (timeout) -> 'red]
  [yellow (emergency) -> 'red])

(define-state-machine door
  #:initial 'closed
  [closed  (open)  -> 'opened]
  [opened  (close) -> 'closed]
  [opened  (lock)  -> 'locked]
  [locked  (unlock) -> 'opened])

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 15: 状态机 DSL =====~n")

  (printf "~n--- 1. 红绿灯 ---~n")
  (define tl (traffic-light))
  (printf "  初始状态: ~a~n" (tl 'noop))           ; noop 不变
  (printf "  → timeout: ~a~n" (tl 'timeout))       ; green
  (printf "  → timeout: ~a~n" (tl 'timeout))       ; yellow
  (printf "  → emergency: ~a~n" (tl 'emergency))   ; red
  (printf "  → timeout: ~a~n" (tl 'timeout))       ; green

  (printf "~n--- 2. 门锁 ---~n")
  (define d (door))
  (printf "  初始: ~a~n" (d 'noop))                ; closed
  (printf "  open: ~a~n" (d 'open))                ; opened
  (printf "  lock: ~a~n" (d 'lock))                ; locked
  (printf "  open: ~a~n" (d 'open))                ; 在 locked 状态 open 无效，仍 locked
  (printf "  unlock: ~a~n" (d 'unlock))            ; opened
  (printf "  close: ~a~n" (d 'close))              ; closed

  (printf "~n--- 3. 多个实例彼此独立 ---~n")
  (define tl-a (traffic-light))
  (define tl-b (traffic-light))
  (tl-a 'timeout) (tl-a 'timeout)                   ; tl-a 推进到 yellow
  (printf "  tl-a 状态: ~a (yellow)~n" (tl-a 'noop))
  (printf "  tl-b 状态: ~a (red, 不受 tl-a 影响)~n" (tl-b 'noop))

  (printf "~n--- 4. 宏展开后的代码 ---~n")
  (printf "  上面的 define-state-machine 在编译期被宏展开成~n")
  (printf "  普通的 (define (name) (let ([state ...]) (lambda (event) ...)))~n")
  (printf "  运行期没有任何 DSL 解释开销，与手写完全等价~n")

  (printf "~n--- 心智模型 ---~n")
  (printf "  这 70 行代码达到了 OCaml 22 typeclass-via-modules 一样的效果：~n")
  (printf "  - 用户写 DSL，看起来像是新语法~n")
  (printf "  - 实际上在编译期被翻译成普通函数~n")
  (printf "  - 错误信息精确（syntax-parse 给的位置）~n")
  (printf "  - 多次实例化彼此独立（闭包封装 state）~n")
  (printf "  这是 Lisp 同象性 (homoiconicity) + Racket 工程化加持的合力~n")

  (printf "~n--- 跨语言对照 ---~n")
  (printf "  - Clojure 12 (macros_state_machine):  同样思路，但 Clojure 用 multimethod 分派~n")
  (printf "  - Scala / Akka FSM:                   用 trait + sealed class 表达，验证靠类型系统~n")
  (printf "  - Erlang gen_statem:                  用 OTP behaviour，在运行时 dispatch~n")
  (printf "  - OCaml GADT (demo 20):               用类型系统强制状态转移合法~n")

  (printf "~n✅ Demo 15 完成 — Racket 15 demo 全部完成 🎩~n"))
