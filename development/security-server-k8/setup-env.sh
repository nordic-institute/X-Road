#!/bin/bash

#TODO add condition to check if tofu is present

# Create the cluster
kind create cluster -n xrd-local-cluster --config deployment/kind.config.yaml

kind load docker-image openbao:2

# Deploy an NGINX ingress
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for the ingress controller to become available
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

# Deploy the dataspace, type 'yes' when prompted
tofu -chdir=deployment init
tofu -chdir=deployment apply