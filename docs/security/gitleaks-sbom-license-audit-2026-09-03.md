# Gitleaks、SBOM 与许可证审计（2026-09-03）

## 1 范围与结论

- 绑定提交：`main@3696741`
- Secret 范围：该提交的 616 个受跟踪文件快照，以及仓库全部 233 个 Git 提交
- SBOM 范围：同一受跟踪文件快照；不包含未跟踪 `tmp/`、本机 `.env`、外部 CI Secret、容器运行时文件系统或云端制品
- 结论：Gitleaks 两层扫描均为 **0 finding**；已生成 SPDX 2.3 SBOM；许可证清单仍有 38 个条目为 `NOASSERTION`。后续 Grype 源码 SBOM 扫描发现 11 个可修复 npm match（6 High、4 Medium、1 Low），详见 [`vulnerability-scan-2026-09-03.md`](vulnerability-scan-2026-09-03.md)。当前状态为 `SECRET-SCAN-PASS / SBOM-GENERATED / LICENSE-PARTIAL / SOURCE-SBOM-VULNERABILITY-SCANNED / FINDINGS-OPEN / COVERAGE-PARTIAL`。

本项没有启动 Docker、应用、E2E、服务器实验或共享环境。

## 2 工具与供应链校验

工具均从官方 GitHub Release 下载到未跟踪 `tmp/`，压缩包 SHA-256 与官方 checksums 文件一致。

| 工具 | 版本 | 平台 | 下载包 SHA-256 |
|---|---:|---|---|
| Gitleaks | 8.30.1 | windows/x64 | `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e` |
| Syft | 1.51.0 | windows/amd64 | `fc5ffaeffb993576ece9c791da5a688fb2c8969a1479bbfe58583672c64da336` |

官方来源：

- `https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1`
- `https://github.com/anchore/syft/releases/tag/v1.51.0`

## 3 Gitleaks 结果

### 3.1 当前受跟踪快照

先以 `git archive HEAD` 导出 `3696741`，避免把未跟踪目录和本机文件纳入扫描，再执行：

```powershell
gitleaks dir --no-banner --no-color --redact=100 `
  --report-format json --report-path gitleaks-head.json head-tree
```

结果：

| 指标 | 值 |
|---|---:|
| 受跟踪文件 | 616 |
| 扫描数据 | 约 9.60 MB |
| Finding | **0** |
| Exit code | 0 |

### 3.2 全部 Git 历史

```powershell
gitleaks git --no-banner --no-color --redact=100 `
  --log-opts='--all' --report-format json `
  --report-path gitleaks-history.json .
```

结果：

| 指标 | 值 |
|---|---:|
| Git 提交 | 233 |
| 扫描数据 | 约 12.77 MB |
| Finding | **0** |
| Exit code | 0 |

报告启用 100% redaction；本次没有候选 Secret 值需要写入仓库。0 finding 表示默认规则没有发现匹配项，不是对所有未知格式凭据的绝对证明。

## 4 SPDX SBOM

生成命令：

```powershell
syft scan dir:head-tree --source-name Lumalife --source-version 3696741 `
  -o spdx-json=lumalife-3696741.spdx.json
```

正式产物：[`lumalife-3696741.spdx.json`](lumalife-3696741.spdx.json)

| 指标 | 值 |
|---|---:|
| SPDX 版本 | SPDX-2.3 |
| Package 条目 | 287 |
| npm | 251 |
| Maven | 15 |
| GitHub Actions | 20 |
| 根项目/未知类型 | 1 |
| SBOM SHA-256 | `4461bfa52b60834df2e36a7c5cff7b597df1a8778906118324c04031cfb89918` |

## 5 许可证摘要

| `licenseDeclared` | 条目数 |
|---|---:|
| MIT | 224 |
| ISC | 18 |
| BSD-3-Clause | 3 |
| Apache-2.0 | 2 |
| MIT AND ISC | 1 |
| CC-BY-4.0 | 1 |
| NOASSERTION | 38 |

在 249 个已有声明的条目中，没有出现 GPL、AGPL、LGPL、EPL 或 MPL 声明。但这不能推出整仓许可证已全部合规，因为：

1. 38 个 SBOM 条目为 `NOASSERTION`，包含重复引用后的 GitHub Actions、项目自身模块和部分 Maven 坐标；
2. 仓库没有受跟踪的根 LICENSE/COPYING 文件，项目自身发布授权仍未声明；
3. 源码目录扫描主要从 lockfile、POM 和 workflow 识别包，未证明所有 Maven 传递依赖和容器基础镜像的许可证均已解析；
4. 本项没有做法律解释，也没有检查第三方素材、字体、截图和云端制品的授权链。

## 6 未完成项与发布门禁

- 对 38 个 `NOASSERTION` 条目逐项补充来源和许可证判断；
- 由项目成员决定并添加适用的项目 LICENSE，课程内部交付与公开发布应区分；
- 对 Maven 传递依赖、容器镜像和前端生产制品生成补充 SBOM；
- 已使用 Grype 0.118.0 对 commit-bound 源码 SBOM 执行扫描；仍需修复 11 个 npm match，并补 Maven 完整传递依赖、容器镜像和前端生产制品扫描；
- 如任何后续扫描发现真实凭据，先撤销/轮换，再处理 Git 历史和已发布制品。

因此 R26 的 Secret scanner 与基础 SBOM 缺口已闭环，源码 SBOM 漏洞扫描已运行但有未关闭发现且覆盖不完整；许可证与整体漏洞审计仍保持 `PARTIAL`。
