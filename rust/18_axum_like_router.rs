/// Rust 函数式编程 Demo 18: 手写迷你 axum —— HTTP 路由 & 中间件的心智模型
///
/// 真实项目用 `axum` / `actix-web` / `rocket` 起 HTTP 服务，
/// 它们的核心其实都可以抽象成两个概念：
///
///   - Handler:   Request -> Response
///   - Middleware: Handler -> Handler  (装饰器 / 组合子)
///
/// 这个 Demo 用零依赖搭一个最小路由器，演示 axum 的核心思想：
///   1) 把路由当作不可变数据结构, 用纯函数注册
///   2) 中间件就是 Handler 的 endomorphism (Handler -> Handler)
///   3) extractor 思路: 从 Request 按类型提取 body / path / query
///
/// 编译运行:
///   rustc 18_axum_like_router.rs -O -o /tmp/demo18 && /tmp/demo18

use std::collections::HashMap;
use std::sync::Arc;

// ============================================================
// 1) 请求 / 响应数据模型
// ============================================================
#[derive(Debug, Clone)]
struct Request {
    method: String,
    path: String,
    query: HashMap<String, String>,
    body: String,
    headers: HashMap<String, String>,
}

#[derive(Debug, Clone)]
struct Response {
    status: u16,
    body: String,
}

impl Response {
    fn ok(body: impl Into<String>) -> Self {
        Response { status: 200, body: body.into() }
    }
    fn not_found() -> Self {
        Response { status: 404, body: "not found".into() }
    }
    fn bad_request(msg: impl Into<String>) -> Self {
        Response { status: 400, body: msg.into() }
    }
}

// ============================================================
// 2) Handler trait —— 任意 Fn(Request) -> Response 自动实现
// ============================================================
type Handler = Arc<dyn Fn(Request) -> Response + Send + Sync + 'static>;

fn handler<F>(f: F) -> Handler
where
    F: Fn(Request) -> Response + Send + Sync + 'static,
{
    Arc::new(f)
}

// ============================================================
// 3) 路由器 —— 按 (method, path) 找 handler
// ============================================================
#[derive(Clone)]
struct Router {
    routes: HashMap<(String, String), Handler>,
    middleware: Vec<Arc<dyn Fn(Handler) -> Handler + Send + Sync + 'static>>,
}

impl Router {
    fn new() -> Self {
        Router { routes: HashMap::new(), middleware: Vec::new() }
    }

    fn route(mut self, method: &str, path: &str, h: Handler) -> Self {
        self.routes.insert((method.into(), path.into()), h);
        self
    }

    /// 中间件：Handler -> Handler
    fn layer<M>(mut self, m: M) -> Self
    where
        M: Fn(Handler) -> Handler + Send + Sync + 'static,
    {
        self.middleware.push(Arc::new(m));
        self
    }

    fn dispatch(&self, req: Request) -> Response {
        let key = (req.method.clone(), req.path.clone());
        match self.routes.get(&key) {
            None => Response::not_found(),
            Some(h) => {
                // 从里向外叠中间件：最后 push 的最靠外
                let mut final_h: Handler = h.clone();
                for m in self.middleware.iter().rev() {
                    final_h = m(final_h);
                }
                final_h(req)
            }
        }
    }
}

// ============================================================
// 4) 常用中间件
// ============================================================
fn logger_middleware(next: Handler) -> Handler {
    handler(move |req| {
        println!("  [log] -> {} {} body.len={}", req.method, req.path, req.body.len());
        let resp = next(req);
        println!("  [log] <- {}", resp.status);
        resp
    })
}

fn auth_middleware(next: Handler) -> Handler {
    handler(move |req| {
        match req.headers.get("x-token") {
            Some(t) if t == "secret" => next(req),
            _ => Response { status: 401, body: "unauthorized".into() },
        }
    })
}

