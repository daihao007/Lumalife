# LumaLife API E2E

这是与 Spring MockMvc 集成测试分离的真实 HTTP 黑盒 E2E 入口。运行器覆盖 CR-01～CR-06 的六条代表性业务链（不包含骑手模块），并输出可读报告：

- `reports/e2e-report.json`：机器可读结果和失败详情；
- `reports/e2e-report.xml`：JUnit XML，可被 CI 或测试平台读取；
- `reports/e2e-summary.md`：审核人员可直接阅读的摘要；
- `reports/e2e-raw.log`：每次 HTTP 请求的原始状态、耗时和响应摘要。

## 运行

在仓库根目录执行：

```powershell
cd e2e
npm test
```

默认行为是始终通过 `mvn spring-boot:run` 在独立端口启动隔离的临时后端，并使用空状态文件保证每次运行从种子数据开始；运行结束后会清理该进程。为避免状态污染，默认不会复用已经运行的后端。若确实需要连接外部环境，可显式设置 `E2E_START_BACKEND=0`。

可用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `E2E_BASE_URL` | `http://localhost:18080` | 被测后端地址 |
| `E2E_START_BACKEND` | `1` | 默认启动隔离后端；设为 `0` 时才连接已有后端 |
| `E2E_BACKEND_PORT` | `18080` | 隔离后端启动端口；仅自动启动后端时使用 |
| `E2E_TIMEOUT_MS` | `30000` | 启动和 HTTP 请求超时 |
| `E2E_REPORT_DIR` | `e2e/reports` | 报告输出目录 |

测试账号使用每次运行生成的唯一用户名；种子账号仅用于跨角色校验。失败时报告会记录场景、接口、期望/实际状态和响应摘要，便于复现。若后端启动失败，仍会生成四种报告，并在环境字段中记录启动错误和后端日志。

## UC08 跨角色客服闭环

`runner.mjs` also executes a dedicated UC08 scenario after CR-01~CR-06. It records the user-send, merchant-read, merchant-human-reply and user-re-read steps, including actor IDs, merchant ID, message IDs, sender roles, timestamps, HTTP statuses and the user-to-merchant authorization boundary. The generated `e2e-report.json` and `e2e-raw.log` are uploaded by CI with the API E2E artifact.
