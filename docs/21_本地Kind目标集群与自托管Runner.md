# 本地 Kind 目标集群与自托管 Runner

> 可选的校园网长期运行方案：主线 CI 验收部署当前使用 GitHub 托管 Runner 上的一次性 Kind，不依赖本文的本机集群或自托管 Runner。

## 目的

任务 #40 的真实部署作业不能使用本机 Kind 的 kubeconfig 连接 GitHub 托管 Runner，因为 API 地址是本机 `127.0.0.1`。本方案让部署作业运行在同一台 Windows 机器上的 GitHub 自托管 Runner，其余测试、镜像构建和 Kind 冒烟仍使用 GitHub 托管 Runner。

## 集群配置

- 集群名：`lumalife`
- API：`https://127.0.0.1:6443`
- Kubernetes：`v1.37.0`
- 工作负载命名空间：`lumalife`
- 部署身份：`system:serviceaccount:lumalife:lumalife-deployer`

部署身份只具备维护 `lumalife` Namespace、Deployment、Service 和健康检查 Pod 所需的权限，不使用 Kind 管理员 kubeconfig。

## 创建或修复集群

先启动 Docker Desktop，然后从仓库根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-kind-target-cluster.ps1
```

脚本可重复执行，并在仓库外生成：

- `%USERPROFILE%\.kube\lumalife-deployer.yaml`
- `%USERPROFILE%\.kube\lumalife-deployer.yaml.base64.txt`

第二个文件的完整内容就是 GitHub Environment Secret `KUBE_CONFIG_BASE64` 的值。不要把这两个文件加入 Git、Issue、PR 或 Actions 日志。

脚本还会预载健康检查镜像。真实部署作业会先通过宿主 Docker 拉取本次 `sha-*` 镜像并载入 Kind，从而避免容器节点错误继承宿主机环回代理地址。

## GitHub 管理员配置

仓库管理员需要执行两项一次性操作：

1. 在 `Settings -> Actions -> Runners` 新增 Windows x64 自托管 Runner，并额外添加标签 `lumalife-k8s`。Runner 必须安装在运行该 Kind 集群的同一台机器上。建议安装到 `C:\actions-runner`，避免 Windows 服务身份权限和长路径问题。
2. 在名为 `kubernetes` 的 Environment 中创建或更新 `KUBE_CONFIG_BASE64`，值取自上述 `.base64.txt` 文件。

建议为 `kubernetes` Environment 配置 required reviewers，并确保只有受保护的 `main` 分支能够部署。Docker Desktop 与自托管 Runner 需要在部署期间保持运行。

### Runner 注册要点

从 GitHub Runner 配置页复制当前版本的下载和校验命令。解压后，使用页面刚生成的一次性令牌注册：

```powershell
Set-Location C:\actions-runner
.\config.cmd --url https://github.com/daihao007/Lumalife --token <NEW_ONE_TIME_TOKEN> --labels lumalife-k8s
.\run.cmd
```

- 不要把注册令牌写入文档、脚本、Issue 或提交；令牌暴露后应立即重新生成。
- 工作流要求 `self-hosted`、`windows`、`x64` 和 `lumalife-k8s` 四个标签；缺少自定义标签时，部署作业会一直排队。
- 部署步骤使用 Windows 自带的 `powershell.exe`，不要仅为 Runner 额外安装 PowerShell 7。
- `run.cmd` 必须在部署期间持续运行；如需无人值守，应按 Runner 配置向导将其安装为 Windows 服务。

## 验证

```powershell
$kubectl = "C:\Program Files\Docker\Docker\resources\bin\kubectl.exe"
& $kubectl --kubeconfig "$env:USERPROFILE\.kube\lumalife-deployer.yaml" get namespace lumalife
& $kubectl --kubeconfig "$env:USERPROFILE\.kube\lumalife-deployer.yaml" auth can-i create pods -n lumalife
& $kubectl --kubeconfig "$env:USERPROFILE\.kube\lumalife-deployer.yaml" auth can-i delete namespaces
```

预期结果依次为 Namespace `Active`、`yes`、`no`。
