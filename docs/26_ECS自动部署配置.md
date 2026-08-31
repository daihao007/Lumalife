# ECS K3s 自动部署配置

本项目的 `.github/workflows/deploy-ecs-k3s.yml` 会在 `main` 分支的 Monolith CI 成功后自动部署到阿里云 ECS 上的 K3s。它不会使用 Docker Desktop，也不会部署到 GitHub Actions 的临时 Kind 集群。

## 一次性服务器准备

在自己的 Mac 终端生成专用部署密钥：

```bash
ssh-keygen -t ed25519 -f ~/.ssh/lumalife_github_deploy -C "lumalife-github-actions"
```

把公钥安装到 ECS：

```bash
ssh-copy-id -i ~/.ssh/lumalife_github_deploy.pub root@你的ECS公网IP
```

验证密钥登录：

```bash
ssh -i ~/.ssh/lumalife_github_deploy root@你的ECS公网IP
```

取得服务器 SSH 指纹：

```bash
ssh-keyscan -t ed25519 你的ECS公网IP 2>/dev/null | ssh-keygen -lf - -E sha256
```

记录输出中的 `SHA256:...` 值。

## GitHub 配置

在仓库 `Settings → Secrets and variables → Actions` 中配置以下值。

### Secrets

| 名称 | 内容 |
| --- | --- |
| `ECS_HOST` | ECS 公网 IP 或域名 |
| `ECS_USER` | `root` |
| `ECS_SSH_KEY` | `~/.ssh/lumalife_github_deploy` 私钥的完整内容 |
| `ECS_SSH_FINGERPRINT` | 上一步得到的 `SHA256:...` 指纹 |
| `MYSQL_PASSWORD` | K3s 部署使用的 MySQL 应用密码 |
| `MYSQL_ROOT_PASSWORD` | K3s 部署使用的 MySQL root 密码 |

### Variables

| 名称 | 内容 |
| --- | --- |
| `ECS_DEPLOY_PATH` | `/opt/Lumalife` |
| `ECS_DEPLOY_ENABLED` | 初始填 `false`；全部 Secret 配好后改为 `true` |

启用后，后续每次推送到 `main` 都按以下顺序执行：测试 → 构建并发布 GHCR 多架构镜像 → 临时 Kind 验证 → SSH 到 ECS → K3s 滚动更新 → 集群内健康检查。

## 安全与失败策略

- SSH 使用部署专用密钥和服务器指纹校验，不使用 ECS 登录密码。
- 工作流只部署由成功 CI 对应的不可变 `sha-提交短哈希` 镜像。
- 部署前要求 `/opt/Lumalife` 工作区干净，并用 `git pull --ff-only` 更新；不会执行 `git reset --hard`。
- 如果一个较新的提交已经到达 main，旧工作流会停止，交由新提交的工作流部署，避免旧镜像覆盖新版本。
