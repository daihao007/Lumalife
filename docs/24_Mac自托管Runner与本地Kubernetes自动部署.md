# Mac 自托管 Runner 与本地 Kubernetes 自动部署

## 目标

GitHub 托管 Runner 无法访问 Mac 上 Docker Desktop 的 Kubernetes API。本项目因此使用两段式流水线：

1. `.github/workflows/ci.yml` 在 GitHub 托管 Runner 上执行测试、构建、MySQL/Compose/E2E/Kubernetes 冒烟，并把五个 `sha-*` 镜像发布到 GHCR。
2. `.github/workflows/deploy-local-k8s.yml` 等待 `Monolith CI` 在 `main` 上成功，然后由这台 Mac 上带 `lumalife-k8s` 标签的自托管 Runner 拉取同一版本镜像，部署到 `docker-desktop` 集群并执行健康检查。

PR 不会更新本地集群。只有进入 `main` 且完整 CI 成功的提交才会自动部署；也可以从 Actions 页面手动选择镜像标签重新部署。

## 前置检查

保持 Docker Desktop 和 Kubernetes 开启，在终端运行：

```bash
docker info >/dev/null
kubectl config use-context docker-desktop
kubectl get nodes
```

节点状态必须为 `Ready`。自托管 Runner 应使用当前 macOS 登录用户运行，以便读取同一份 Docker Desktop 与 kubeconfig 配置。

## 注册 Mac 自托管 Runner

1. 打开 GitHub 仓库的 `Settings -> Actions -> Runners`。
2. 点击 `New self-hosted runner`，选择 `macOS` 和本机架构。Apple Silicon 选择 ARM64，Intel Mac 选择 x64。
3. 在 Mac 上新建一个专用目录，并严格执行 GitHub 页面生成的下载和配置命令。配置命令增加自定义标签：

   ```bash
   ./config.sh --url https://github.com/daihao007/Lumalife --token <GitHub页面显示的一次性令牌> --labels lumalife-k8s
   ```

   一次性令牌不要提交到 Git、Issue、聊天或截图中。

4. 首次验证可直接启动 Runner：

   ```bash
   ./run.sh
   ```

5. 确认仓库 `Settings -> Actions -> Runners` 中该 Runner 显示 `Idle`，并且标签包含 `self-hosted`、`macOS`、`ARM64` 和 `lumalife-k8s`。Runner 名称不等于标签；如果配置时把 `lumalife-k8s` 填成了名称，需要在 Runner 详情页的 Labels 区域补加同名标签。

如需开机后持续自动部署，按照 GitHub Runner 配置完成后的提示安装为 macOS 服务：

```bash
./svc.sh install
./svc.sh start
./svc.sh status
```

Docker Desktop 仍需在部署时运行；Runner 服务在线但 Docker Desktop 或 Kubernetes 关闭时，部署会在目标检查阶段失败并留下诊断日志。

## GitHub Environment

工作流使用名为 `kubernetes` 的 Environment，部署记录会显示在仓库 Deployments 页面。可在 `Settings -> Environments -> kubernetes` 中限制只有 `main` 分支能够部署。

如果配置 Required reviewers，每次部署都需要人工批准；如果希望合并后完全自动部署，不要为该 Environment 配置人工审批。

本地自托管流程不需要 `KUBE_CONFIG_BASE64`。它直接使用 Runner 用户的 `docker-desktop` 上下文。

## 第一次验收

Runner 在线后，打开 `Actions -> Deploy Local Kubernetes -> Run workflow`。第一次可将 `image_tag` 填为已存在的 `main`，验证 Runner、Docker、kubectl 和部署脚本是否连通。

成功后检查：

```bash
kubectl -n lumalife get deployments,statefulsets,pods,services,persistentvolumeclaims
kubectl -n lumalife get pods
```

访问前端：

```bash
kubectl -n lumalife port-forward svc/frontend 5173:80
```

随后打开 `http://localhost:5173`。

## 自动触发链路

```text
PR -> Monolith CI -> Quality gate
                     |
merge/push main -----+
                     v
              发布 sha-* 镜像
                     v
              Kind 部署冒烟通过
                     v
         Mac 自托管 Runner 拉取同一 sha-*
                     v
        Docker Desktop Kubernetes 部署与健康检查
```

如果 Mac 关机、Runner 离线或 Docker Desktop 未启动，本地部署任务会等待或失败，但云端 CI 和镜像发布记录仍然保留。恢复 Runner 后，可在 Actions 页面重新运行失败任务或手动部署同一个 `sha-*` 标签。
