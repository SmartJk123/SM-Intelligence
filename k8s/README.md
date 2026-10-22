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
k8s/
├── 00-namespace.yaml          # Defines isolated 'smi' namespace
├── 01-secrets.yaml            # Sensitive credentials (JWT secret, DB password, service tokens)
├── 02-configmap.yaml          # Twelve-Factor configuration & internal CoreDNS routing
├── 99-ingress.yaml            # Edge Ingress routing rules & proxy timeouts
├── kind-config.yaml           # Multi-port cluster definition for local simulation
├── deploy.sh                  # One-command automated deployment script
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

## Running on GitHub Codespaces (1-Command Launch)

1. Open a terminal in your Codespace.
2. Execute the deployment script:
   ```bash
   ./k8s/deploy.sh
   ```
3. Watch your pods come alive:
   ```bash
   kubectl get pods -n smi -w
   ```
