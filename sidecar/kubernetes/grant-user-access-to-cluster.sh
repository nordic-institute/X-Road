#!/bin/bash

CLUSTER_NAME=$1
REGION_CODE=$2
USER_NAME=$3
USER_ARN=$4

ROLE="    - rolearn: $USER_ARN\n      username: $USER_NAME\n      groups:\n        - system:masters\n        - system:nodes"

CONFIG_MAP=kubectl get -n kube-system configmap/aws-auth -o yaml

echo "THIS IS THE $CONFIG_MAP"

if [[ CONFIG_MAP != *"mapUsers:"* ]]; then
  echo "Its not there!"
fi

#kubectl get -n kube-system configmap/aws-auth -o yaml | awk "/mapRoles: \|/{print;print \"$ROLE\";next}1" > /tmp/aws-auth-patch.yml

#kubectl patch configmap/aws-auth -n kube-system --patch "$(cat /tmp/aws-auth-patch.yml)"
