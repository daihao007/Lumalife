# D06 / Issue #43 用户认证业务服务拆分证据

日期：2026-09-01（Asia/Shanghai）  
Issue：[#43](https://github.com/daihao007/Lumalife/issues/43)  
分支：`codex/issue-43-identity-service`  
当前合并基线：`main@97c399e`

## 实现范围

本次将认证相关能力收敛到独立的 `identity-service`，并保留单体 BFF 的外部 `/api/v1/**` 兼容接口：

- 独立 Spring Boot 入口、独立构建和 Actuator `health/liveness/readiness` 探针。
- 服务自有账号、密码哈希、角色、access session、用户地址状态；BFF 不直接读写这些状态。
- 登录、注册、当前用户、token introspection、资料更新和地址增删改查/默认设置内部接口。
- service token、请求 ID、traceparent、调用方服务名的内部调用边界；用户资料和地址写操作校验 `X-User-Id` 归属。
- access token 只以 SHA-256 哈希持久化，带可配置 TTL；状态文件通过临时文件和原子替换写入。
- 可选的一次性旧单体快照回填：目标状态不存在且配置了 `LUMALIFE_IDENTITY_BACKFILL_SOURCE_FILE` 时导入账号和地址；不复制订单、商品或商家经营数据。
- Docker Compose 的独立数据卷、Kubernetes PVC，以及后端 `RemoteIdentityServicePort` 的远程路由和回滚闸门。

商家经营资料、商品、库存、订单、支付和评价不在本 Issue 的身份服务所有权内；商家注册返回的 `merchantId` 仍是身份账号绑定能力，经营资料迁移属于后续服务任务。

## PR 审核修复

- 默认关闭身份远程路由和 backfill 完成标记，避免空 PVC 上线后接管已有账号；切换前必须显式提供并验证历史快照。
- 下单工作流先从 `IdentityServicePort` 解析归属地址，再向本地或远程订单 Port 传递不可变地址快照；order-service 校验用户/地址一致性并持久化 `address_snapshot`。
- identity-service Pod 使用 UID/GID/fsGroup `10001` 挂载 PVC；短请求 ID 由 BFF 重新生成。
- 商家昵称同时更新经营资料与身份资料，身份写入失败时回滚经营昵称；任意未占用地址 ID 不再被解释为新增地址。

## 验收映射

| Issue 验收项 | 本次证据 |
| --- | --- |
| 服务可独立构建/测试 | `services/identity-service/pom.xml` 独立 `verify`；测试覆盖 HTTP 健康探针、登录、注册、token、资料/地址边界和回填重启 |
| 身份数据归属清晰 | `IdentityStore` 仅维护用户、会话和地址；服务状态文件使用 `users`、`addresses`、`sessions`，不保存原始 token |
| 前端经网关访问 | `RemoteIdentityServicePort` 是 BFF 唯一远程适配入口，保留 `/api/v1/**`；远程流量由 backfill 完成标记控制 |
| 契约与健康检查 | `docs/contracts/identity-service.openapi.yaml`、`services/README.md`、内部统一错误 envelope、Actuator 探针和本页测试命令 |

## 本次回归

已通过：

```text
mvn -B -ntp -f services/identity-service/pom.xml test
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

测试包含：

- 正常登录、注册和安全用户视图；非法角色不得创建平台管理员账号。
- 有效 token claims、伪造 token、令牌哈希落盘、TTL 过期和重启恢复。
- 用户资料/地址跨用户访问拒绝、默认地址唯一、每用户最多 5 条、删除后的默认地址修复、地址快照读取。
- 旧单体账号/地址快照回填，并确认持久化内容不包含原始 bearer token。
- 缺失服务令牌和缺失/非法调用链元数据均返回统一 `{code,message,data,requestId,reason,details}` 错误结构。

## 切换与回滚

Compose/Kubernetes 将 `LUMALIFE_IDENTITY_REMOTE_ENABLED` 和 `LUMALIFE_IDENTITY_BACKFILL_COMPLETED` 默认设为 `false`。启用 BFF 远程身份调用前，必须先通过 `LUMALIFE_IDENTITY_BACKFILL_SOURCE_FILE` 挂载并导入历史账号/地址快照，核对账号与地址数量，再显式把两个开关设为 `true`；只打开远程开关时，适配器以 `IDENTITY_BACKFILL_REQUIRED` 关闭远程流量。

发生回归异常时，将 `LUMALIFE_IDENTITY_REMOTE_ENABLED=false` 或清除 backfill 完成标记即可回到单体身份 Port；身份服务自身通过保留 PVC/Compose volume 和原子状态文件写入避免重启丢失。真正切换前仍需在目标环境执行历史快照回填、双读比对和全角色 UI 回归；本证据不宣称 merchant/order 已完成迁移。
