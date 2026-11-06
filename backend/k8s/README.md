# SM-Intelligence Kubernetes Architecture

This directory houses production-ready, declarative Kubernetes manifests for the **SM-Intelligence** distributed FinTech platform (11 microservices + Edge API Gateway).

## Architecture Overview

```text
               External Clients (Web, Mobile, Admin)
                               │ (Port 80/443)
                               ▼
                    ┌─────────────────────┐
                    │     smi-ingress     │ (NGINX Ingress Controller)
             ═══════╡  (networking.k8s.io)╞═════════════════════════════
             ║      └──────────┬──────────┘                            ║
             ║                 │ (ClusterIP :8080)                     ║
             ║                 ▼                                       ║
             ║      ┌─────────────────────┐                            ║
             ║      │     api-gateway     │                            ║
             ║      └──────────┬──────────┘                            ║
             ║                 │ (CoreDNS Private Service Resolution)  ║
             ║         ┌───────┼───────┬──────────────┐                ║
             ║         ▼       ▼       ▼              ▼                ║
             ║     identity accounts transactions bank-integration     ║
             ║      (:8081)  (:8082)    (:8083)        (:8090)         ║
             ║                                                         ║
             ║      KUBERNETES PRIVATE CLUSTER (Namespace: smi)        ║
             ║      • Zero-Trust: Downstream services have no public IP║
             ║      • Twelve-Factor App: ConfigMaps & Secrets decoupled║
             ║      • Resilience: Actuator Liveness & Readiness Probes ║
             ═══════════════════════════════════════════════════════════
```

## Directory Structure

```text
backend/k8s/
├── 00-namespace.yaml          # Defines isolated 'smi' namespace
├── 01-secrets.yaml            # Sensitive credentials (JWT secret, DB password, service tokens)
├── 02-configmap.yaml          # Twelve-Factor configuration & internal CoreDNS routing
├── 99-ingress.yaml            # Edge Ingress routing rules & proxy timeouts
├── kind-config.yaml           # Multi-port cluster definition for local simulation
├── deploy.sh                  # One-command automated deployment script
├── build-all.sh               # Automated 12-microservice image builder & K3s/Kind importer
└── services/                  # Declarative Deployments & ClusterIP Services
    ├── api-gateway.yaml
    ├── identity-service.yaml
    ├── accounts-service.yaml
    ├── transactions-service.yaml
    ├── bank-integration-service.yaml
    ├── budgets-service.yaml
    ├── investments-service.yaml
    ├── categories-service.yaml
    ├── forecasts-service.yaml
    ├── notifications-service.yaml
    ├── audit-service.yaml
    └── assistant-service.yaml
```

## Production Cloud Deployment (K3s on Ubuntu / OCI ARM64)

1. **Install K3s (Lightweight Production Kubernetes)**:
   ```bash
   curl -sfL https://get.k3s.io | sh -
   sudo chmod 644 /etc/rancher/k3s/k3s.yaml
   export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
   echo 'export KUBECONFIG=/etc/rancher/k3s/k3s.yaml' >> ~/.bashrc
   ```

2. **Apply Platform Manifests & Secrets**:
   ```bash
   kubectl apply -f backend/k8s/00-namespace.yaml
   kubectl apply -f backend/k8s/02-configmap.yaml
   kubectl apply -f backend/k8s/services/
   kubectl apply -f backend/k8s/99-ingress.yaml
   ```

3. **Build & Import the Entire 12-Service Fleet**:
   ```bash
   ./backend/k8s/build-all.sh
   ```

4. **Verify Fleet Health**:
   ```bash
   kubectl get pods -n smi -o wide
   ```

## Running on Local / Codespaces (Kind Simulation)

1. Execute the deployment script:
   ```bash
   ./backend/k8s/deploy.sh
   ```
2. Build and load the fleet:
   ```bash
   ./backend/k8s/build-all.sh
   ```
3. Watch pods:
   ```bash
   kubectl get pods -n smi -w
   ```
