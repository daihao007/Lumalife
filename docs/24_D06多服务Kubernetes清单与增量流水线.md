# D06 多服务 Kubernetes 清单与增量流水线

关联任务：[#45](https://github.com/daihao007/Lumalife/issues/45)

## 交付范围

| 服务 | 应用版本 | 容器端口 | 独立镜像 | Kubernetes 资源 |
|---|---|---:|---|---|
| identity-service | 0.1.0 | 8081 | `ghcr.io/daihao007/lumalife-identity-service` | Deployment + Service |
| merchant-service | 0.1.0 | 8082 | `ghcr.io/daihao007/lumalife-merchant-service` | Deployment + Service |
| order-service | 0.1.0 | 8083 | `ghcr.io/daihao007/lumalife-order-service` | Deployment + Service |

每个 Deployment 都配置了：

- `startupProbe`：`/actuator/health/liveness`
- `readinessProbe`：`/actuator/health/readiness`
- `livenessProbe`：`/actuator/health/liveness`
- 非 root 用户、只读根文件系统、资源 request/limit
- `app.kubernetes.io/version` 版本标签和 `SERVICE_VERSION` 运行时版本

应用可通过 `/actuator/info` 查询服务名、应用版本和契约版本。端口覆盖变量使用
`LUMALIFE_IDENTITY_PORT`、`LUMALIFE_MERCHANT_PORT`、`LUMALIFE_ORDER_PORT`，避免与 Kubernetes
自动生成的 `*_SERVICE_PORT` 环境变量冲突；Pod 同时关闭了旧式 Service Link 注入。

## 增量选择规则

流水线 `.github/workflows/services-cd.yml` 先执行 `scripts/detect-changed-services.sh`，再把返回的 JSON
数组作为 GitHub Actions 动态 matrix。因此构建和部署使用同一份服务列表，不会产生“构建了 A、却部署 B”的漂移。

| 变化路径 | 受影响服务 |
|---|---|
| `services/identity-service/**`、`k8s/services/identity-service.yaml` | identity-service |
| `services/merchant-service/**`、`k8s/services/merchant-service.yaml` | merchant-service |
| `services/order-service/**`、`k8s/services/order-service.yaml` | order-service |
| `services/pom.xml`、`k8s/healthcheck/**`、增量流水线或其检测/部署脚本 | 全部三个服务 |
| 前端或普通文档等无关路径 | 无，不构建也不部署微服务 |

Pull Request 只构建受影响镜像而不推送，并在一次性 Kind 集群执行相同服务的 smoke test。Kind 健康检查
镜像固定使用 `linux/amd64`、关闭 provenance 并以 `--load` 载入本机，避免多架构 manifest list 在
side-load 到 containerd 时出现缺失 digest。合入 `main` 后，受影响镜像以不可变的 `sha-<7位提交号>`
标签推送到 GHCR，只有 Kind smoke 成功后才会部署相同服务。

部署脚本不会先应用 `0.1.0` 再执行 `kubectl set image`。它为每个服务创建临时 Kustomize overlay，提前写入
目标镜像、`SERVICE_VERSION` 和 Service/Deployment/Pod 版本标签，核对渲染镜像后一次性 `kubectl apply -k`。
因此首次创建和后续升级都只产生目标版本的 ReplicaSet。

## 本地实操

### 1. 测试路径过滤

```bash
bash scripts/test-detect-changed-services.sh
printf 'services/identity-service/src/main/java/App.java\n' \
  | bash scripts/detect-changed-services.sh --stdin
```

第二条命令应输出：

```json
["identity-service"]
```

### 2. 验证服务与 Kubernetes 清单

```bash
mvn -B -ntp -f services/pom.xml verify
kubectl kustomize k8s > rendered-k8s.yaml
```

### 3. 独立构建镜像

Docker 构建上下文必须是 `services/`，因为每个服务都继承同级父 POM：

```bash
docker build -f services/identity-service/Dockerfile -t lumalife-identity-service:dev services
docker build -f services/merchant-service/Dockerfile -t lumalife-merchant-service:dev services
docker build -f services/order-service/Dockerfile -t lumalife-order-service:dev services
```

Dockerfile 使用 BuildKit Maven 缓存。第一个服务下载依赖后，其他服务可复用缓存。

### 4. 构建 Kind 单平台健康检查镜像

```bash
docker buildx build \
  --platform linux/amd64 \
  --provenance=false \
  --load \
  -f k8s/healthcheck/Dockerfile \
  -t lumalife-healthcheck:kind .

kind load docker-image --name lumalife-ci lumalife-healthcheck:kind
```

### 5. 部署指定服务

例如只部署 identity 和 order：

```bash
IMAGE_REGISTRY=ghcr.io/daihao007 \
  bash scripts/deploy-services-k8s.sh sha-abcdef0 identity-service order-service
```

生产环境需要在 GitHub 的 `kubernetes` Environment 中配置 `KUBE_CONFIG_BASE64`；工作流不会在
Pull Request 中部署，也不会在没有受影响服务时运行镜像或部署 Job。

## 2026-08-31 验收记录

- `actionlint .github/workflows/services-cd.yml`：通过。
- `bash scripts/test-detect-changed-services.sh`：6 组路径映射全部通过。
- `mvn -B -ntp -f services/pom.xml verify`：3 个服务健康测试全部通过，0 失败。
- `kubectl kustomize k8s`：成功渲染，包括 3 个新增 Deployment 和 3 个新增 Service。
- 三个 Dockerfile 均完成真实镜像构建；三个容器本地 readiness 均为 `UP`，`/actuator/info` 返回对应服务名和 `0.1.0`。
- 一次性 Kind 集群完成三服务部署；identity、merchant、order 均为 `2/2 READY`，镜像标签和 Deployment
  版本均为 `issue45`，三个集群内 readiness 检查全部通过。验收后临时集群已删除。
- 原子部署复验中每个服务只有 1 个目标 ReplicaSet，首次创建直接使用 `issue45` 镜像；集群内没有
  `Failed` 事件，也没有尝试拉取默认 `0.1.0` 镜像。

最终部署摘要：

```text
identity-service   2/2   ghcr.io/daihao007/lumalife-identity-service:issue45
merchant-service   2/2   ghcr.io/daihao007/lumalife-merchant-service:issue45
order-service      2/2   ghcr.io/daihao007/lumalife-order-service:issue45
Incremental rollout passed for: identity-service merchant-service order-service (issue45).
```
