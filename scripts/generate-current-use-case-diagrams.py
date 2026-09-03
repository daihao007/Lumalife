#!/usr/bin/env python3
"""Generate current prod,remote three-layer use-case diagrams.

The source facts are intentionally kept at the service-boundary level documented in
docs/13_概要设计说明书.md and docs/14_详细设计说明书.md.  This generator writes
editable Mermaid sources; SVG export is performed by the Mermaid CLI in the audit
workflow so the source and rendered artifacts stay paired.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MMD_DIR = ROOT / "docs/diagrams/final/use-cases"
SVG_DIR = ROOT / "docs/assets/final/use-cases"


USE_CASES = [
    {
        "id": "UC01",
        "title": "账号、资料与地址",
        "actor": "用户",
        "services": "identity-service",
        "system": """flowchart LR
    U[用户]
    BFF[backend BFF\\n52 public APIs]
    ID[identity-service\\n13 internal APIs]
    DB[(identity DB\\n3 tables)]
    U -->|注册 / 登录 / 资料 / 地址| BFF
    BFF -->|internal HTTP + service token| ID
    ID -->|事务读写| DB
    ID -->|token + user summary| BFF
    BFF --> U
""",
        "component": """sequenceDiagram
    actor U as 用户
    participant UI as frontend
    participant B as backend BFF
    participant I as identity-service
    participant D as identity DB
    U->>UI: 注册、登录或维护资料/地址
    UI->>B: /api/v1/auth、/user、/user/addresses
    B->>I: internal HTTP + service token
    I->>D: 校验唯一性/身份并读写自有表
    D-->>I: 事务结果
    I-->>B: token 与用户摘要或受控错误
    B-->>UI: {code,message,data}
    UI-->>U: 展示成功或 4xx 错误
""",
        "object": """classDiagram
    class UserAccount {
      +id
      +loginIdentifier
      +role
      +status
    }
    class AuthSession {
      +tokenId
      +expiresAt
    }
    class UserAddress {
      +id
      +userId
      +isDefault
    }
    UserAccount \"1\" --> \"0..*\" AuthSession : identity-service owns
    UserAccount \"1\" --> \"0..*\" UserAddress : identity-service owns
""",
    },
    {
        "id": "UC02",
        "title": "商家发现、详情与收藏",
        "actor": "用户",
        "services": "merchant-service",
        "system": """flowchart LR
    U[用户]
    BFF[backend BFF\\nCatalog/Favorite ports]
    MER[merchant-service\\n30 internal APIs]
    DB[(merchant DB\\n10 tables)]
    U -->|搜索 / 详情 / 收藏| BFF
    BFF -->|internal HTTP + service token| MER
    MER -->|目录、库存与收藏查询| DB
    DB -->|评价只读投影| MER
    MER --> BFF
    BFF --> U
""",
        "component": """sequenceDiagram
    actor U as 用户
    participant UI as frontend
    participant B as backend BFF
    participant M as merchant-service
    participant D as merchant DB
    U->>UI: 搜索、查看详情或收藏
    UI->>B: /api/v1/categories、/merchants、/user/favorites
    B->>M: internal HTTP + service token
    M->>D: 查询目录/详情/收藏及评价投影
    D-->>M: 自有数据与只读投影
    M-->>B: 结果或受控错误
    B-->>UI: 稳定公开响应信封
    UI-->>U: 列表、详情或收藏状态
""",
        "object": """classDiagram
    class Category { +id +name }
    class Merchant { +id +name +status }
    class MerchantCatalog { +id +merchantId +price +status }
    class GroupDeal { +id +merchantId +validUntil +status }
    class MerchantFavorite { +userId +merchantId }
    class ReviewProjection { +merchantId +score +count }
    Category \"1\" --> \"0..*\" Merchant : merchant-service
    Merchant \"1\" --> \"0..*\" MerchantCatalog : owns
    Merchant \"1\" --> \"0..*\" GroupDeal : owns
    Merchant \"1\" --> \"0..*\" MerchantFavorite : referenced
    Merchant \"1\" --> \"0..1\" ReviewProjection : read-only projection
