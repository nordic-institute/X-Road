#!/bin/bash
# Containerised message log archiver/cleanup script.
# Creates a Kubernetes Job to run the message-log-archiver container.
#
# Usage: message_log_archiver.sh archive <instanceId>
#        message_log_archiver.sh cleanup

abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

JOB_TEMPLATE="/etc/xroad/job-templates/job-template.yaml"

command="${1:-}"

if [[ "$command" != "archive" && "$command" != "cleanup" ]]; then
  echo "Usage: $0 archive <instanceId> | cleanup" >&2
  exit 1
fi

if [[ "$command" == "archive" ]]; then
  instance_identifier="${2:-}"
  if [[ -z "$instance_identifier" ]]; then
    echo "Usage: $0 archive <instanceId>" >&2
    exit 1
  fi
  cli_args="archive ${instance_identifier}"
else
  cli_args="cleanup"
fi

# Validate Kubernetes environment
if [ -z "${KUBERNETES_SERVICE_HOST:-}" ] && [ ! -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
  abort "Kubernetes environment not detected."
fi

if [ ! -f "$JOB_TEMPLATE" ]; then
  abort "Job template not found at ${JOB_TEMPLATE}."
fi

jobname="message-log-${command}-$(date +%Y%m%d%H%M%S)"

echo "Creating Kubernetes Job '${jobname}' for message log ${command}..."

sed -e "s|__JOBNAME__|${jobname}|g" \
    -e "s|__CLI_ARGS__|${cli_args}|g" \
    "$JOB_TEMPLATE" | kubectl apply -f -

if [ $? -ne 0 ]; then
  abort "Failed to create Job '${jobname}'."
fi

echo "Job '${jobname}' created. Waiting for completion..."

if ! kubectl wait --for=condition=complete job/"${jobname}" --timeout=600s; then
  pod=$(kubectl get pods --selector=job-name="${jobname}" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
  if [[ -n "$pod" ]]; then
    echo "--- Pod logs ---"
    kubectl logs "$pod" 2>/dev/null || true
  fi
  abort "Job '${jobname}' did not complete successfully within timeout."
fi

echo "Message log ${command} completed successfully."
