# D07/D08 MySQL 与 Kubernetes 闭环记录（2026-08-28）

## 结论

- D07：Compose/Kubernetes 已不再只验证 Schema。后端使用 `JdbcBusinessStateRepository` 将全部可变业务状态保存到 MySQL，并能在后端重启后恢复登录和业务数据。
- D08：仓库已包含可运行的 MySQL StatefulSet、PVC、前后端工作负载与探针；`main` 未配置 `KUBE_CONFIG_BASE64` 时由 Windows 自托管 Runner 自动创建或复用本机 Kind 集群，不再直接失败。

## 实现证据

1. V003 创建 `business_state` JSON 聚合存储；数据库校验要求 V001～V003 共 3 个迁移和 19 张表。
2. Compose 和 Kubernetes 注入 `LUMALIFE_PERSISTENCE=mysql`，Actuator 的 MySQL HealthIndicator 会实际读取 Repository。
3. Kubernetes 部署脚本创建 MySQL Secret 和初始化 ConfigMap，等待 StatefulSet、Backend、Frontend 后执行三条集群内 HTTP 健康检查。
4. 聚合快照目前采用单写者模型，因此后端固定为一个副本；这避免多实例内存状态相互覆盖。关系表级 Repository 与水平扩容属于后续架构演进，不影响本次持久化闭环。

## 本地验收结果

- 后端测试：76 个通过，0 失败。
- 原生 MySQL 8.0.45：应用 V001～V003，注册新用户，确认 `business_state` 写入，强制重启后端后登录成功。
- Docker Compose：MySQL、Backend、Frontend 全部 healthy；重启 Backend 后状态仍可读取。
- Kind v0.32.0 / Kubernetes v1.36.1：MySQL StatefulSet、Backend、Frontend 全部 Ready；后端 readiness、前端 `/healthz`、前端代理后的后端 readiness 均通过。

## 完成边界

当前实现解决了“业务仅存在进程内、重启即丢失”和“没有 kubeconfig 导致主线部署直接失败”两项验收问题。未配置 kubeconfig 时部署到自托管 Runner 上的本机 Kind；若课程后续要求公网长期部署，仍需提供真实集群的 `KUBE_CONFIG_BASE64`。MySQL 当前保存完整业务聚合快照，而不是把每个领域操作都改写成关系表事务。
