# ECS K3s 自动部署配置

本项目使用 ECS 上的 GitHub Actions self-hosted Runner 执行 K3s 部署。Runner 主动通过 HTTPS 领取任务，不需要 GitHub 托管 Runner 从动态公网 IP 登录 ECS 的 SSH 服务。

## 自动化流程

1. 推送代码到 `main`。
2. GitHub 托管 Runner 执行测试、构建并推送不可变 GHCR 镜像。
3. CI 上传与提交 SHA 绑定的 `ecs-deploy-bundle` 制品。
4. CI 全部成功后触发 `Deploy ECS K3s`。
5. ECS self-hosted Runner 下载该 CI 运行产生的制品。
6. Runner 在本机执行 K3s 滚动更新和集群内健康检查。

## GitHub 配置

在仓库 `Settings → Secrets and variables → Actions` 中保留以下配置。

### Secrets

| 名称 | 内容 |
| --- | --- |
| `MYSQL_PASSWORD` | 当前 K3s MySQL 应用密码 |
| `MYSQL_ROOT_PASSWORD` | 当前 K3s MySQL root 密码 |

旧的 `ECS_HOST`、`ECS_USER`、`ECS_SSH_KEY`、`ECS_SSH_FINGERPRINT` 不再由工作流使用，确认新流程成功后可以删除。

### Variables

| 名称 | 内容 |
| --- | --- |
| `ECS_DEPLOY_ENABLED` | 保持 `true`；需要紧急停止自动部署时改为 `false` |
| `ECS_RUNNER_READY` | Runner 注册并验证前不创建或设为 `false`，完成后改为 `true` |
| `ECS_KUBECONFIG_PATH` | `/home/lumalife-runner/.kube/config` |

## Runner 安全边界

- Runner 标签必须包含 `lumalife-ecs`，普通 CI 作业不会使用它。
- 部署任务仅接受本仓库 `main` 分支的成功 `push` CI，不接受 fork 或 Pull Request。
- Runner 使用专用 Linux 用户运行，不使用 `root` SSH 登录。
- ECS 安全组的 22 端口只需允许管理员自己的固定公网 IP。
- 仓库为公开仓库时，不要让 Pull Request 作业使用 self-hosted Runner。

## 日常使用

配置完成后不需要手动登录服务器部署。每次 `main` CI 成功后自动更新 ECS；CI 失败时不会发布部署任务。
