#!/bin/bash

if ! command -v tofu &>/dev/null; then
  echo "OpenTofu is not installed"
  exit 1
fi

if ! command -v kubectl &>/dev/null; then
  echo "Kubectl is not installed"
  exit 1
fi


tofu -chdir=terraform/environments/dev init

echo "Destroying existing dev environment..."
tofu -chdir=terraform/environments/dev destroy

echo "Initializing dev environment..."
tofu -chdir=terraform/environments/dev apply --auto-approve

echo "Forwarding port 4000 for proxy-ui-api and ports 5500, 5577, 8080 & 8443 for proxy to the host..."
kubectl port-forward service/proxy-ui-api 4000:4000 -n ss & \
kubectl port-forward service/proxy 5500:5500 5577:5577 8080:8080 8443:8443 -n ss & \
wait
