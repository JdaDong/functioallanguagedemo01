# Demo 27 — Transit：跨语言高性能数据格式

第四个**项目档子目录**（依赖：`com.cognitect/transit-clj`）。

## 运行

```bash
cd clojure/27_transit_format
clojure -M:run
```

## 内容

- 三种 wire format：`:json` / `:json-verbose` / `:msgpack`
- 富类型保真：keyword / set / uuid / instant / ratio
- **缓存压缩**：重复 key/value 自动引用（实测 100 条同结构记录省 ~10-15% 体积，msgpack 比 verbose 省 ~46%）
- `write-handler` / `read-handler`：自定义类型
- Transit vs EDN vs JSON 选型对照

## Transit / EDN / JSON 何时用谁

| 格式 | 主战场 |
|---|---|
| **EDN** | Clojure 内部、配置文件、调试日志 |
| **Transit** | 跨语言（浏览器/移动端 ↔ Clojure 后端）；Datomic Cloud；re-frame-http-fx |
| **JSON** | 对接外部第三方 API（无选择余地） |
