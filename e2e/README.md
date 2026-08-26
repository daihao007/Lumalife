# LumaLife API E2E

这是与 Spring MockMvc 集成测试分离的真实 HTTP 黑盒 E2E 入口。运行器覆盖 CR-04～CR-06 的三条跨角色业务链，并输出可读报告：

- `reports/e2e-report.json`：机器可读结果和失败详情；
- `reports/e2e-report.xml`：JUnit XML，可被 CI 或测试平台读取；
- `reports/e2e-summary.md`：审核人员可直接阅读的摘要。

## 运行

在仓库根目录执行：

```powershell
cd e2e
npm test
```

默认行为是先探测 `http://localhost:8080/actuator/health`：如果后端已运行则复用；否则通过 `mvn spring-boot:run` 启动临时后端，并使用空状态文件保证每次运行从种子数据开始。

可用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `E2E_BASE_URL` | `http://localhost:8080` | 被测后端地址 |
| `E2E_START_BACKEND` | `1` | 设为 `0` 时只连接已有后端 |
| `E2E_TIMEOUT_MS` | `30000` | 启动和 HTTP 请求超时 |
| `E2E_REPORT_DIR` | `e2e/reports` | 报告输出目录 |

测试账号使用每次运行生成的唯一用户名；种子账号仅用于跨角色校验。失败时报告会记录场景、接口、期望/实际状态和响应摘要，便于复现。