""",
    },
    {
        "id": "UC03",
        "title": "购物车、外卖下单、支付与取消",
        "actor": "用户",
        "services": "order-service + merchant-service + RabbitMQ",
        "system": """flowchart LR
    U[用户]
    BFF[backend BFF\\nCart/Order workflow ports]
    ORD[order-service\\n订单、支付、Saga]
    ODB[(order DB\\n10 tables)]
    MQ[(RabbitMQ\\nOutbox / Inbox)]
    MER[merchant-service\\n库存所有者]
    MDB[(merchant DB)]
    U -->|购物车 / 下单 / 支付 / 取消| BFF
    BFF -->|命令与商品快照| ORD
    BFF -->|商品与优惠查询| MER
    ORD --> ODB
    ORD -->|reserve request| MQ
    MQ --> MER
    MER --> MDB
    MER -->|reserve result| MQ
    MQ --> ORD
    ORD -->|状态 / 幂等结果| BFF
    BFF --> U
""",
        "component": """sequenceDiagram
    actor U as 用户
    participant UI as frontend
    participant B as backend BFF
    participant M as merchant-service
    participant O as order-service
    participant OD as order DB
    participant Q as RabbitMQ
    U->>UI: 加购并提交外卖订单/支付
    UI->>B: cart、orders/delivery、payments
    B->>M: 获取商品/优惠快照
    M-->>B: 当前可售数据
    B->>O: 创建订单与支付命令
    O->>OD: 写订单、支付、Saga、Outbox
    O->>Q: inventory.reserve.requested
    Q->>M: 幂等预留库存
    M-->>Q: inventory.reserve.result
    Q->>O: 推进 Saga 与订单状态
    O->>OD: 保存确认/拒绝或补偿状态
    O-->>B: 当前订单状态或冲突
    B-->>UI: 支付、取消或处理中结果
    alt 待支付取消
        B->>O: 取消命令
        O->>OD: 校验状态并幂等取消
    end
""",
        "object": """classDiagram
    class Cart { +userId +status }
    class CartItem { +cartId +catalogId +quantity }
    class OrderRecord { +id +userId +status +amountSnapshot }
    class Payment { +orderId +clientRequestId +status }
    class OrderSaga { +orderId +step +status }
    class InventoryReservation { +orderId +catalogId +quantity +status }
    class OutboxEvent { +eventId +type +status }
    Cart \"1\" --> \"1..*\" CartItem : order-service
    OrderRecord \"1\" --> \"0..*\" Payment : owns
    OrderRecord \"1\" --> \"1\" OrderSaga : coordinates
    OrderRecord \"1\" --> \"0..*\" InventoryReservation : requests merchant
    OrderRecord \"1\" --> \"0..*\" OutboxEvent : publishes
""",
    },
    {
        "id": "UC04",
        "title": "履约、收货与评价",
        "actor": "商家管理员 + 用户",
        "services": "order-service + merchant-service",
        "system": """flowchart LR
    M[商家管理员]
    U[用户]
    BFF[backend BFF\\nOrder admin/workflow ports]
    ORD[order-service\\n履约、收货、评价真值]
    ODB[(order DB)]
    MER[merchant-service\\n评价只读投影]
    MDB[(merchant DB)]
    M -->|本店订单 / 状态推进| BFF
    U -->|确认收货 / 一次评价| BFF
    BFF --> ORD
    ORD -->|归属与状态校验| ODB
    ORD -->|评价投影| MER
    MER --> MDB
    ORD --> BFF
    BFF --> M
    BFF --> U
""",
        "component": """sequenceDiagram
    actor M as 商家管理员
    actor U as 用户
    participant UI as frontend
    participant B as backend BFF
    participant O as order-service
    participant D as order DB
    participant R as merchant-service
    M->>UI: 查看本店订单并推进履约
    UI->>B: merchant-admin/orders 与 transition
    B->>O: internal HTTP + role/ownership context
    O->>D: 校验商家归属和相邻状态
    D-->>O: PAID -> ACCEPTED -> DELIVERING -> COMPLETED
    O-->>B: 当前订单时间线
    B-->>UI: 更新商家工作台
    U->>UI: 确认收货并提交评价
    UI->>B: orders/{id}/receive、reviews
    B->>O: 校验用户归属、完成状态和重复评价
    O->>D: 写 RECEIVED 与 Review 真值
    O->>R: 发布/更新评价只读投影
    O-->>B: 收货和评价结果
    B-->>UI: 稳定公开响应
    alt 越权、非法状态或重复评价
        O-->>B: 403/409，状态不变
    end
