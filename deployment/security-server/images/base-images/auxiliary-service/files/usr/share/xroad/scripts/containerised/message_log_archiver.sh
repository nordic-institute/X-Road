#!/bin/bash
# Containerised message log archiver/cleanup script.
# Creates a Kubernetes Job to run the message-log-archiver container.
#
# Usage: message_log_archiver.sh archive <instanceId>
#        message_log_archiver.sh cleanup

abort() { echo "FATAL: $*" >&2; exit 1; }

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

# Check if there is already an active (running/pending) job for this command.
# Jobs with status.active > 0 have pods that are still running or pending.
active_jobs=$(kubectl get jobs -o jsonpath='{range .items[?(@.status.active)]}{.metadata.name}{"\n"}{end}' 2>/dev/null \
  | grep -c "^message-log-${command}-" || true)

if [[ "$active_jobs" -gt 0 ]]; then
  echo "A message-log ${command} job is already running. Skipping."
  exit 0
fi

jobname="message-log-${command}-$(date +%Y%m%d%H%M%S)"

echo "Creating Kubernetes Job '${jobname}' for message log ${command}..."

if ! sed -e "s|__JOBNAME__|${jobname}|g" \
    -e "s|__CLI_ARGS__|${cli_args}|g" \
    "$JOB_TEMPLATE" | kubectl apply -f -; then
  abort "Failed to create Job '${jobname}'."
fi

echo "Job '${jobname}' created successfully. Waiting for completion..."

poll_interval_seconds="${XROAD_ARCHIVER_POLL_INTERVAL_SECONDS:-5}"
max_wait_seconds="${XROAD_ARCHIVER_MAX_WAIT_SECONDS:-21900}"
waited_seconds=0
poll_failures=0

while true; do
  # Terminal Job conditions are retry-agnostic, unlike pod success/failure counts.
  if conditions=$(kubectl get "job/${jobname}" \
      -o jsonpath='{range .status.conditions[?(@.status=="True")]}{.type}{"\n"}{end}' 2>&1); then
    poll_failures=0
    if grep -q '^Complete$' <<<"$conditions"; then
      echo "Job '${jobname}' completed successfully."
      exit 0
    fi
    if grep -q '^Failed$' <<<"$conditions"; then
      echo "Job '${jobname}' failed." >&2
      kubectl logs "job/${jobname}" --tail=50 >&2 2>/dev/null \
        || kubectl describe "job/${jobname}" >&2 2>/dev/null
      exit 1
    fi
  else
    case "$conditions" in *NotFound*) abort "Job '${jobname}' no longer exists." ;; esac
    poll_failures=$((poll_failures + 1))
    if [[ "$poll_failures" -ge 12 ]]; then
      abort "Cannot query Job '${jobname}': ${conditions}"
    fi
  fi

  if [[ "$waited_seconds" -ge "$max_wait_seconds" ]]; then
    kubectl delete "job/${jobname}" >&2 2>/dev/null
    abort "Job '${jobname}' did not reach a terminal state within ${max_wait_seconds}s."
  fi

  sleep "$poll_interval_seconds"
  waited_seconds=$((waited_seconds + poll_interval_seconds))
done
