# Kubernetes Deployment Guide

이 디렉토리는 MSA-Webtoon 프로젝트의 Kubernetes 배포 관련 스크립트와 설정을 포함합니다.

## 목차

1. [사전 준비](#사전-준비)
2. [Docker Registry 설정](#docker-registry-설정)
3. [로컬 개발 환경 (Minikube/k3s)](#로컬-개발-환경)
4. [프로덕션 배포](#프로덕션-배포)
5. [CI/CD 파이프라인](#cicd-파이프라인)
6. [트러블슈팅](#트러블슈팅)

## 사전 준비

### 필수 도구

- **kubectl**: Kubernetes CLI
- **helm**: Kubernetes 패키지 매니저
- **Docker**: 컨테이너 빌드 및 실행
- **Minikube** 또는 **k3s**: 로컬 Kubernetes 클러스터 (개발용)

### 설치 (Windows)

```powershell
# Chocolatey를 사용한 설치
choco install kubernetes-cli kubernetes-helm minikube docker-desktop

# 또는 Scoop 사용
scoop install kubectl helm minikube
```

### 설치 (Linux/macOS)

```bash
# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Minikube (Linux)
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# k3s (Linux, 더 가벼움)
curl -sfL https://get.k3s.io | sh -
```

## Docker Registry 설정

### 1. Docker Hub 사용

가장 간단한 방법은 Docker Hub를 사용하는 것입니다.

#### 이미지 푸시

```bash
# 로그인
docker login

# 이미지 빌드 및 푸시 (Jib 사용)
./gradlew :services:api-gateway:jib \
  -Djib.to.image=docker.io/yourusername/msa-webtoon-api-gateway:0.1.0

# 모든 서비스 빌드 및 푸시
./gradlew jib \
  -Djib.to.image=docker.io/yourusername/msa-webtoon
```

#### Kubernetes Secret 생성

```bash
# Linux/macOS
export DOCKER_USERNAME="yourusername"
export DOCKER_PASSWORD="your-access-token"
./platform/k8s/setup-registry-secret.sh

# Windows PowerShell
$env:DOCKER_USERNAME = "yourusername"
$env:DOCKER_PASSWORD = "your-access-token"
.\platform\k8s\setup-registry-secret.ps1
```

**참고**: Docker Hub에서는 보안을 위해 패스워드 대신 Access Token 사용을 권장합니다.
- https://hub.docker.com/settings/security 에서 토큰 생성

### 2. GitHub Container Registry (GHCR) 사용

```bash
# 로그인
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# 이미지 빌드 및 푸시
./gradlew :services:api-gateway:jib \
  -Djib.to.image=ghcr.io/yourorg/msa-webtoon-api-gateway:0.1.0

# Kubernetes Secret 생성
export DOCKER_REGISTRY="ghcr.io"
export DOCKER_USERNAME="your-github-username"
export DOCKER_PASSWORD="your-github-token"
./platform/k8s/setup-registry-secret.sh
```

### 3. 프라이빗 Registry (Harbor 등) 사용

```bash
# 로그인
docker login registry.example.com

# 이미지 빌드 및 푸시
./gradlew :services:api-gateway:jib \
  -Djib.to.image=registry.example.com/msa-webtoon/api-gateway:0.1.0

# Kubernetes Secret 생성
export DOCKER_REGISTRY="registry.example.com"
export DOCKER_USERNAME="your-username"
export DOCKER_PASSWORD="your-password"
./platform/k8s/setup-registry-secret.sh
```

## 로컬 개발 환경

### Minikube 시작

```bash
# Minikube 시작 (Docker 드라이버 사용)
minikube start --driver=docker --cpus=4 --memory=8192

# 또는 k3s 사용
sudo k3s server &
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
```

### 로컬 이미지 빌드 및 배포

#### Option 1: Minikube Docker 데몬 사용

```bash
# Minikube Docker 데몬 사용
eval $(minikube docker-env)

# 로컬에서 이미지 빌드 (Jib로 로컬 Docker에 빌드)
./gradlew :services:api-gateway:jibDockerBuild

# Helm으로 배포 (imagePullPolicy를 Never로 설정)
helm upgrade --install api-gateway platform/helm/charts/api-gateway \
  --namespace msa-webtoon \
  --create-namespace \
  --values platform/helm/charts/api-gateway/values-dev.yaml \
  --set image.pullPolicy=Never
```

#### Option 2: 로컬 레지스트리 사용

```bash
# 로컬 레지스트리 실행
docker run -d -p 5000:5000 --name registry registry:2

# 이미지 빌드 및 푸시
./gradlew :services:api-gateway:jib \
  -Djib.to.image=localhost:5000/msa-webtoon/api-gateway:dev

# Helm으로 배포
helm upgrade --install api-gateway platform/helm/charts/api-gateway \
  --namespace msa-webtoon \
  --create-namespace \
  --values platform/helm/charts/api-gateway/values-dev.yaml \
  --set image.repository=localhost:5000/msa-webtoon/api-gateway \
  --set image.tag=dev
```

### 모든 서비스 배포

```bash
# 모든 서비스 배포 스크립트
for service in api-gateway auth-service catalog-service event-ingest rank-service search-service; do
  helm upgrade --install $service platform/helm/charts/$service \
    --namespace msa-webtoon \
    --create-namespace \
    --values platform/helm/charts/$service/values-dev.yaml
done
```

### 서비스 접근

```bash
# Port-forward로 서비스 접근
kubectl port-forward -n msa-webtoon svc/api-gateway 8080:8080

# 또는 Minikube service 사용
minikube service api-gateway -n msa-webtoon
```

## 프로덕션 배포

### 1. 이미지 빌드 및 푸시

CI/CD 파이프라인에서 자동으로 수행되지만, 수동으로도 가능합니다:

```bash
# 버전 태그와 함께 모든 서비스 빌드
VERSION=0.1.0
./gradlew jib \
  -Djib.to.image=docker.io/yourusername/msa-webtoon \
  -Djib.to.tags=$VERSION,latest
```

### 2. Helm 배포

```bash
# 프로덕션 환경으로 배포
for service in api-gateway auth-service catalog-service event-ingest rank-service search-service; do
  helm upgrade --install $service platform/helm/charts/$service \
    --namespace msa-webtoon-prod \
    --create-namespace \
    --values platform/helm/charts/$service/values-prod.yaml \
    --set image.tag=0.1.0 \
    --wait \
    --timeout 5m
done
```

### 3. 배포 확인

```bash
# Pod 상태 확인
kubectl get pods -n msa-webtoon-prod

# 서비스 상태 확인
kubectl get svc -n msa-webtoon-prod

# Ingress 확인
kubectl get ingress -n msa-webtoon-prod

# 로그 확인
kubectl logs -n msa-webtoon-prod -l app.kubernetes.io/name=api-gateway --tail=100 -f
```

## CI/CD 파이프라인

### Jenkins 파이프라인

`ci/Jenkinsfile`에 정의된 파이프라인은 다음 단계를 수행합니다:

1. **Build**: 모든 서비스 컴파일
2. **Test**: 단위 테스트 및 커버리지 리포트 생성
3. **Build Images**: 7개 서비스를 병렬로 이미지 빌드 및 푸시
4. **Deploy to K8s**: main 브랜치에서만 자동 배포

### 환경 변수 설정

Jenkins에서 다음 환경 변수를 설정해야 합니다:

- `DOCKER_REGISTRY`: 레지스트리 주소 (예: `docker.io`, `ghcr.io`)
- `DOCKER_USERNAME`: 레지스트리 사용자명
- `DOCKER_PASSWORD`: 레지스트리 패스워드/토큰 (Credential로 관리)
- `KUBECTL_NAMESPACE`: 배포할 네임스페이스 (예: `msa-webtoon-prod`)

### Jenkins Credentials 설정

```groovy
// Jenkins credentials 추가
withCredentials([usernamePassword(
    credentialsId: 'docker-registry-credentials',
    usernameVariable: 'DOCKER_USERNAME',
    passwordVariable: 'DOCKER_PASSWORD'
)]) {
    // 이미지 빌드 및 푸시
}
```

## Registry별 설정 예제

### Docker Hub

```yaml
# Helm values
image:
  repository: yourusername/msa-webtoon-api-gateway
  tag: "0.1.0"

imagePullSecrets:
  - name: docker-registry-credentials
```

### GitHub Container Registry

```yaml
# Helm values
image:
  repository: ghcr.io/yourorg/msa-webtoon-api-gateway
  tag: "0.1.0"

imagePullSecrets:
  - name: ghcr-credentials
```

### Private Registry

```yaml
# Helm values
image:
  repository: registry.example.com/msa-webtoon/api-gateway
  tag: "0.1.0"

imagePullSecrets:
  - name: private-registry-credentials
```

## 트러블슈팅

### ImagePullBackOff 오류

```bash
# Pod 상태 확인
kubectl describe pod <pod-name> -n msa-webtoon

# 일반적인 원인:
# 1. 잘못된 이미지 이름/태그
# 2. Registry credentials 누락
# 3. Private 이미지인데 public으로 접근 시도

# 해결 방법:
# 1. 이미지 이름 확인
kubectl get pod <pod-name> -n msa-webtoon -o jsonpath='{.spec.containers[0].image}'

# 2. Secret 확인
kubectl get secret docker-registry-credentials -n msa-webtoon

# 3. Secret 재생성
./platform/k8s/setup-registry-secret.sh
```

### Pod가 시작되지 않음

```bash
# 로그 확인
kubectl logs <pod-name> -n msa-webtoon

# 이벤트 확인
kubectl get events -n msa-webtoon --sort-by='.lastTimestamp'

# 리소스 부족 확인
kubectl top nodes
kubectl top pods -n msa-webtoon
```

### Helm 차트 문제

```bash
# Helm 차트 검증
helm lint platform/helm/charts/api-gateway

# Dry-run으로 확인
helm install api-gateway platform/helm/charts/api-gateway \
  --namespace msa-webtoon \
  --dry-run --debug

# 배포된 차트 확인
helm list -n msa-webtoon
helm get values api-gateway -n msa-webtoon
```

## 추가 리소스

- [Kubernetes 공식 문서](https://kubernetes.io/docs/)
- [Helm 공식 문서](https://helm.sh/docs/)
- [Jib 문서](https://github.com/GoogleContainerTools/jib)
- [Docker Hub](https://hub.docker.com/)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
