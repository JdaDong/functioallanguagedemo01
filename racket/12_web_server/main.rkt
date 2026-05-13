#lang racket
;; ============================================================
;; Demo 12: web-server —— Racket 内置 HTTP 服务
;; ------------------------------------------------------------
;; Racket 标准库自带 web-server。用 (require web-server/servlet-env)
;; 一行就能起一个真实 HTTP 服务，类似 Python 的 Flask。
;;
;; 本 demo 演示：
;;   1. 在 8765 端口起 server
;;   2. 路由 / 与 /hello/<name> 与 /add?a=1&b=2
;;   3. 用 sync/timeout 等到自测请求完成后自动停止
;;
;; ⚠ 这个 demo 会真实占用 8765 端口约 2 秒。
;; ============================================================

(require web-server/servlet
         web-server/servlet-env
         web-server/dispatch
         net/url
         net/http-client)

;; --- 1. 路由 handlers -----------------------------------------

(define (home-handler req)
  (response/output
   (lambda (out)
     (display "Hello from Racket web-server!\n试试 /hello/Alice 或 /add?a=3&b=4\n" out))))

(define (hello-handler req name)
  (response/output
   (lambda (out)
     (fprintf out "Hello, ~a! 来自 Racket 的问候 🎩\n" name))))

(define (add-handler req)
  (define bindings (request-bindings/raw req))
  (define a-binding (bindings-assq #"a" bindings))
  (define b-binding (bindings-assq #"b" bindings))
  (define a (and a-binding (string->number (bytes->string/utf-8 (binding:form-value a-binding)))))
  (define b (and b-binding (string->number (bytes->string/utf-8 (binding:form-value b-binding)))))
  (response/output
   (lambda (out)
     (cond
       [(and a b) (fprintf out "~a + ~a = ~a\n" a b (+ a b))]
       [else (fprintf out "请提供 ?a=数字&b=数字 两个 query 参数\n")]))))

(define (not-found-handler req)
  (response/output
   #:code 404
   (lambda (out) (display "404 Not Found\n" out))))

;; --- 2. URL dispatcher ---------------------------------------
(define-values (dispatch route-url)
  (dispatch-rules
   [("") home-handler]
   [("hello" (string-arg)) hello-handler]
   [("add") add-handler]
   [else not-found-handler]))

;; --- 3. 在后台 thread 起 server，主线程做 self-test -----------

(define (run-self-test port)
  ;; 用 net/http-client 发起本地 GET 请求，验证 server 工作
  (define (fetch path)
    (define-values (status headers body-port)
      (http-sendrecv "127.0.0.1" path #:port port))
    (define body (port->string body-port))
    (close-input-port body-port)
    (values status (string-trim body))))

  (printf "~n--- self-test ---~n")
  (define-values (s1 b1) (fetch "/"))
  (printf "  GET /            → ~a  body: ~s~n" s1 b1)
  (define-values (s2 b2) (fetch "/hello/Alice"))
  (printf "  GET /hello/Alice → ~a  body: ~s~n" s2 b2)
  (define-values (s3 b3) (fetch "/add?a=3&b=4"))
  (printf "  GET /add?a=3&b=4 → ~a  body: ~s~n" s3 b3)
  (define-values (s4 b4) (fetch "/no-such-path"))
  (printf "  GET /no-such-path → ~a  body: ~s~n" s4 b4))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 12: web-server =====~n")
  (define port 8765)
  (printf "~n在 127.0.0.1:~a 起一个 web-server，自测后自动退出...~n" port)

  ;; serve/servlet 默认会阻塞主线程，我们放后台
  (define server-thread
    (thread (lambda ()
              (serve/servlet dispatch
                             #:port port
                             #:listen-ip "127.0.0.1"
                             #:command-line? #t              ; 不要打开浏览器
                             #:servlet-regexp #rx""          ; 所有路径都给 dispatch
                             #:launch-browser? #f
                             #:banner? #f))))

  ;; 等 server 起来
  (sleep 0.5)

  ;; 自测
  (with-handlers ([exn:fail? (lambda (e)
    (printf "  ❌ self-test 失败: ~a~n" (exn-message e)))])
    (run-self-test port))

  ;; 干净退出
  (kill-thread server-thread)
  (sleep 0.2)

  (printf "~n--- 心智模型 ---~n")
  (printf "  Racket web-server 是函数式 web 框架的"教科书"实现：~n")
  (printf "  - request → response 是纯函数（dispatch 中的 handler）~n")
  (printf "  - URL 解析用 dispatch-rules（声明式路由 DSL）~n")
  (printf "  - 想做 \"continuation web\" 的 web app（demo 09 的延续~n")
  (printf "    跨 HTTP 请求保留），这是 Racket 的另一个独门绝技~n")
  (printf "    （send/suspend、send/forward）~n")

  (printf "~n✅ Demo 12 完成~n"))
