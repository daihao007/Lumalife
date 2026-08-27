# D05 真实浏览器回归记录

## 环境与口径

- 执行时间：2026-08-26 15:55～15:58（Asia/Shanghai）。
- 前端：本地 Vite 页面；后端：本地 Spring Boot API。
- 数据隔离：使用临时状态文件 `lumalife-d05-review-20260826-1554.json`，避免既有演示数据干扰订单编号与日志。
- 执行方式：在真实浏览器中逐步点击和输入。该项为手工 UI 回归，不冒充 Playwright/Cypress 自动化。
- 自动化补充：前端组件/路由测试验证渲染和交互，后端测试验证领域与权限，`e2e/runner.mjs` 对运行中的 HTTP API 执行跨角色黑盒闭环。

## 执行时间线

| 时间 | 角色 | 操作与结果 | 截图 |
| --- | --- | --- | --- |
| 15:55:32 | 用户 | 创建外卖订单 `#1009` | `01-user-order-1009-paid.png` |
| 15:55:33 | 用户 | 支付成功，订单为 PAID | `01-user-order-1009-paid.png` |
| 15:56:00～15:56:01 | 商家 | 依次接单、开始配送、完成订单，状态为 COMPLETED | `02-merchant-order-1009-completed.png` |
| 15:56:39 | 用户 | 确认收货，状态为 RECEIVED | `03-user-order-1009-received-reviewed.png` |
| 15:56:40 | 用户 | 提交 5/5/5 评价“D05复审：外卖履约完整且准时” | `03-user-order-1009-received-reviewed.png` |
| 15:57:01 | 用户 | 创建并支付团购订单 `#1020`，页面生成券码 `100000001020` | `04-user-groupbuy-1020-paid-coupon.png` |
| 15:57:35 | 商家 | 输入完整 12 位券码，点击核销前保留页面状态 | `05-merchant-groupbuy-1020-coupon-entered.png` |
| 15:57:36 | 商家 | 核销成功，团购订单状态为 USED；页面无外卖履约按钮 | `06-merchant-groupbuy-1020-used.png` |
| 15:58 | 用户 | 提交 5/5/5 评价“D05复审：团购券码核销成功”，两单均显示已评价 | `07-user-orders-1009-1020-reviewed.png` |
| 15:58 | 管理员 | 看板显示系统健康 UP、待处理订单 0、2 笔订单、营收 ¥96.70、完成率 100%，日志含 `#1009` 履约/评价及 `#1020` 支付/核销/评价 | `08-admin-dashboard-health-audit-log.png` |

## 审计问题闭环

| 审计问题 | 整改结果 |
| --- | --- |
| 文档订单号与截图不一致 | 全部证据使用同一隔离环境，外卖固定为 `#1009`、团购固定为 `#1020` |
| 外卖链路缺少收货/评价证据 | 新增 `03-user-order-1009-received-reviewed.png` |
| 团购只展示核销结果，未证明券码生成和输入 | 新增生成券码、输入未核销、核销完成三张连续截图 |
| 团购声称已评价但截图显示未评价 | 新增 `07-user-orders-1009-1020-reviewed.png`，两单均显示已评价 |
| 管理员健康状态无页面证据 | 新增 `08-admin-dashboard-health-audit-log.png`，明确显示 `系统健康 UP` |
| 测试数量陈旧、测试类型混淆 | 更新为前端 23 项、后端 66 项、HTTP E2E 3/3，并单列真实浏览器手工回归 |

## 复现命令

```powershell
cd frontend
npm test -- --run
npm run build

cd ..\backend
mvn -B -ntp verify

cd ..\e2e
npm test
```

Windows 执行最后一项前需确保 `mvn.cmd` 已加入 `PATH`。HTTP E2E 会自行启动并停止后端，生成 `e2e/reports` 报告。
