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
  {{- /*
  Only ds-control-plane reads the DSP participant-context id from env; the
  proxy's copy is seeded as a configuration row by init/config-seed-job.yaml.
  As merge dst, the override beats any per-service literal in values.yaml.
  */}}
  {{- if .root.Values.dsp.participantContextId }}
  {{- if eq .service "ds-control-plane" }}
  {{- $env = merge (dict "XROAD_DSP_PARTICIPANT_CONTEXT_ID" .root.Values.dsp.participantContextId) $env }}
  {{- end }}
  {{- end }}
  {{- /*
  The softtoken-signer consumer channel is not enabled here:
  xroad.common-rpc.channel.softtoken-signer.enabled is read from the
  database only, never from env — the config-seed Job appends the row; the
  channel host falls back to the "softtoken-signer" container default.
  */}}
  {{- $env = merge $env (dict "JAVA_MAX_RAM_PERCENTAGE" (printf "%v" .root.Values.jvmHeap.maxRAMPercentage)) }}
  {{- if .root.Values.jvmHeap.mallocArenaMax }}
  {{- $env = merge $env (dict "MALLOC_ARENA_MAX" (printf "%v" .root.Values.jvmHeap.mallocArenaMax)) }}
  {{- end }}
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
