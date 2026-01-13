{{- define "xroad.configmap.env" -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .root.Release.Name }}-{{ .service }}-env
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
data:
  {{- $env := .config.env }}
  {{- if .root.Values.jvmMetrics.enabled }}
    {{- $env = merge $env (dict "JAVA_TOOL_OPTIONS" (printf "-javaagent:/opt/jmx_prometheus_javaagent.jar=%d:/opt/jmx-exporter-config.yaml" (int .root.Values.jvmMetrics.jmxExporter.port))) }}
  {{- end }}
  {{- toYaml $env | nindent 2 }}

{{- end }}
