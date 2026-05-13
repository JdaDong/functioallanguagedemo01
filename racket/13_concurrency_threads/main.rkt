#lang racket
;; ============================================================
;; Demo 13: 并发 —— thread + channel + sync (CSP 风格)
;; ------------------------------------------------------------
;; Racket 内置三件套：
;;   thread       OS 线程上的轻量绿色线程（"green thread"）
;;   channel      sync/sync-able 通信原语（CSP 风格）
;;   sync         一等公民同步（select 多个事件，谁先就绪用谁）
;;
;; 与其它语言对照：
;;   Erlang/Elixir    Actor + mailbox（每个进程一个隐式邮箱）
;;   Clojure          core.async + chan/<!/>!（同 Racket 思路）
;;   Go               goroutine + channel（语法层支持）
;;   Racket           thread + channel + 一等公民 sync (event<%>)
;;
;; 本 demo 涵盖：
;;   1. thread + thread-wait
;;   2. channel + sync 单点
;;   3. sync 多事件（producer/consumer）
;;   4. sync/timeout（超时控制）
;;   5. channel 实现 ping-pong 协程（与 demo 09 协程对照）
;; ============================================================

;; --- 1. thread + thread-wait -----------------------------------
(define (compute-square n)
  (thread (lambda ()
            (sleep 0.1)              ; 模拟工作
            (printf "  worker: ~a^2 = ~a~n" n (* n n)))))

;; --- 2. channel + 单生产单消费 ---------------------------------
(define (single-producer-consumer)
  (define ch (make-channel))
  (thread (lambda ()
            (for ([i (in-range 5)])
              (channel-put ch i))
            (channel-put ch 'done)))
  (let loop ([results '()])
    (define v (channel-get ch))
    (cond
      [(eq? v 'done) (reverse results)]
      [else (loop (cons v results))])))

;; --- 3. sync 多事件（"select"） --------------------------------
;; 同时等多个 channel，谁先有数据就用谁
(define (multi-source-consumer)
  (define fast (make-channel))
  (define slow (make-channel))

  (thread (lambda ()
            (for ([i (in-range 3)])
              (sleep 0.05)
              (channel-put fast (list 'fast i)))))
  (thread (lambda ()
            (for ([i (in-range 3)])
              (sleep 0.15)
              (channel-put slow (list 'slow i)))))

  (define results '())
  (for ([_ (in-range 6)])
    ;; sync 接受任意多个 evt，谁就绪先用谁
    (define v (sync fast slow))
    (set! results (cons v results)))
  (reverse results))

;; --- 4. sync/timeout 超时控制 ---------------------------------
(define (wait-with-timeout)
  (define ch (make-channel))
  (thread (lambda ()
            (sleep 0.5)
            (channel-put ch '太晚了)))
  ;; 100ms 内拿不到就超时
  (sync/timeout 0.1 ch))

;; --- 5. ping-pong 用 channel 实现 ------------------------------
;; 与 demo 09 的 call/cc 协程对照：channel 才是"真"并发，
;; call/cc 是"模拟"协程
(define (ping-pong-channel rounds)
  (define ping-ch (make-channel))
  (define pong-ch (make-channel))
  (define output '())
  (define out-mutex (make-semaphore 1))
  (define (push! x)
    (call-with-semaphore out-mutex
      (lambda () (set! output (cons x output)))))

  (define ping-thread
    (thread (lambda ()
              (for ([i (in-range rounds)])
                (push! `(ping ,i))
                (channel-put pong-ch 'go)
                (channel-get ping-ch)))))

  (define pong-thread
    (thread (lambda ()
              (for ([i (in-range rounds)])
                (channel-get pong-ch)
                (push! `(pong ,i))
                (channel-put ping-ch 'go)))))

  (thread-wait ping-thread)
  (thread-wait pong-thread)
  (reverse output))

;; --- 6. 实战：worker pool ---------------------------------------
;; N 个 worker thread 从同一个 input channel 拉任务
(define (run-worker-pool n-workers tasks)
  (define input-ch (make-channel))
  (define result-ch (make-channel))

  ;; 启 N 个 worker
  (for ([id (in-range n-workers)])
    (thread (lambda ()
              (let loop ()
                (define task (channel-get input-ch))
                (cond
                  [(eq? task 'done)
                   (channel-put input-ch 'done)]      ; 转发让其它 worker 也退出
                  [else
                   (sleep 0.01)                        ; 模拟工作
                   (channel-put result-ch
                                (list 'worker id 'task task '-> (* task task)))
                   (loop)])))))

  ;; 喂 task
  (thread (lambda ()
            (for ([t (in-list tasks)])
              (channel-put input-ch t))
            (channel-put input-ch 'done)))

  ;; 收结果
  (for/list ([_ (in-list tasks)])
    (channel-get result-ch)))

;; --- main ------------------------------------------------------
(module+ main
  (printf "===== Demo 13: thread + channel + sync =====~n")

  (printf "~n--- 1. thread 基础 ---~n")
  (define ts (for/list ([i '(2 3 5 7 11)]) (compute-square i)))
  (for ([t ts]) (thread-wait t))
  (printf "  所有 worker 完成~n")

  (printf "~n--- 2. 单生产单消费 ---~n")
  (printf "  channel 取出: ~a~n" (single-producer-consumer))

  (printf "~n--- 3. sync 多事件（select 风格） ---~n")
  (printf "  fast/slow 交错: ~a~n" (multi-source-consumer))
  (printf "  ↑ sync 实现了\"谁先到就用谁\"的语义，等价于 Go 的 select~n")

  (printf "~n--- 4. sync/timeout ---~n")
  (printf "  100ms 超时（task 要 500ms）: ~a (#f 表示超时)~n"
          (wait-with-timeout))

  (printf "~n--- 5. ping-pong via channel ---~n")
  (printf "  ~a~n" (ping-pong-channel 3))

  (printf "~n--- 6. worker pool（4 worker，10 task） ---~n")
  (define results (run-worker-pool 4 (build-list 10 add1)))
  (for ([r results]) (printf "  ~a~n" r))

  (printf "~n--- 心智模型 ---~n")
  (printf "  Racket = thread + channel + 一等公民 sync~n")
  (printf "  与 Clojure core.async 几乎一一对应（chan/go/<! ↔ channel/thread/channel-get）~n")
  (printf "  与 Erlang/Elixir 不同：Racket 没有内置邮箱，要显式 channel~n")
  (printf "  与 Go 不同：sync 是一等公民值，可以装进数据结构、传函数~n")

  (printf "~n✅ Demo 13 完成~n"))