""",
        "object": """classDiagram
    class OrderRecord { +id +merchantId +userId +status }
    class Delivery { +orderId +status +updatedAt }
    class Review { +orderId +userId +score +content }
    class Merchant { +id +ownerUserId }
    class ReviewProjection { +merchantId +score +count }
    Merchant \"1\" --> \"0..*\" OrderRecord : merchant ownership check
    OrderRecord \"1\" --> \"0..1\" Delivery : order-service
    OrderRecord \"1\" --> \"0..1\" Review : one review after completion
    Merchant \"1\" --> \"0..1\" ReviewProjection : merchant read model
    Review --> ReviewProjection : projected after truth write
""",
    },
    {
        "id": "UC05",
        "title": "团购购买与券码",
        "actor": "用户",
        "services": "order-service + merchant-service + RabbitMQ",
        "system": """flowchart LR
    U[用户]
    BFF[backend BFF\\nOrder workflow]
    MER[merchant-service\\n团购与库存]
    MDB[(merchant DB)]
    ORD[order-service\\n支付与券码]
    ODB[(order DB)]
    MQ[(RabbitMQ\\n库存 Saga)]
    U -->|查看/购买有效团购| BFF
    BFF --> MER
    MER --> MDB
    BFF --> ORD
    ORD --> ODB
    ORD --> MQ
    MQ --> MER
    MER --> MQ
    MQ --> ORD
    ORD -->|支付成功生成唯一券码| BFF
    BFF --> U
""",
        "component": """sequenceDiagram
    actor U as 用户
    participant UI as frontend
    participant B as backend BFF
    participant M as merchant-service
    participant O as order-service
    participant D as order DB
    participant Q as RabbitMQ
    U->>UI: 选择有效团购并支付
    UI->>B: group-buy order request
    B->>M: 获取团购规则与库存快照
    M-->>B: 有效套餐或业务冲突
    B->>O: 提交团购订单与幂等支付命令
    O->>D: 写订单、支付、Saga、Outbox
    O->>Q: inventory.reserve.requested
    Q->>M: 幂等预留团购库存
    M-->>Q: reserve result
    Q->>O: 更新 Saga
    O->>D: 支付成功后生成唯一 12 位券码
    O-->>B: 订单与券码结果
    B-->>UI: 展示券码或受控错误
    Note over O,D: 相同 clientRequestId 复用原结果
""",
        "object": """classDiagram
    class GroupDeal { +id +merchantId +validUntil +status }
    class OrderRecord { +id +userId +groupDealId +status }
    class Payment { +orderId +clientRequestId +status }
    class CouponCode { +orderId +code12 +status +usedAt }
    class OrderSaga { +orderId +step +status }
    class InventoryReservation { +groupDealId +quantity +status }
    GroupDeal \"1\" --> \"0..*\" OrderRecord : merchant rule snapshot
    OrderRecord \"1\" --> \"1\" Payment : order-service
    OrderRecord \"1\" --> \"0..1\" CouponCode : generated after payment
    OrderRecord \"1\" --> \"1\" OrderSaga : coordinates inventory
    GroupDeal \"1\" --> \"0..*\" InventoryReservation : merchant owns stock
""",
    },
    {
        "id": "UC06",
        "title": "券码核销",
        "actor": "商家管理员",
        "services": "order-service",
        "system": """flowchart LR
    M[商家管理员]
    BFF[backend BFF\\nMerchant admin]
    ORD[order-service\\nCoupon verification]
    DB[(order DB\\nCouponCode)]
    M -->|提交券码| BFF
    BFF -->|角色与调用身份| ORD
    ORD -->|存在/未使用/绑定商家校验| DB
    DB -->|幂等标记已使用| ORD
    ORD --> BFF
    BFF --> M
