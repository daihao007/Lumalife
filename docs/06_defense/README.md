# LumaLife 最终答辩交付包

> 更新日期：2026-09-03（Asia/Shanghai）
> 状态：`⚠️ PARTIAL`

## 1 已生成材料

| 材料 | 文件 | 状态 |
|---|---|---|
| 最终答辩 PPT | `LumaLife-最终答辩.pptx` | ✅ 13 页可编辑稿；13/13 渲染与逐页视觉检查，布局 0 越界 |
| 项目技术总结 | [`technical-summary.md`](technical-summary.md) | ✅ 当前事实版 |
| 个人权重与全员确认 | [`contribution-signoff.md`](contribution-signoff.md) | `UNVERIFIED`，等待成员填写和签署 |
| 项目管理证据 | [`../05_management/README.md`](../05_management/README.md) | ⚠️ 十日原件不完整 |
| 最终事实与审计 | [`../project-facts.md`](../project-facts.md)、[`../final-audit/final-project-audit.md`](../final-audit/final-project-audit.md) | ✅ 当前权威入口 |

## 2 建议答辩分工

以下按 GitHub Project 的主责生成，仅是演示分工草案，不等同于成员签字确认。

| GitHub 负责人 | 建议答辩主题 | 主要证据 |
|---|---|---|
| `daihao007` | 项目目标、需求追溯、结论与材料总览 | Project Facts、REQ/UC 追溯、正式文档 |
| `Chrmysle` | 用户/商家/管理员界面与代表性业务演示 | 用户手册截图、UI E2E |
| `yuwu-code` | 微服务边界、数据归属、Saga 与故障处理 | 架构三表、服务代码、故障证据 |
| `ZQHtech` | 测试分类、52 API 矩阵与性能结果 | Test Inventory、API 矩阵、性能原始结果 |
| `Sun0720336` | Docker、CI/CD、Kubernetes、HPA 与排障 | Compose、CI、K8s 清单、HPA 原始 CSV |

成员应在正式答辩前确认或调整分工；未确认时状态为 `UNVERIFIED`。

## 3 建议现场演示顺序

1. 普通用户注册并浏览商家，加入购物车并创建订单。
2. 商家处理订单或维护商品，展示角色与数据边界。
3. 用户查看订单并完成评价，展示状态闭环。
4. 客服对话展示用户、商家和 Assistant 的协作。
5. 平台管理员查看指标与健康状态。
6. 展示 52/52 API 覆盖矩阵、测试清单和当前 `NOT-RUN` 边界。
7. 展示 Compose/Kubernetes/HPA、故障和性能原始证据。

现场演示前必须使用实际可用环境复查账号、网络和数据状态。没有复查时不得写“演示环境已就绪”。

## 4 答辩口径红线

- 当前默认架构是 backend BFF + 4 个业务微服务，不是“完全移除单体”。
- 221 是正式测试资产总数，不等于本次当前 HEAD 一次性全量通过。
- 当前 Microservice E2E 为 `NOT-RUN`；旧提交 9/9 只能作为既有证据。
- 性能小样本中单体三接口均更快，不能宣传微服务性能提升。
- timeout、显式 503、fallback 和隔离不能称为通用 circuit breaker。
- HPA、故障和性能结果必须注明证据提交与实验边界。
- 个人权重、实际工时和全员确认必须由成员本人填写或签署。

## 5 提交前清单

- [x] 当前项目技术总结
- [x] 可编辑答辩 PPT
- [x] PPT 中的数字与 `project-facts.md` 一致
- [x] PPT 每页包含来源说明
- [x] PPT 全页渲染与溢出检查
- [ ] 五名成员确认演示分工
- [ ] 五名成员填写个人贡献权重，合计 100%
- [ ] 五名成员签字或提供可核验电子确认
- [ ] D09/D10 Project 状态和证据真实闭环
- [ ] 答辩设备与实际演示环境复查
