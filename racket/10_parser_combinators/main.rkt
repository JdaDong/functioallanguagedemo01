#lang racket
;; ============================================================
;; Demo 10: Parser Combinators —— 手写一个 mini parser 库
;; ------------------------------------------------------------
;; Parser combinator 是 FP 经典：把"解析器"变成一个返回
;;   (parser input) -> (cons 解析结果 剩余输入)  或  #f
;; 的函数。然后用 *组合子* 把小 parser 拼成大的。
;;
;; 这里的 parser 操作的是字符串（位置 + 剩余）。比 Haskell 的
;; megaparsec 简单得多，但能解析任意 BNF。
;;
;; 本 demo 实现：
;;   1. 三大基础：char、satisfy、return
;;   2. 三大组合子：seq（>>=）、alt（<|>）、many
;;   3. 衍生组合子：string、digits、whitespace
;;   4. 实战：解析一个迷你算术表达式 1 + 2 * 3
;; ============================================================

;; Parser 类型约定：
;;   parser : string -> (cons value rest-string) | #f
;; 成功返回 (cons 值 剩余字符串)，失败返回 #f

;; --- 1. 三大基础 ----------------------------------------------

;; (return v)：不消耗输入，永远成功，结果是 v
(define ((return v) input)
  (cons v input))

