# D05 Kubernetes 自动部署与健康检查

## 1. 交付目标

本文对应 Issue #40。流水线在代码进入 `main` 或从 `main` 手动触发后，按以下顺序执行：

```text
后端/前端/数据库/Compose/E2E/清单检查
                  ↓
               质量门禁
                  ↓
       构建并发布 sha-* 版本镜像
                  ↓
          Kind 临时集群部署验证
                  ↓
       目标集群滚动部署与健康检查
```

任一上游步骤失败时，其下游镜像发布和 Kubernetes 部署都会被跳过。部署只引用 `sha-<短提交号>` 镜像，不以 `latest` 作为发布版本。

当前业务后端仍使用内存版 `DemoStore`，尚未消费 MySQL，因此本次 Kubernetes 清单只交付前端和后端；这与当前运行时边界一致，不代表数据库持久化已经完成。

## 2. Kubernetes 资源

`k8s/` 使用原生 Kustomize，包含固定命名空间 `lumalife`、前后端各两个副本的 Deployment 与 ClusterIP Service。

| 工作负载 | 启动探针 | 就绪探针 | 存活探针 |
| --- | --- | --- | --- |
| backend | `/actuator/health/liveness` | `/actuator/health/readiness` | `/actuator/health/liveness` |
| frontend | `/healthz` | `/healthz` | `/healthz` |

后端在 `application.yml` 中显式启用 Spring Boot 健康探针。前端 Nginx 通过集群内 `backend:8080` Service 代理 `/api/` 和 `/actuator/`。

## 3. 首次配置目标集群

在 GitHub 仓库中创建名为 `kubernetes` 的 Environment，并添加 Secret：

- `KUBE_CONFIG_BASE64`：有权管理目标命名空间的 kubeconfig 文件经过 Base64 编码后的内容。

Linux/macOS 生成方式：

```bash
base64 -w 0 < ~/.kube/config
```

PowerShell 生成方式：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.kube\config"))
```

GHCR 镜像应设置为公开可拉取。如果必须使用私有镜像，先在集群创建长期 `read:packages` 拉取凭据并挂到默认 ServiceAccount；不要把短期 `GITHUB_TOKEN` 当作长期拉取凭据：

```bash
kubectl apply -f k8s/namespace.yaml
kubectl -n lumalife create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io \
  --docker-username=<github-user> \
  --docker-password=<read-packages-token>
kubectl -n lumalife patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"ghcr-pull"}]}'
```

## 4. 自动部署与健康检查

PR 阶段的 `Kubernetes rollout smoke test` 会创建一次性 Kind 集群、本地构建并加载两张镜像、应用同一套清单，然后等待前后端滚动发布完成。

`main` 的 `Deploy Kubernetes` 使用不可变的 `sha-*` 镜像更新目标集群，并执行：

1. `kubectl rollout status`，只有所有副本通过 readiness probe 才继续；
2. 在集群内创建一次性 curl Pod，检查后端 readiness、前端 `/healthz` 和前端代理后的后端 readiness；
3. 输出 Deployment、Pod 和 Service 状态；失败时额外输出 Deployment/Pod 的 describe 诊断，作为探针记录。

部署逻辑集中在 `scripts/deploy-k8s.sh`，本地或运维终端可用同样命令复验：

```bash
bash scripts/deploy-k8s.sh sha-<短提交号>
kubectl -n lumalife get deployments,pods,services -o wide
```

## 5. 回滚

发现发布异常时可回滚前后端 Deployment，并再次等待探针：

```bash
kubectl -n lumalife rollout undo deployment/backend
kubectl -n lumalife rollout undo deployment/frontend
kubectl -n lumalife rollout status deployment/backend --timeout=300s
kubectl -n lumalife rollout status deployment/frontend --timeout=300s
```

回滚后应记录恢复到的 revision 与对应镜像；后续流水线仍会以 Git 中选定提交的 `sha-*` 镜像作为期望状态。

## 6. Issue #40 验收证据

成功记录：合并后保存一次 `main` 的 Actions 运行链接，并确认质量门禁、两张版本镜像、Kind 冒烟与 `Deploy Kubernetes` 均成功；下载 `kubernetes-manifests` Artifact，保存部署作业末尾的 Pod/探针输出。

失败阻断记录：在 Actions 中从 `main` 手动运行 `Monolith CI`，将 `demonstrate_failure_gate` 设为 `true`。确认 `Quality gate` 预期失败，镜像、Kind 冒烟和真实部署作业均为 `Skipped`，然后把运行链接附到 Issue #40。该开关不修改应用代码或集群。
