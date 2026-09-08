#!/bin/bash

KUBE_TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
KUBE_CA="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
KUBE_API="https://kubernetes.default.svc"

# Kubernetes API call function
k8s_api() {
  local method=$1
  local endpoint=$2
  local payload=$3
  local description=$4

  echo "[K8S] $description..." >&2

  local response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
    --cacert "$KUBE_CA" \
    -H "Authorization: Bearer $KUBE_TOKEN" \
    -H "Content-Type: application/json" \
    -X "$method" \
    "$KUBE_API$endpoint" \
    ${payload:+-d "$payload"})

  local http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d":" -f2)
  local body=$(echo "$response" | grep -v "HTTP_STATUS")

  echo "[K8S] $description - Status: $http_status" >&2
  echo "[K8S] $description - Response: $body" >&2

  if [ "$http_status" != "200" ] && [ "$http_status" != "201" ]; then
    return 1
  fi

  echo "$body"
}
