# LumaLife 故障处理实验报告

> `EXISTING-EVIDENCE-VERIFIED`：原始 before/during/after 文件支持下述结论。本轮未重新改变服务器或集群状态。实验脚本的 PASS gate 只强制 during 非 200，未强制 503/业务码/恢复成功，因此最终结论依赖原始状态码与响应体的人工复核，不能只凭脚本退出文本。

## 1. 实验结论

本轮在 Docker Desktop Kubernetes（namespace `lumalife`）完成 merchant-service 故障注入，结果为 PASS。

实验使用 `04_tests/cloud-native/run-fault-experiment.sh`，将 merchant-service 从 1 个副本缩容到 0，再恢复到原副本数。为避免 HPA 立即干预故障注入，脚本仅在注入窗口临时删除 merchant-service HPA，并在退出路径恢复原清单；未删除数据库、PVC 或应用源码。

## 2. 实测结果

| 检查项 | 故障前 | merchant-service=0 | 恢复后 |
| --- | ---: | ---: | ---: |
| backend readiness | HTTP 200 | HTTP 200 | HTTP 200 |
| `GET /api/v1/merchants` | HTTP 200 | HTTP 503 | HTTP 200 |
| 业务错误码 | — | `50300` | — |
| reason | — | `MERCHANT_SERVICE_UNAVAILABLE` | — |
| merchant-service 副本 | 1/1 | 0/0 | 1/1 |

故障期间实际返回体为：

```json
{"code":50300,"message":"商家服务暂时不可用","data":null,"reason":"MERCHANT_SERVICE_UNAVAILABLE"}
```

这证明 BFF 没有把服务边界故障转换成未解释的 HTTP 500，用户能够获得明确的“商家服务暂时不可用”结果；backend readiness 也没有因为商家查询服务短时不可用而错误地判为未就绪。

## 3. 原始证据

- `04_tests/cloud-native/fault/summary.md`
- `04_tests/cloud-native/fault/before.txt`
- `04_tests/cloud-native/fault/during.txt`
- `04_tests/cloud-native/fault/after.txt`
- `04_tests/cloud-native/fault/before-merchants-body.txt`
- `04_tests/cloud-native/fault/during-merchants-body.txt`
- `04_tests/cloud-native/fault/after-merchants-body.txt`
- `04_tests/cloud-native/fault/events.txt`
- `04_tests/cloud-native/fault/backend.log`

## 4. 课程范围内的边界

本实验验证的是单个 HTTP 服务故障时的隔离、明确错误和恢复，不等价于生产级熔断、限流、自动重试、跨区域容灾或全链路混沌工程。Saga 的库存确认失败补偿由 order-service 单元测试和代码路径验证，未在本次商家服务停机实验中强行制造支付数据。

补充的代码级边界验证：`OrderInventoryResultConsumerTest` 覆盖 `CHECK_REQUIRED`、`RELEASE_FAILED` 和重复结果消息幂等；`MerchantInventoryInboxConsumerTest` 覆盖重复 release 投递只处理一次；`MerchantExpiredReservationProcessorTest` 覆盖过期预占的安全检查。上述测试不能替代 RabbitMQ + MySQL 远端故障 E2E，现场报告仍应将两者区分。
