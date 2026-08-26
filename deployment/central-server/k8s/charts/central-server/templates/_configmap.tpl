{{/*
Env ConfigMap for a service. No jvmHeap/jvmMetrics/otel knobs: the all-in-one
image starts its JVMs under supervisord, which ignores them.
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
  Issuer Service names advertised to other namespaces — deliberately separate
  from XROAD_HOST (the CS's own identity). Defaults only: values-supplied env wins.
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
