# Issue #39：失败注入记录

## 注入目的

验证 E2E 在外部后端不可用时能识别环境故障、返回非零退出码，并保留可供 CI 上传的诊断报告。

## 执行条件

```powershell
$env:E2E_START_BACKEND = "0"
$env:E2E_BASE_URL = "http://127.0.0.1:65530"
$env:E2E_TIMEOUT_MS = "500"
$env:E2E_REPORT_DIR = "e2e/reports/d05-failure-check"
npm.cmd --prefix e2e test
```

## 实际结果

本地执行时间：2026-08-27 15:04:46（Asia/Shanghai）。

```text
E2E 0/0 passed; environment: failed; report: e2e\reports\d05-failure-check
process exit code: 1
reports: e2e-report.json, e2e-report.xml, e2e-summary.md, e2e-raw.log
```

统计：总场景 `0`，通过 `0`，失败断言 `0`；失败原因为环境启动失败：`backend is not healthy and E2E_START_BACKEND=0`。结论：故障注入符合预期。运行器未执行业务场景，报告四件套均生成，退出码为 `1`，能够阻断质量门禁并保留环境诊断信息。对应 [CI job](https://github.com/daihao007/Lumalife/actions/runs/33048180760/job/98437032218)、[CI 原始报告 artifact](https://github.com/daihao007/Lumalife/actions/runs/33048180760/artifacts/9636470831)。该记录对应 [Issue #39](https://github.com/daihao007/Lumalife/issues/39)。