""",
        "component": """sequenceDiagram
    actor M as 商家管理员
    participant UI as MerchantDesk
    participant B as backend BFF
    participant O as order-service
    participant D as order DB
    M->>UI: 输入本店券码并核销
    UI->>B: POST /merchant-admin/coupons/verify
    B->>O: internal HTTP + merchant identity
    O->>D: 查找券码、绑定商家和当前状态
    alt 可核销
        O->>D: 幂等更新为已使用
        D-->>O: 已核销
        O-->>B: 成功
    else 未知/他店/重复
        O-->>B: 404/403/409，保持原状态
    end
    B-->>UI: 统一响应信封
""",
        "object": """classDiagram
    class CouponCode { +code12 +orderId +merchantId +status +usedAt }
    class OrderRecord { +id +merchantId +status }
    class Merchant { +id +ownerUserId }
    class RedemptionAttempt { +code12 +actorId +result +createdAt }
    OrderRecord \"1\" --> \"0..1\" CouponCode : order-service owns
    Merchant \"1\" --> \"0..*\" CouponCode : bound merchant
    Merchant \"1\" --> \"0..*\" RedemptionAttempt : authorization context
    CouponCode \"1\" --> \"0..*\" RedemptionAttempt : idempotent attempts
""",
    },
    {
        "id": "UC07",
        "title": "商家经营内容发布",
        "actor": "商家管理员",
        "services": "merchant-service",
        "system": """flowchart LR
    M[商家管理员]
    BFF[backend BFF\\nMerchant admin]
    MER[merchant-service\\n目录/团购/库存]
    DB[(merchant DB\\n10 tables)]
    USER[用户端查询]
    M -->|新增、编辑、上下架、删除| BFF
    BFF -->|角色与归属校验| MER
    MER -->|事务读写| DB
    DB -->|发布状态即时可见| USER
    MER --> BFF
    BFF --> M
""",
        "component": """sequenceDiagram
    actor M as 商家管理员
    participant UI as MerchantProducts
    participant B as backend BFF
    participant S as merchant-service
    participant D as merchant DB
    M->>UI: 新增/编辑/上架/下架商品或团购
    UI->>B: /merchant-admin/products、group-deals
    B->>S: internal HTTP + merchant identity
    S->>D: 校验归属并写入目录/团购状态
    D-->>S: 保存结果
    S-->>B: 当前资源或业务冲突
    B-->>UI: 管理结果
    Note over S,D: 普通用户不能通过前端隐藏绕过服务层权限
""",
        "object": """classDiagram
    class Merchant { +id +ownerUserId +status }
    class MerchantCatalog { +id +merchantId +name +price +status }
    class GroupDeal { +id +merchantId +rules +status }
    class Inventory { +catalogId +availableQuantity +version }
    Merchant \"1\" --> \"0..*\" MerchantCatalog : owns
    Merchant \"1\" --> \"0..*\" GroupDeal : owns
    MerchantCatalog \"1\" --> \"0..1\" Inventory : merchant-owned stock
    Merchant \"1\" --> \"0..*\" Inventory : bounded by ownership
""",
    },
    {
        "id": "UC08",
        "title": "用户、商家与 AI 客服",
        "actor": "用户 + 商家",
        "services": "merchant-service + assistant-service",
        "system": """flowchart LR
    U[用户]
    M[商家管理员]
    BFF[backend BFF\\nAssistant port]
    MER[merchant-service\\n会话所有者]
    MDB[(merchant DB\\nchat_message)]
    AI[assistant-service\\n无业务数据库]
    AGNES[Agnes provider]
    U -->|提问 / 读历史| BFF
    M -->|读会话 / 人工回复| BFF
    BFF --> MER
    MER --> MDB
    BFF --> AI
    AI -->|HTTPS / timeout| AGNES
    AGNES --> AI
    AI -->|回答或确定性 fallback| BFF
    BFF --> MER
    BFF --> U
    BFF --> M
