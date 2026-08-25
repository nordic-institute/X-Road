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
  {{- $env := .config.env }}
  {{- /*
  Names the co-located Issuer Service advertises to callers in other namespaces
  (EDC-read env; still live as env, unlike xroad.* keys). These are separate
  from XROAD_HOST on purpose: XROAD_HOST is the CS's own identity (the
  management-service cert's common name), while these end up in artifacts other
  namespaces have to resolve — the issuance endpoint the Security Servers'
  identity hubs dial, and the status-list URL baked into issued credentials.
  The xroad.* counterparts that used to sit here — the management-service cert
  alt-names and xroad.dataspace.issuer.host — are DSL keys the config layer no
  longer reads from env; they ride db-config-seed-configmap.yaml instead.
  Defaults only: a values-supplied value wins.
  */}}
  {{- if eq .service "central-server" }}
  {{- $svcFqdn := printf "%s.%s" .service .root.Release.Namespace }}
  {{- $issuerEnv := dict
      "EDC_HOSTNAME" $svcFqdn
      "EDC_STATUSLIST_CALLBACK_ADDRESS" (printf "https://%s:6187/statuslist" $svcFqdn) }}
  {{- $env = merge $env $issuerEnv }}
  {{- end }}
  {{- toYaml $env | nindent 2 }}
{{- end }}
