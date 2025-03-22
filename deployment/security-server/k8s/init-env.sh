#!/bin/bash

tofu -chdir=terraform/environments/dev init

echo "Destroying existing environment..."
tofu -chdir=terraform/environments/dev destroy

echo "Initializing environment..."
tofu -chdir=terraform/environments/dev apply --auto-approve
