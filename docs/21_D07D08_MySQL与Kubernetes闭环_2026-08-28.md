# D07/D08 MySQL 与 Kubernetes 闭环记录（2026-08-28）

## 结论

- D07：Compose/Kubernetes 已不再只验证 Schema。后端使用 `JdbcBusinessStateRepository` 将全部可变业务状态保存到 MySQL，并能在后端重启后恢复登录和业务数据。
- D08：仓库已包含可运行的 MySQL StatefulSet、PVC、前后端工作负载与探针；`main` 未配置 `KUBE_CONFIG_BASE64` 时由 Windows 自托管 Runner 自动创建或复用本机 Kind 集群，不再直接失败。

## 实现证据

1. V001 创建业务关系表，V003 的 `business_state` 仅作为旧部署兼容入口；发现旧快照时自动导入关系表并清除活动快照。
2. Compose 和 Kubernetes 注入 `LUMALIFE_PERSISTENCE=mysql`，Actuator 的 MySQL HealthIndicator 会实际读取 Repository。
3. Kubernetes 部署脚本创建 MySQL Secret 和初始化 ConfigMap，等待 StatefulSet、Backend、Frontend 后执行三条集群内 HTTP 健康检查。
4. 关系表同步采用事务和 MySQL 命名写锁；领域聚合目前仍是单写者模型，因此后端固定为一个副本。按请求 Repository 与水平扩容属于后续服务化演进。

## 本地验收结果

- 后端测试：76 个通过，0 失败。
- 原生 MySQL：应用 V001～V004，通过 API 写入购物车，确认 `cart_item` 已写入且 `business_state` 为空，强制重启后端后业务数据仍可读取。
- Docker Compose：MySQL、Backend、Frontend 全部 healthy；重启 Backend 后状态仍可读取。
- Kind v0.32.0 / Kubernetes v1.36.1：MySQL StatefulSet、Backend、Frontend 全部 Ready；后端 readiness、前端 `/healthz`、前端代理后的后端 readiness 均通过。

## 完成边界

当前实现解决了“业务仅存在进程内、MySQL 只验证 Schema”和“没有 kubeconfig 导致主线部署直接失败”两项验收问题。未配置 kubeconfig 时部署到自托管 Runner 上的本机 Kind；若课程后续要求公网长期部署，仍需提供真实集群的 `KUBE_CONFIG_BASE64`。MySQL 当前使用规范化业务表承载稳态数据，V003 JSON 表只承担旧数据升级兼容。
