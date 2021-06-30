#!/bin/bash

USER_NAME=$1
USER_ARN=$2

kubectl get -n kube-system configmap/aws-auth -o yaml > /tmp/aws-auth-patch.yml

isInFile=$(cat /tmp/aws-auth-patch.yml | grep -c "mapUsers")

# Add the IAM user to the aws-auth ConfigMap to operate the EKS cluster nodes
ROLE="    - userarn: $USER_ARN\n      username: $USER_NAME\n      groups:\n        - system:masters\n        - system:nodes"
if [ $isInFile -eq 0 ]; then
    kubectl get -n kube-system configmap/aws-auth -o yaml | awk "/^data:/{print;print \"  mapUsers: \| \n$ROLE\";next}1" > /tmp/aws-auth-patch.yml
else
    kubectl get -n kube-system configmap/aws-auth -o yaml | awk "/mapUsers: \|/{print;print \"$ROLE\";next}1" > /tmp/aws-auth-patch.yml
fi

kubectl patch configmap/aws-auth -n kube-system --patch "$(cat /tmp/aws-auth-patch.yml)"
