# LumaLife 项目管理材料

> 核验日期：2026-09-03（Asia/Shanghai）
> 状态：`⚠️ PARTIAL`
> 证据原则：只引用 GitHub Project、Issue/PR、Git 历史和仓库原始截图；没有原件的站会、截图或确认不得倒填。

## 1 材料范围

本目录是课程项目管理材料的统一入口。当前已核验：

- GitHub Project：[Lumalife Project #2](https://github.com/users/daihao007/projects/2)，公开且未关闭；
- 项目计划为 10 个实践日、5 名成员、每人每天 1 项，共 50 项任务；
- 五名成员各 10 项、每项计划 7 小时，即每人 70 计划工时、合计 350 人时；
- D01～D08 的 40 项任务在 Project 中均为 `Done`；
- D09（2026-09-03）和 D10（2026-09-04）的 10 项任务在本次核验时均为 `Todo`；
- 仓库保留 D01、D02 的看板与日报统计截图，共 4 张；
- Issue 正文中的已勾选站会记录只覆盖部分日期和成员，不能宣称“10 天全员站会证据齐全”。

逐日状态、Issue 范围、站会勾选和截图缺口见 [`ten-day-evidence-matrix.md`](ten-day-evidence-matrix.md)。

## 2 分工与工作量

| GitHub 负责人 | 主责 | 任务数 | 计划工时 | 当前 Done / Open |
|---|---|---:|---:|---:|
| `daihao007` | 范围、需求设计追溯、阶段门禁、最终材料 | 10 | 70h | 8 / 2 |
| `Chrmysle` | 多角色前端、接口适配、演示证据 | 10 | 70h | 8 / 2 |
| `yuwu-code` | 业务服务、数据边界、故障处理 | 10 | 70h | 8 / 2 |
| `ZQHtech` | 单元/API/E2E、回归和性能实验 | 10 | 70h | 8 / 2 |
| `Sun0720336` | 数据库、容器、CI/CD、Kubernetes、HPA | 10 | 70h | 8 / 2 |
| **合计** |  | **50** | **350h** | **40 / 10** |

上述为计划工时，不等于已核验实际投入。实际工时若无成员签字、平台时长记录或其他原件，状态为 `UNVERIFIED`。

## 3 已有仓库原始证据

| 日期 | 看板 | 日报统计 | 补充说明 |
|---|---|---|---|
| 2026-08-25 / D01 | [`issue-18-project-board.png`](../evidence/2026-08-25/issue-18-project-board.png) | [`daily-project-stats.png`](../evidence/2026-08-25/daily-project-stats.png) | 截图显示当日任务与状态 |
| 2026-08-26 / D02 | [`issue-23-project-board.png`](../evidence/2026-08-26/issue-23-project-board.png) | [`issue-23-daily-project-stats.png`](../evidence/2026-08-26/issue-23-daily-project-stats.png) | 另有 [`daily-project-stats.md`](../evidence/2026-08-26/daily-project-stats.md) |

图片只能证明截图时的 Project 状态；后补 Markdown 与后续 Issue/PR 状态必须按各自时间阅读。

## 4 当前不能宣称的内容

- 不能宣称已保存 D01～D10 每天的看板和日报截图；当前只找到 D01、D02。
- 不能宣称五名成员每天均完成并留下站会原文；已勾选记录只有 9 个 Issue，覆盖 4 个实践日。
- 不能把任务模板中的“站会”复选框当作实际会议记录；未勾选即 `UNVERIFIED`。
- 不能把 70 计划工时写成 70 已完成工时。
- 不能把 D09、D10 写成已完成；本次只读核验时 Project 状态为 `Todo`。

## 5 复核命令

```powershell
gh project list --owner daihao007 --limit 20 --format json
gh project field-list 2 --owner daihao007 --format json
gh project item-list 2 --owner daihao007 --limit 200 --format json
gh issue list --repo daihao007/Lumalife --state all --limit 200 `
  --json number,title,state,createdAt,closedAt,assignees,url,body
git log --date=short --pretty=format:"%h`t%ad`t%an`t%s" --all `
  --since=2026-08-25 --until=2026-09-05
```

这些命令是只读复核入口，不会修改 Project、Issue 或仓库状态。
