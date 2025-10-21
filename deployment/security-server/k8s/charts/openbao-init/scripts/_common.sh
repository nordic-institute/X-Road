#!/bin/bash

KUBE_TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
KUBE_CA="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
KUBE_API="https://kubernetes.default.svc"

bao_api() {
  local method=$1
  local endpoint=$2
  local payload=$3
  local token=$4
  local description=$5

  echo "[BAO] $description..." >&2

  local response=$(curl -s -k -w "\nHTTP_STATUS:%{http_code}" \
    --connect-timeout 5 \
    --retry 3 \
    --retry-delay 2 \
    -X "$method" \
    "$OPENBAO_ADDR$endpoint" \
    -H "Content-Type: application/json" \
    ${token:+-H "X-Vault-Token: $token"} \
    ${payload:+-d "$payload"})

  local curl_exit=$?
  if [ $curl_exit -ne 0 ]; then
    echo "[BAO] Connection failed (exit code: $curl_exit)" >&2
    return 1
  fi

  local http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d":" -f2)
  local body=$(echo "$response" | grep -v "HTTP_STATUS")

  echo "[BAO] $description - Status: $http_status" >&2
  echo "[BAO] $description - Response: $body" >&2

  if [ "$http_status" != "200" ] && [ "$http_status" != "204" ]; then
    return 1
  fi

  echo "$body"
}

# Kubernetes API call function
k8s_api() {
  local method=$1
  local endpoint=$2
  local payload=$3
  local description=$4

  echo "[K8S] $description..." >&2

  local response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
    -k --cacert "$KUBE_CA" \
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
