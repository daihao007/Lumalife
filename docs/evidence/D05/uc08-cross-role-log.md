# UC08 用户—商家客服跨角色闭环证据

## 1. 验证范围

本记录对应 [Issue #97](https://github.com/daihao007/Lumalife/issues/97)，验证以下完整链路：

`普通用户发送消息 → 商家管理员读取 → 商家管理员人工回复 → 普通用户再次读取`

用户发送后系统可能返回 `MERCHANT_AI` 自动回复；本记录只把 `senderRole=MERCHANT` 的消息计为人工商家回复。

## 2. 本地执行记录

| 项目 | 值 |
| --- | --- |
| 执行时间 | 2026-08-28 15:34:10（Asia/Shanghai） |
| 环境 | Windows 11 amd64、Java 21.0.8、Maven 3.9.9、Node.js 24.16.0、npm 11.13.0 |
| 命令 | `cd e2e && npm.cmd test` |
| 后端地址 | `http://localhost:18081`，显式启动隔离后端，避免复用旧进程 |
| 退出码 | `0` |
| 总场景 | 7 |
| 通过 | 7 |
| 失败 | 0 |
| 失败原因 | 无 |

本次冲突解决以任务二合并后的最新 `main` 提交 [`94b9598b`](https://github.com/daihao007/Lumalife/commit/94b9598b28e75fff4046b222aed1a71df6c5a8fc) 为基线；任务一将在本次同步提交后重新复核。

## 3. UC08 关键结果

本次运行的 UC08 场景通过，关键记录如下：

| 步骤 | 角色 | userId | merchantId | messageId | senderRole | HTTP |
| --- | --- | ---: | ---: | ---: | --- | ---: |
| 用户发送 | USER | 1069 | 1 | 1073 | USER | 200 |
| 商家读取 | MERCHANT_ADMIN | 1069 | 1 | 1073 | USER | 200 |
| 商家人工回复 | MERCHANT_ADMIN | 1069 | 1 | 1076 | MERCHANT | 200 |
| 用户再次读取 | USER | 1069 | 1 | 1073、1076 | USER、MERCHANT | 200 |
| 商家 B 越权读取商家 A 会话 | MERCHANT_ADMIN_B | 1069 | 1→1078 | — | — | 404 |
| 商家 B 越权回复商家 A 会话 | MERCHANT_ADMIN_B | 1069 | 1→1078 | — | — | 404 |
| 用户访问商家会话接口 | USER | — | — | — | — | 403 |

消息使用唯一运行标识 `UC08-1787902450752-7`，用于确认用户消息和人工回复属于同一次会话，而不是固定种子数据。

## 4. 可复现接口链路

脚本通过以下接口完成验证：

1. `POST /api/v1/conversations/{merchantId}/messages`：用户发送消息。
2. `GET /api/v1/merchant-admin/conversations`：商家确认会话出现在列表。
3. `GET /api/v1/merchant-admin/conversations/{userId}`：商家读取用户消息。
4. `POST /api/v1/merchant-admin/conversations/{userId}/messages`：商家发送人工回复。
5. `GET /api/v1/conversations/{merchantId}`：用户再次读取并确认 `USER`、`MERCHANT` 两条消息均存在。
6. `GET /api/v1/merchant-admin/conversations/{userId}` 使用商家 B token 访问商家 A 会话：验证返回 `404`。
7. `POST /api/v1/merchant-admin/conversations/{userId}/messages` 使用商家 B token 访问商家 A 会话：验证返回 `404`，且不会创建错误归属的回复。
8. `GET /api/v1/merchant-admin/conversations` 使用普通用户 token：验证返回 `403`。

## 5. 原始证据

- 本地生成的 `e2e/reports/e2e-report.json`：包含 UC08 场景结果、账号/商家/消息 ID 和角色序列。
- 本地生成的 `e2e/reports/e2e-raw.log`：每条 HTTP 记录包含开始时间、完成时间、接口、状态码；末尾 `run-summary` 包含命令、退出码、总数、通过数、失败数和失败原因。
- 本地 E2E：7/7 通过，退出码 0；UC08 商家 B 读取/回复商家 A 会话均为 `404`。
- PR CI：待本次修订推送后更新为与 PR head 对应的成功流水线、job 和 artifact 链接。
