# 十个实践日管理证据矩阵

> 快照时间：2026-09-03（Asia/Shanghai）
> 项目范围：D01～D10，2026-08-25 至 2026-09-04；2026-08-30 为计划休息日。
> 状态定义：`VERIFIED` 表示存在可直接核验的原件；`PARTIAL` 表示仅部分成员或部分材料可核验；`UNVERIFIED` 表示未找到对应原件。

## 1 逐日矩阵

| 日次 | 日期 | GitHub Issue | Project 状态 | 已勾选站会 Issue | 仓库每日截图 | 结论 |
|---|---|---|---:|---|---:|---|
| D01 | 2026-08-25 | [#16](https://github.com/daihao007/Lumalife/issues/16)～[#20](https://github.com/daihao007/Lumalife/issues/20) | 5 Done | #17、#18、#20（3/5） | 2 | `PARTIAL`；#16 明确写明站会无可验证记录、未倒填 |
| D02 | 2026-08-26 | [#21](https://github.com/daihao007/Lumalife/issues/21)～[#25](https://github.com/daihao007/Lumalife/issues/25) | 5 Done | #23、#25（2/5） | 2 | `PARTIAL` |
| D03 | 2026-08-27 | [#26](https://github.com/daihao007/Lumalife/issues/26)～[#30](https://github.com/daihao007/Lumalife/issues/30) | 5 Done | 0/5 | 0 | 任务完成可核验；站会/每日截图 `UNVERIFIED` |
| D04 | 2026-08-28 | [#31](https://github.com/daihao007/Lumalife/issues/31)～[#35](https://github.com/daihao007/Lumalife/issues/35) | 5 Done | #31、#32、#35（3/5） | 0 | 任务完成可核验；站会 `PARTIAL`，每日截图 `UNVERIFIED` |
| D05 | 2026-08-29 | [#36](https://github.com/daihao007/Lumalife/issues/36)～[#40](https://github.com/daihao007/Lumalife/issues/40) | 5 Done | #37（1/5） | 0 | 任务完成可核验；站会 `PARTIAL`，每日截图 `UNVERIFIED` |
| — | 2026-08-30 | — | 计划休息日 | 不适用 | 不适用 | Project README 明确不安排集中实践 |
| D06 | 2026-08-31 | [#41](https://github.com/daihao007/Lumalife/issues/41)～[#45](https://github.com/daihao007/Lumalife/issues/45) | 5 Done | 0/5 | 0 | 任务完成可核验；站会/每日截图 `UNVERIFIED` |
| D07 | 2026-09-01 | [#46](https://github.com/daihao007/Lumalife/issues/46)～[#50](https://github.com/daihao007/Lumalife/issues/50) | 5 Done | 0/5 | 0 | 任务完成可核验；站会/每日截图 `UNVERIFIED` |
| D08 | 2026-09-02 | [#51](https://github.com/daihao007/Lumalife/issues/51)～[#55](https://github.com/daihao007/Lumalife/issues/55) | 5 Done | 0/5 | 0 | 任务完成可核验；站会/每日截图 `UNVERIFIED` |
| D09 | 2026-09-03 | [#56](https://github.com/daihao007/Lumalife/issues/56)～[#60](https://github.com/daihao007/Lumalife/issues/60) | 5 Todo | 0/5 | 0 | 本次核验时尚未完成，不得提前闭环 |
| D10 | 2026-09-04 | [#61](https://github.com/daihao007/Lumalife/issues/61)～[#65](https://github.com/daihao007/Lumalife/issues/65) | 5 Todo | 0/5 | 0 | 未来计划，状态 `NOT-DUE/UNVERIFIED` |

## 2 任务字段完整性

对 Issue #16～#65 的 50 个计划任务进行只读检查：

| 字段/条件 | 核验结果 | 说明 |
|---|---:|---|
| 指定负责人 | 50/50 | 五名成员各 10 项 |
| 日报日期 | 50/50 | 与 D01～D10 标题计划一致 |
| 计划工时 | 50/50 | 每项 7 小时 |
| 验收条件或验收结果 | 50/50 | #32 使用“验收结果”标题，其余使用验收条件 |
| 阻塞/风险 | 50/50 | 有字段不代表风险已经消除，需结合状态阅读 |
| 证据要求或证据记录 | 50/50 | D09/D10 多数仍是要求，不是已完成证据 |
| Project 状态 Done | 40/50 | D01～D08 |
| Project 状态 Todo | 10/50 | D09～D10 |
| 已勾选站会 | 9/50 | 只覆盖 D01、D02、D04、D05，不能外推到全体/全日 |

## 3 证据强度说明

### 3.1 可用于答辩的证据

- GitHub Project 的公开计划、字段定义、任务状态与五人均衡分配；
- Issue #16～#65 的负责人、日期、目标、验收、风险及证据链接；
- D01、D02 的仓库原始看板和日报截图；
- PR、提交和 Git 历史用于证明任务产出及交叉协作。

### 3.2 仍需由项目成员补充的原件

- D03～D10 每天的 Project 看板和日报统计截图；
- 每个实践日的站会纪要原文，至少包含昨天完成、今天计划、阻塞和参与人；
- D09、D10 完成后的实际状态、证据链接和当日截图；
- 若课程要求实际工时，需补成员确认或平台时长原件，不能从计划工时推算。

### 3.3 不允许的补证方式

- 不得按 Issue 模板反向编写过去的站会内容；
- 不得修改截图时间或把当前 Project 截图冒充历史每日截图；
- 不得因为任务已 Done 就把站会和日报同时判定为已完成；
- 不得替成员签署实际工时、贡献比例或全员确认。

## 4 下一次复核

在 D09 或 D10 状态发生真实变化后，重新运行 `README.md` 中的只读命令，更新本矩阵的状态和证据链接。若没有新增原件，缺口继续保持 `UNVERIFIED`。
