#!/bin/bash

echo "Waiting for OpenBao to be ready..."
for _ in $(seq 1 15); do
  bao status
  bao_status=$?
  if [[ "$bao_status" -eq 0 || "$bao_status" -eq 2 ]]; then # 0 - unsealed, 1 - error, 2 - sealed
    echo "OpenBao is ready."
    break
  fi
  sleep 1
done
