#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="smi-cluster"

echo "=== [1/5] Checking Kubernetes Tooling ==="
if ! command -v kind &> /dev/null; then
  echo "Installing Kind..."
  curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.27.0/kind-linux-amd64
  chmod +x ./kind
  sudo mv ./kind /usr/local/bin/kind
fi

if ! command -v kubectl &> /dev/null; then
  echo "Installing kubectl..."
  curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
  chmod +x ./kubectl
  sudo mv ./kubectl /usr/local/bin/kubectl
fi

echo "=== [2/5] Initializing Kind Cluster: ${CLUSTER_NAME} ==="
if ! kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
  kind create cluster --name "${CLUSTER_NAME}" --config "${SCRIPT_DIR}/kind-config.yaml"
else
  echo "Cluster ${CLUSTER_NAME} already exists."
fi

echo "=== [3/5] Applying Kubernetes Manifests ==="
kubectl apply -f "${SCRIPT_DIR}/00-namespace.yaml"
kubectl apply -f "${SCRIPT_DIR}/01-secrets.yaml"
kubectl apply -f "${SCRIPT_DIR}/02-configmap.yaml"
kubectl apply -f "${SCRIPT_DIR}/services/"
kubectl apply -f "${SCRIPT_DIR}/99-ingress.yaml"

echo "=== [4/5] Cluster Status in Namespace 'smi' ==="
kubectl get all -n smi

echo ""
echo "=== [5/5] Success! Deployment completed ==="
echo "To monitor your microservice pods in real time, run:"
echo "  kubectl get pods -n smi -w"
