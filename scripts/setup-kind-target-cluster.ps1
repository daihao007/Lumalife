[CmdletBinding()]
param(
    [string]$KubeconfigPath = "$env:USERPROFILE\.kube\lumalife-deployer.yaml"
)

$ErrorActionPreference = "Stop"

$dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
$dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
$kubectl = Join-Path $dockerBin "kubectl.exe"
$env:Path = "$dockerBin;$env:Path"

if (-not (Test-Path -LiteralPath $kubectl)) {
    throw "kubectl was not found at $kubectl. Install Docker Desktop first."
}

& (Join-Path $dockerBin "docker.exe") info *> $null
if ($LASTEXITCODE -ne 0) {
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw "Docker Desktop is not installed."
    }
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    foreach ($attempt in 1..60) {
        Start-Sleep -Seconds 2
        & (Join-Path $dockerBin "docker.exe") info *> $null
        if ($LASTEXITCODE -eq 0) { break }
    }
    if ($LASTEXITCODE -ne 0) { throw "Docker Desktop did not become ready." }
}

$kindCommand = Get-Command kind -ErrorAction SilentlyContinue
if ($kindCommand) {
    $kind = $kindCommand.Source
} else {
    $kind = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter kind.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $kind) {
    throw "kind is not installed. Run: winget install --exact --id Kubernetes.kind"
}

function Import-KindImage {
    param([Parameter(Mandatory = $true)][string]$Image)

    & (Join-Path $dockerBin "docker.exe") pull --platform linux/amd64 $Image
    if ($LASTEXITCODE -ne 0) { throw "Failed to pull $Image." }
    & $kind load docker-image --name lumalife $Image
    if ($LASTEXITCODE -eq 0) { return }

    # Some multi-architecture image indexes cannot be imported by `kind load`.
    # Import only the node's linux/amd64 variant as a compatibility fallback.
    $archive = Join-Path ([IO.Path]::GetTempPath()) "lumalife-kind-image.tar"
    try {
        & (Join-Path $dockerBin "docker.exe") save --output $archive $Image
        if ($LASTEXITCODE -ne 0) { throw "Failed to export $Image." }
        $archiveForDocker = $archive.Replace("\", "/")
        & (Join-Path $dockerBin "docker.exe") cp $archiveForDocker "lumalife-control-plane:/var/local/lumalife-kind-image.tar"
        if ($LASTEXITCODE -ne 0) { throw "Failed to copy $Image into the Kind node." }
        & (Join-Path $dockerBin "docker.exe") exec --privileged lumalife-control-plane ctr --namespace=k8s.io images import --platform linux/amd64 --snapshotter=overlayfs /var/local/lumalife-kind-image.tar
        if ($LASTEXITCODE -ne 0) { throw "Failed to import $Image into the Kind node." }
    } finally {
        if (Test-Path -LiteralPath $archive) { [IO.File]::Delete($archive) }
    }
}

$clusters = @(& $kind get clusters 2>$null)
if ($clusters -notcontains "lumalife") {
    & $kind create cluster --config "k8s/kind/cluster.yaml" --wait 180s
    if ($LASTEXITCODE -ne 0) { throw "Failed to create the Kind cluster." }
}

Import-KindImage -Image "curlimages/curl:8.12.1"

& $kubectl --context kind-lumalife apply -f k8s/namespace.yaml
& $kubectl --context kind-lumalife apply -f k8s/bootstrap/deployer-rbac.yaml
if ($LASTEXITCODE -ne 0) { throw "Failed to bootstrap the deployment identity." }

$tokenBase64 = ""
foreach ($attempt in 1..30) {
    $tokenBase64 = & $kubectl --context kind-lumalife -n lumalife get secret lumalife-deployer-token -o "jsonpath={.data.token}"
    if ($tokenBase64) { break }
    Start-Sleep -Seconds 1
}
if (-not $tokenBase64) { throw "The service-account token was not populated." }

$caData = & $kubectl --context kind-lumalife config view --raw --minify -o "jsonpath={.clusters[0].cluster.certificate-authority-data}"
$server = & $kubectl --context kind-lumalife config view --raw --minify -o "jsonpath={.clusters[0].cluster.server}"
$token = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($tokenBase64))

$kubeconfig = @"
apiVersion: v1
kind: Config
clusters:
  - name: kind-lumalife
    cluster:
      server: $server
      certificate-authority-data: $caData
contexts:
  - name: lumalife-deployer@kind-lumalife
    context:
      cluster: kind-lumalife
      namespace: lumalife
      user: lumalife-deployer
current-context: lumalife-deployer@kind-lumalife
users:
  - name: lumalife-deployer
    user:
      token: $token
"@

$kubeconfigDirectory = Split-Path -Parent $KubeconfigPath
[IO.Directory]::CreateDirectory($kubeconfigDirectory) | Out-Null
$utf8NoBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($KubeconfigPath, $kubeconfig, $utf8NoBom)
$base64Path = "$KubeconfigPath.base64.txt"
$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KubeconfigPath))
[IO.File]::WriteAllText($base64Path, $base64, $utf8NoBom)

& $kubectl --kubeconfig $KubeconfigPath auth can-i patch deployment/backend -n lumalife | Out-Null
if ($LASTEXITCODE -ne 0) { throw "The generated deployment kubeconfig failed its RBAC check." }
& $kubectl --kubeconfig $KubeconfigPath apply -k k8s --dry-run=server | Out-Null
if ($LASTEXITCODE -ne 0) { throw "The generated deployment kubeconfig cannot apply the manifests." }

Write-Host "Kind target cluster is ready at $server"
Write-Host "Restricted kubeconfig: $KubeconfigPath"
Write-Host "KUBE_CONFIG_BASE64 value: $base64Path"
Write-Host "The credential value was not printed and is outside the repository."
