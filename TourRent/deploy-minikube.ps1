param([string]$Profile='tourrent-pr8')
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    minikube start -p $Profile --driver=docker --memory=5120 --cpus=8 --kubernetes-version=v1.32.0 --keep-context
    if ($LASTEXITCODE -ne 0) { throw 'Minikube failed to start' }
    docker compose build
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    $images=@('discovery-server','config-server','catalog-service','rental-service','logistics-service','api-gateway') | ForEach-Object { "tourrent/${_}:1.0.0" }
    foreach ($image in @('postgres:17-alpine','rabbitmq:4.1-management-alpine','quay.io/keycloak/keycloak:26.0.7')) {
        docker pull $image
        if ($LASTEXITCODE -ne 0) { throw "Image pull failed: $image" }
        $images+= $image
    }
    & "$PSScriptRoot/tools/load-images.ps1" -Profile $Profile -Images $images
    kubectl --context=$Profile apply -f k8s/tourrent.json
    if ($LASTEXITCODE -ne 0) { throw 'Kubernetes apply failed' }
    kubectl --context=$Profile -n tourrent wait --for=condition=available deployment --all --timeout=600s
    if ($LASTEXITCODE -ne 0) { throw 'Some deployments are not ready; inspect kubectl logs' }
    Write-Host 'Cluster ready. Stop Compose before forwarding ports 8078 and 8180.'
    Write-Host "kubectl --context=$Profile -n tourrent port-forward svc/api-gateway 8078:8080"
    Write-Host "kubectl --context=$Profile -n tourrent port-forward svc/keycloak 8180:8080"
} finally { Pop-Location }
