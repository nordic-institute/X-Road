{{/*
Env ConfigMap for a service.

Deliberately simpler than the Security Server chart's version: this chart
has no jvmHeap/jvmMetrics/otel knobs yet because the all-in-one dev image
predates that per-service env convention (its JVMs are started by the
packaged xroad-centralserver install under supervisord, not by a
JAVA_TOOL_OPTIONS-aware entrypoint). Add those knobs back once the chart
gains real per-component services that honor them.
*/}}
{{- define "xroad.configmap.env" -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .root.Release.Name }}-{{ .service }}-env
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
data:
  {{- toYaml .config.env | nindent 2 }}
{{- end }}
