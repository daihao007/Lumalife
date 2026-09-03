# Diagram Inventory and Scope

当前生产微服务总图以 [`final/current-architecture.mmd`](final/current-architecture.mmd) 为准。

- `d03/` 与 `assets/d03/`：UC01~UC03 的 `monolith-start` 三层模型基线；历史 `CR-01~03` 已映射为正式 `UC01~03`，不代表 `prod,remote` 的服务调用链。
- `d04/` 与 `assets/d04/`：UC04~UC09 的单体兼容层三层模型基线；其中 `DemoStore`、`AssistantService` 等类仍存在，但默认生产请求会通过 Remote Port 调用业务微服务。
- 当前微服务服务边界、数据归属和通信分别由 `architecture/microservices-inventory.md`、`data-ownership.md`、`service-communication.md` 校准。

27 张历史三层模型及 27 个 SVG 已于 2026-09-03 机械迁移到 `UCxx-{SYS|COMP|OBJ}-SEQxx`，对应图标识统一为 `REQxx / UCxx / SYS-SEQxx / COMP-SEQxx / OBJ-SEQxx`。迁移仅改编号和链接，没有把历史单体内容重写为微服务。答辩时不得把这些图称为当前生产微服务顺序图；需要展示当前架构时使用 `final/current-architecture.mmd`。
