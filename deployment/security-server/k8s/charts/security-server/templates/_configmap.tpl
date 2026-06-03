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
  {{- $env = merge $env (dict "JAVA_MAX_RAM_PERCENTAGE" (printf "%v" .root.Values.jvmHeap.maxRAMPercentage)) }}
  {{- if .root.Values.jvmMetrics.enabled }}
    {{- $javaToolOpts := printf "-javaagent:/opt/jmx_prometheus_javaagent.jar=%d:/opt/jmx-exporter-config.yaml" (int .root.Values.jvmMetrics.jmxExporter.port) }}
    {{- $env = merge $env (dict "JAVA_TOOL_OPTIONS" $javaToolOpts) }}
  {{- end }}
  {{- if .root.Values.otel.enabled }}
    {{- $nodeId := .root.Values.otel.nodeId }}
    {{- $namespace := .root.Values.otel.resourceAttributes.serviceNamespace }}
    {{- $resourceAttrs := "" }}
    {{- if $nodeId }}
      {{- $namespace = $nodeId }}
      {{- $resourceAttrs = printf "service.namespace=%s,service.instance.id=%s,deployment.environment=%s" $namespace $nodeId .root.Values.otel.resourceAttributes.deploymentEnvironment }}
    {{- else }}
      {{- $resourceAttrs = printf "service.namespace=%s,deployment.environment=%s" $namespace .root.Values.otel.resourceAttributes.deploymentEnvironment }}
    {{- end }}
    {{- $otelEnv := dict
      "OTEL_SDK_DISABLED" "false"
      "OTEL_EXPORTER_OTLP_ENDPOINT" .root.Values.otel.endpoint
      "OTEL_EXPORTER_OTLP_PROTOCOL" .root.Values.otel.protocol
      "OTEL_TRACES_SAMPLER" .root.Values.otel.sampler
      "OTEL_LOGS_EXPORTER" "none"
      "OTEL_METRICS_EXPORTER" "none"
      "OTEL_RESOURCE_ATTRIBUTES" $resourceAttrs
    }}
    {{- if .config.otelServiceName }}
      {{- $svcName := .config.otelServiceName }}
      {{- if $nodeId }}
        {{- $svcName = printf "%s-%s" $svcName $nodeId }}
      {{- end }}
      {{- $otelEnv = merge $otelEnv (dict "OTEL_SERVICE_NAME" $svcName) }}
    {{- end }}
    {{- $env = merge $env $otelEnv }}
  {{- end }}
  {{- toYaml $env | nindent 2 }}

{{- end }}
