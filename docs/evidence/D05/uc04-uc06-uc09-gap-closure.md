# UC04/UC06/UC09 中期缺口闭环证据

## 复验范围

本记录补齐中期审查提出的两组问题：商家履约/券码异常的页面级错误可见性，以及管理员看板对真实 Spring Boot Actuator 健康状态的接入。

## UC04/UC06 页面级异常

正式商家入口为 `frontend/src/pages/MerchantOrders.tsx`。履约流转和券码核销失败都会写入页面内 `role="alert"` 节点 `merchant-orders-error`，同时保留全局操作提示；用户修改券码后错误会清除，失败操作也会解除按钮锁定，允许恢复重试。

前端测试 `frontend/src/pages/OrderInteractions.test.tsx` 覆盖以下页面证据：

| 场景 | 页面断言 |
| --- | --- |
| 重复/非法履约流转 | 页面显示 `非法订单状态流转`，履约按钮恢复可操作 |
| 重复核销 | 页面显示 `券码不可重复核销`，原券码输入保持不变 |
| 非法券码格式 | 12 位数字校验在请求前阻止非法输入 |
| 跨商家核销 | 页面显示 `不能核销其他商家的券码`，输入保持不变 |

## UC09 Actuator 健康链路

`AdminDashboardService` 通过 Spring Boot `HealthEndpoint.health()` 读取实时健康组件状态，覆盖指标服务原来的固定 `UP` 字段，并返回 `health.source=/actuator/health` 作为来源标识；业务计算的待处理订单数量仍由指标服务提供。管理员页面显示为 `UP · Actuator`（异常状态时按 Actuator 返回值显示）。

后端集成测试先请求 `/actuator/health`，再请求管理员 `/api/v1/admin/metrics`，断言两者的 `status` 一致，并断言来源标识为 `/actuator/health`。

## 本次复验结果

```text
cd frontend && npm.cmd test -- --run
4 files passed; 25 tests passed

cd backend && mvn.cmd -B -ntp test
74 tests passed; 0 failures; 0 errors; BUILD SUCCESS
```

对应测试定位：

- `MerchantOrders` 页面异常：`shows a page-level error when a stale fulfillment action is rejected`、`validates coupon format before verification and reports API errors`、`shows a page-level error when another merchant's coupon is rejected`。
- Actuator 与看板一致性：`ApiSecurityIntegrationTest.platformAdminCanAccessMetrics`（真实 Spring 上下文端点集成断言）。
