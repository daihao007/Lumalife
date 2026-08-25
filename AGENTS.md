AGENTS
======

概述
----

此文档列出在本仓库中可用的“代理（agents）”与其用途、调用示例及约定。代理用于通过子代理（subagent）执行复杂任务或自动化多步工作流，例如代码库巡检、Java 项目升级、安全扫描等。

全局约定
----

- 调用方式：使用 `runSubagent` 工具或等效流程，传入 `agentName` 和 `prompt`。
- Prompt 内尽量包含清晰目标、范围和期望输出；对于代码浏览类代理，指定 `thoroughness`（`quick|medium|thorough`）。
- 变更此文件时请在仓库根目录提交 PR 并在 PR 描述中说明新增或修改的 agent 条目。

可用代理（摘要）
----

- Explore
  - 用途：快速读代码库并生成可操作的摘要或定位特定符号/文件。
  - 参数提示：描述要查找的内容与期望彻底程度（示例："查找所有 REST API 路由，thoroughness: quick"）。

- modernize-java
  - 用途：对 Java 项目进行版本升级、兼容性检查与自动化修复建议。
  - argumentHint：目标版本与项目路径。

- modernize-java-upgrade
  - 用途：逐步执行 Java/Spring Boot 升级计划并修改代码以保证构建通过。
  - argumentHint：目标版本（例如 Java 21 / Spring Boot 3.2）。

- modernize-java-assessment
  - 用途：生成升级或现代化评估报告（证据驱动的发现）。

- modernize-java-security
  - 用途：扫描并修复 Java 依赖中的已知 CVE，生成优先级修复计划。

- modernize-azure-java
  - 用途：将 Java 应用现代化并部署到 Azure（包含架构与部署建议）。

- modernize-azure-dotnet
  - 用途：将 .NET 应用现代化并部署到 Azure。

如何调用（示例）
----

示例：请求 Explore 代理快速浏览项目并列出 API 路由

{
  "agentName": "Explore",
  "prompt": "查找项目中所有 API 路由并按文件列出，thoroughness: quick"
}

示例：请求 modernize-java-upgrade 进行目标版本升级评估

{
  "agentName": "modernize-java-upgrade",
  "prompt": "将项目升级到 Java 21，生成逐步执行计划并列出可能的破坏性变更。项目根路径: /"
}

新增或定制代理
----

如需新增代理，请在本文件末尾添加条目并提交 PR；描述代理用途、参数提示和示例调用。

变更记录
----

- 2026-05-23: 初始版本，由自动化生成。