;; (satisfy pred)：消耗一个字符，如果谓词为真则成功
(define ((satisfy pred) input)
  (cond
    [(string=? input "") #f]
    [(pred (string-ref input 0))
     (cons (string-ref input 0) (substring input 1))]
    [else #f]))

;; (char c)：解析特定字符
(define (char c)
  (satisfy (lambda (x) (char=? x c))))

;; --- 2. 三大组合子 --------------------------------------------

;; seq：顺序组合，前一个成功后用其结果生成下一个 parser（即 monadic bind）
(define (seq p f)
  ;; (f val) 必须返回另一个 parser
  (lambda (input)
    (define r1 (p input))
    (cond
      [(not r1) #f]
      [else
       (define p2 (f (car r1)))
       (p2 (cdr r1))])))

;; alt：备选，p1 失败则尝试 p2
(define (alt p1 p2)
  (lambda (input)
    (define r1 (p1 input))
    (or r1 (p2 input))))

;; many：0 个或多个，永不失败（最少 0 个时返回空列表）
(define (many p)
  (lambda (input)
    (let loop ([acc '()] [rest input])
      (define r (p rest))
      (cond
        [(not r) (cons (reverse acc) rest)]
        [else (loop (cons (car r) acc) (cdr r))]))))

;; many1：至少 1 个
(define (many1 p)
  (seq p
       (lambda (x)
         (seq (many p)
              (lambda (xs)
                (return (cons x xs)))))))

;; --- 3. 衍生组合子 --------------------------------------------

;; (parse-string s)：解析字面字符串
(define (parse-string s)
  (cond
    [(string=? s "") (return "")]
    [else
     (seq (char (string-ref s 0))
          (lambda (_)
            (seq (parse-string (substring s 1))
                 (lambda (rest)
                   (return s)))))]))

;; digit / digits
(define digit-parser
  (satisfy char-numeric?))

(define digits-parser
  (seq (many1 digit-parser)
       (lambda (chars)
         (return (string->number (list->string chars))))))

;; whitespace（消耗 0+ 空白）
(define ws-parser
  (many (satisfy char-whitespace?)))

;; (token p)：先吃空白再跑 p
(define (token p)
  (seq ws-parser
       (lambda (_)
         p)))

;; --- 4. 实战：算术表达式 --------------------------------------
;;   expr   = term   (('+' | '-') term)*
;;   term   = factor (('*' | '/') factor)*
;;   factor = number | '(' expr ')'
;;
;; 我们用左结合：1 - 2 - 3 = (1 - 2) - 3 = -4

(define number-parser
  (token digits-parser))

;; expr / term 互相递归 -> 用 lambda 包延迟
(define (expr-parser input) ((make-expr-parser) input))
(define (term-parser input) ((make-term-parser) input))

(define (make-factor-parser)
  ;; factor = number | '(' expr ')'
  (alt number-parser
       (seq (token (char #\())
            (lambda (_)
              (seq expr-parser
                   (lambda (v)
                     (seq (token (char #\)))
                          (lambda (_)
                            (return v)))))))))

(define (make-binop-chain p ops)
  ;; (p (op p)*)，左结合
  (lambda (input)
    (define r1 (p input))
    (cond
      [(not r1) #f]
      [else
       (let loop ([acc (car r1)] [rest (cdr r1)])
         (define op-result ((token (one-of-chars ops)) rest))
         (cond
           [(not op-result) (cons acc rest)]
           [else
            (define op (car op-result))
            (define after-op (cdr op-result))
            (define r2 (p after-op))
            (cond
              [(not r2) (cons acc rest)]
              [else
               (loop (apply-op op acc (car r2))
                     (cdr r2))])]))])))

(define (one-of-chars cs)
  (lambda (input)
    (cond
      [(string=? input "") #f]
      [(memv (string-ref input 0) cs)
       (cons (string-ref input 0) (substring input 1))]
      [else #f])))

(define (apply-op op a b)
  (case op
    [(#\+) (+ a b)]
    [(#\-) (- a b)]
    [(#\*) (* a b)]
    [(#\/) (/ a b)]))

(define (make-term-parser)
  (make-binop-chain (make-factor-parser) '(#\* #\/)))

(define (make-expr-parser)
  (make-binop-chain term-parser '(#\+ #\-)))

;; 顶层：解析后还要消掉尾部空白
(define top-parser
  (seq expr-parser
       (lambda (v)
         (seq ws-parser
              (lambda (_) (return v))))))

(define (calc str)
  (define r (top-parser str))
  (cond
    [(not r) (format "❌ 解析失败：~v" str)]
    [(not (string=? (cdr r) ""))
     (format "⚠ 部分解析：值=~a 剩余=~v" (car r) (cdr r))]
    [else (car r)]))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 10: Parser Combinators =====~n")

  (printf "~n--- 1. 基础 parser ---~n")
  (printf "  (char #\\a) on \"abc\": ~a~n" ((char #\a) "abc"))
  (printf "  (char #\\a) on \"xyz\": ~a~n" ((char #\a) "xyz"))
  (printf "  digit-parser on \"5xy\": ~a~n" (digit-parser "5xy"))
  (printf "  digits-parser on \"42abc\": ~a~n" (digits-parser "42abc"))

  (printf "~n--- 2. 组合子 ---~n")
  (printf "  (parse-string \"hello\") on \"hello world\": ~a~n"
          ((parse-string "hello") "hello world"))
  (printf "  (alt parse-a parse-b) on \"banana\": ~a~n"
          ((alt (parse-string "apple") (parse-string "banana")) "banana"))
  (printf "  (many digit-parser) on \"123abc\": ~a~n"
          ((many digit-parser) "123abc"))

  (printf "~n--- 3. 算术表达式 ---~n")
  (for ([test '("1+2"
                "1 + 2 * 3"
                "(1 + 2) * 3"
                "10 - 3 - 2"
                "2 * (3 + 4) * (5 - 1)"
                "1 / 0 garbage")])
    (printf "  \"~a\"~a= ~a~n"
            test
            (make-string (max 1 (- 30 (string-length test))) #\space)
            (calc test)))

  (printf "~n--- 心智模型 ---~n")
  (printf "  Parser = string -> (value, rest-string) | #f~n")
  (printf "  组合子是高阶函数：seq/alt/many 把小 parser 拼成大的~n")
  (printf "  Haskell 的 megaparsec、Rust 的 nom、Scala 的 fastparse~n")
  (printf "  本质都是这个思路。这是 FP 处理「语法树」的招牌技巧~n")

  (printf "~n✅ Demo 10 完成~n"))