""",
        "component": """sequenceDiagram
    actor U as 用户
    actor M as 商家
    participant UI as frontend
    participant B as backend BFF
    participant R as merchant-service
    participant D as merchant DB
    participant A as assistant-service
    participant P as Agnes provider
    U->>UI: 向指定商家提问
    UI->>B: conversation/message request
    B->>R: 校验会话归属并保存用户消息
    R->>D: 写 chat_message
    B->>A: assistant request
    A->>P: HTTPS provider call
    alt provider 可用
        P-->>A: 生成回复
    else 密钥缺失/超时/空响应
        A-->>A: 选择确定性 fallback
    end
    A-->>B: 回复或 fallback
    B->>R: 保存 AI 消息
    R->>D: 写入会话历史
    B-->>UI: 返回完整往返
    M->>B: 读取所属会话并人工回复
    B->>R: 归属校验后写入商家消息
    R-->>B: 会话历史
    B-->>M: 展示历史与人工回复结果
""",
        "object": """classDiagram
    class Conversation { +id +userId +merchantId +status }
    class ChatMessage { +conversationId +senderType +content +createdAt }
    class UserAccount { +id +role }
    class Merchant { +id +ownerUserId }
    class AssistantRequest { +conversationId +provider +fallbackUsed }
    UserAccount \"1\" --> \"0..*\" Conversation : user ownership
    Merchant \"1\" --> \"0..*\" Conversation : merchant ownership
    Conversation \"1\" --> \"1..*\" ChatMessage : merchant-service owns
    Conversation \"1\" --> \"0..*\" AssistantRequest : assistant-service stateless
""",
    },
    {
        "id": "UC09",
        "title": "平台运营指标与健康聚合",
        "actor": "平台管理员",
        "services": "identity-service + merchant-service + order-service",
        "system": """flowchart LR
    A[平台管理员]
    BFF[backend BFF\\nMetrics aggregation]
    ID[identity-service]
    MER[merchant-service]
    ORD[order-service]
    IDDB[(identity DB)]
    MDB[(merchant DB)]
    ODB[(order DB)]
    A -->|/api/v1/admin/metrics| BFF
    BFF --> ID
    BFF --> MER
    BFF --> ORD
    ID --> IDDB
    MER --> MDB
    ORD --> ODB
    ID --> BFF
    MER --> BFF
    ORD --> BFF
    BFF -->|聚合结果 / 健康状态| A
""",
        "component": """sequenceDiagram
    actor A as 平台管理员
    participant UI as AdminDashboard
    participant B as backend BFF
    participant I as identity-service
    participant M as merchant-service
    participant O as order-service
    A->>UI: 查看运营指标与系统状态
    UI->>B: GET /api/v1/admin/metrics
    B->>B: 校验 PLATFORM_ADMIN
    par 聚合用户
        B->>I: metrics/health internal HTTP
        I-->>B: 用户与健康摘要
    and 聚合商家
        B->>M: metrics/health internal HTTP
        M-->>B: 商家与健康摘要
    and 聚合订单
        B->>O: metrics/health internal HTTP
        O-->>B: 订单与健康摘要
    end
    B-->>UI: 聚合结果或受控依赖错误
    UI-->>A: 看板展示
""",
        "object": """classDiagram
    class MetricsSnapshot { +capturedAt +users +merchants +orders +amount }
    class HealthSnapshot { +service +status +details }
    class UserAccount { +id +role +status }
    class Merchant { +id +status }
    class OrderRecord { +id +status +amount }
    MetricsSnapshot o-- UserAccount : identity aggregate
    MetricsSnapshot o-- Merchant : merchant aggregate
    MetricsSnapshot o-- OrderRecord : order aggregate
    MetricsSnapshot --> HealthSnapshot : BFF response model
""",
    },
]


def write_sources() -> None:
    MMD_DIR.mkdir(parents=True, exist_ok=True)
    SVG_DIR.mkdir(parents=True, exist_ok=True)
    for item in USE_CASES:
        uc = item["id"]
        header = (
            f"%% CURRENT prod,remote diagram; {uc} {item['title']}; "
            f"services: {item['services']}\n"
        )
        seq = uc[2:]
        for layer, key in (("SYS", "system"), ("COMP", "component"), ("OBJ", "object")):
            (MMD_DIR / f"{uc}-{layer}-SEQ{seq}-CURRENT.mmd").write_text(
                header + item[key].lstrip(), encoding="utf-8"
            )


if __name__ == "__main__":
    write_sources()
    print(f"generated {len(USE_CASES) * 3} current Mermaid sources in {MMD_DIR.relative_to(ROOT)}")
