# D06 多服务 Kubernetes 清单与增量流水线

关联任务：[#45](https://github.com/daihao007/Lumalife/issues/45)

> 当前 main 收口口径（2026-09-02）：业务服务为 identity、merchant、order、assistant 四个独立 Spring Boot 服务；完整运行时应用镜像为 backend、frontend 加四个服务镜像，共 6 个。下文 2026-08-31 的三服务 Kind 数字属于历史验收快照，当前 CI/Kubernetes 结果见 [main CI](https://github.com/daihao007/Lumalife/actions/runs/33584079761)、[Kubernetes rollout smoke](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100105159011) 和 [Deploy Kubernetes](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100105958216)。内部 token/RabbitMQ 密码由 `lumalife-runtime` Secret 提供。

## 交付范围

| 服务 | 应用版本 | 容器端口 | 独立镜像 | Kubernetes 资源 |
|---|---|---:|---|---|
| identity-service | 0.1.0 | 8081 | `ghcr.io/daihao007/lumalife-identity-service` | Deployment + Service |
| merchant-service | 0.1.0 | 8082 | `ghcr.io/daihao007/lumalife-merchant-service` | Deployment + Service |
| order-service | 0.1.0 | 8083 | `ghcr.io/daihao007/lumalife-order-service` | Deployment + Service |
| assistant-service | 0.1.0 | 8084 | `ghcr.io/daihao007/lumalife-assistant-service` | Deployment + Service |

每个 Deployment 都配置了：

- `startupProbe`：`/actuator/health/liveness`
- `readinessProbe`：`/actuator/health/readiness`
- `livenessProbe`：`/actuator/health/liveness`
- 非 root 用户、只读根文件系统、资源 request/limit
- `app.kubernetes.io/version` 版本标签和 `SERVICE_VERSION` 运行时版本

应用可通过 `/actuator/info` 查询服务名、应用版本和契约版本。端口覆盖变量使用
`LUMALIFE_IDENTITY_HTTP_PORT`、`LUMALIFE_MERCHANT_HTTP_PORT`、`LUMALIFE_ORDER_HTTP_PORT`，避免与 Kubernetes
自动生成的 `*_SERVICE_PORT` 环境变量冲突；Pod 同时关闭了旧式 Service Link 注入。

## 增量选择规则

流水线 `.github/workflows/services-cd.yml` 先执行 `scripts/detect-changed-services.sh`，再把返回的 JSON
数组作为 GitHub Actions 动态 matrix。因此镜像构建和临时 Kind 冒烟测试使用同一份服务列表，不会产生
“构建了 A、却验证 B”的漂移。

| 变化路径 | 受影响服务 |
|---|---|
| `services/identity-service/**`、`k8s/services.yaml` | identity-service |
| `services/merchant-service/**`、`k8s/services.yaml` | merchant-service |
| `services/order-service/**`、`k8s/services.yaml` | order-service |
| `services/assistant-service/**`、`k8s/services.yaml` | assistant-service |
| `services/pom.xml`、`k8s/healthcheck/**`、增量流水线或其检测/冒烟脚本 | 全部四个服务 |
| 前端、普通文档等无关路径 | 无，不运行微服务增量作业 |

Pull Request 只构建受影响镜像而不推送，并在一次性 Kind 集群执行相同服务的 smoke test。Kind 健康检查
镜像固定使用 `linux/amd64`、关闭 provenance 并以 `--load` 载入本机，避免多架构 manifest list 在
side-load 到 containerd 时出现缺失 digest。合入 `main` 后，受影响镜像以不可变的 `sha-<7位提交号>`
标签推送到 GHCR；该工作流不连接或修改生产集群。

冒烟脚本不会先应用 `0.1.0` 再执行 `kubectl set image`。它以正式的 `k8s/services.yaml` 为唯一 Deployment
来源，再为每个服务创建临时 Kustomize overlay，提前写入
目标镜像、`SERVICE_VERSION` 和 Service/Deployment/Pod 版本标签，核对渲染镜像后一次性 `kubectl apply -k`。
因此首次创建和后续升级都只产生目标版本的 ReplicaSet。仓库不再维护 `k8s/services/*.yaml` 第二套
业务 Deployment；脚本会拒绝任何非 `kind-*` 的 kubectl context。

生产部署仍由主流水线统一负责：`Monolith CI` 生成并上传部署包，随后 `Deploy ECS K3s` 在 ECS 自托管
Runner 上调用 `scripts/deploy-k8s.sh`，应用正式的 `k8s/services.yaml`。增量验证工作流不再维护第二套
`KUBE_CONFIG_BASE64` 凭据或覆盖同名生产 Deployment。

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
kubectl kustomize k8s > rendered-services.yaml
```

### 3. 独立构建镜像

Docker 构建上下文必须是 `services/`，因为每个服务都继承同级父 POM：

```bash
docker build -f services/identity-service/Dockerfile -t lumalife-identity-service:dev services
docker build -f services/merchant-service/Dockerfile -t lumalife-merchant-service:dev services
docker build -f services/order-service/Dockerfile -t lumalife-order-service:dev services
docker build -f services/assistant-service/Dockerfile -t lumalife-assistant-service:dev services
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

### 5. 在临时 Kind 集群验证指定服务

当前 kubectl context 必须是 `kind-*`。例如只验证 identity 和 order：

```bash
IMAGE_REGISTRY=ghcr.io/daihao007 \
  bash scripts/smoke-services-k8s.sh sha-abcdef0 identity-service order-service
```

此命令仅用于一次性 Kind 集群。生产发布不要调用该脚本，应使用主流水线的 ECS K3s 部署链路。

## 2026-08-31 验收记录

- `actionlint .github/workflows/services-cd.yml`：通过。
- `bash scripts/test-detect-changed-services.sh`：8 组路径映射全部通过。
- `mvn -B -ntp -f services/pom.xml verify`：四个服务入口均纳入聚合验证；本段保留 2026-08-31 历史三服务 smoke 记录，当前 CI 结果见 [最新 main CI](https://github.com/daihao007/Lumalife/actions/runs/33584079761)。
- `kubectl kustomize k8s`：当前清单成功渲染，包含 backend、frontend、RabbitMQ、四个业务 Deployment 及三套 service DB。
- 2026-08-31 的三个服务 Dockerfile/readiness 记录属于历史快照；当前正式 CI 已构建并验证四个服务镜像，详见 [Microservice E2E job](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100104417404)。
- 一次性 Kind 集群完成三服务部署；identity、merchant、order 均为 `2/2 READY`，镜像标签和 Deployment
  版本均为 `issue45`，三个集群内 readiness 检查全部通过。验收后临时集群已删除。
- 原子部署复验中的 `issue45` 镜像和三服务 ReplicaSet 结论属于历史快照；当前部署仍以同一 `k8s/services.yaml` 为唯一来源，并保留六镜像版本化发布。集群内没有
  `Failed` 事件，也没有尝试拉取默认 `0.1.0` 镜像。

最终部署摘要：

```text
identity-service   2/2   ghcr.io/daihao007/lumalife-identity-service:issue45
merchant-service   2/2   ghcr.io/daihao007/lumalife-merchant-service:issue45
order-service      2/2   ghcr.io/daihao007/lumalife-order-service:issue45
Kind smoke test passed for: identity-service merchant-service order-service (issue45).
```