fn cors_middleware(next: Handler) -> Handler {
    handler(move |req| {
        let mut resp = next(req);
        // 简化处理：在 body 加一行表示加了 CORS 头
        resp.body.push_str("\n[+CORS]");
        resp
    })
}

// ============================================================
// 5) 业务 handler
// ============================================================
fn hello_handler(req: Request) -> Response {
    let name = req.query.get("name").cloned().unwrap_or_else(|| "world".into());
    Response::ok(format!("Hello, {}!", name))
}

fn echo_handler(req: Request) -> Response {
    Response::ok(format!("you said: {}", req.body))
}

fn divide_handler(req: Request) -> Response {
    let a = req.query.get("a").and_then(|s| s.parse::<i64>().ok());
    let b = req.query.get("b").and_then(|s| s.parse::<i64>().ok());
    match (a, b) {
        (Some(_), Some(0))       => Response::bad_request("division by zero"),
        (Some(a), Some(b))       => Response::ok(format!("{}/{} = {}", a, b, a / b)),
        _                        => Response::bad_request("missing a/b in query"),
    }
}

fn build_router() -> Router {
    Router::new()
        .route("GET",  "/hello",  handler(hello_handler))
        .route("POST", "/echo",   handler(echo_handler))
        .route("GET",  "/div",    handler(divide_handler))
        // 中间件顺序：logger -> cors -> auth -> handler
        .layer(auth_middleware)
        .layer(cors_middleware)
        .layer(logger_middleware)
}

fn main() {
    println!("=== Rust Demo 18: axum 风格 HTTP 路由 & 中间件 ===\n");

    let app = build_router();

    let mk_req = |method: &str, path: &str, qs: &[(&str, &str)], token: Option<&str>, body: &str| {
        let mut q = HashMap::new();
        for (k, v) in qs { q.insert(k.to_string(), v.to_string()); }
        let mut h = HashMap::new();
        if let Some(t) = token { h.insert("x-token".into(), t.into()); }
        Request {
            method: method.into(),
            path: path.into(),
            query: q,
            body: body.into(),
            headers: h,
        }
    };

    // --- 正常请求 ---
    println!("-- GET /hello?name=alice (带 token) --");
    println!("  resp = {:?}\n", app.dispatch(mk_req("GET", "/hello", &[("name", "alice")], Some("secret"), "")));

    println!("-- POST /echo (带 token) --");
    println!("  resp = {:?}\n", app.dispatch(mk_req("POST", "/echo", &[], Some("secret"), "ping")));

    println!("-- GET /div?a=10&b=3 --");
    println!("  resp = {:?}\n", app.dispatch(mk_req("GET", "/div", &[("a", "10"), ("b", "3")], Some("secret"), "")));

    println!("-- GET /div?a=10&b=0 (业务错误) --");
    println!("  resp = {:?}\n", app.dispatch(mk_req("GET", "/div", &[("a", "10"), ("b", "0")], Some("secret"), "")));

    // --- 未授权 ---
    println!("-- GET /hello 无 token => 401 --");
    println!("  resp = {:?}\n", app.dispatch(mk_req("GET", "/hello", &[], None, "")));

    // --- 未知路由 ---
    println!("-- GET /nope => 404 --");
    println!("  resp = {:?}\n", app.dispatch(mk_req("GET", "/nope", &[], Some("secret"), "")));

    println!("=== 对照真实 axum ===");
    println!("  use axum::{{Router, routing::get}};");
    println!("  let app = Router::new()");
    println!("      .route(\"/hello\", get(hello))");
    println!("      .layer(TraceLayer::new_for_http())");
    println!("      .layer(CorsLayer::permissive());");
    println!("  axum::serve(listener, app).await?;");
    println!("  思路完全一致: Router = (path -> Handler) map, layer 堆叠中间件");
    println!("  axum 额外贡献: tower::Service 抽象 + extractor trait 按类型自动提取请求字段");
}
