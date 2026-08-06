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
  The shared management-service TLS cert (Vault xrd-secret/tls/management-service)
  is issued with common-name XROAD_HOST, i.e. the bare in-namespace service name.
  Callers in other namespaces reach this Service by its namespace-qualified DNS
  name, which the bare-CN SAN list doesn't cover, so Jetty's SNI check rejects the
  handshake. These extra SANs cover the qualified names.
  */}}
  {{- if eq .service "central-server" }}
  {{- $svcFqdn := printf "%s.%s" .service .root.Release.Namespace }}
  {{- $altNames := printf "%s,%s.svc.cluster.local" $svcFqdn $svcFqdn }}
  {{- $env = merge (dict "XROAD_MANAGEMENT_SERVICE_TLS_CERTIFICATE_PROVISIONING_ALT_NAMES" $altNames) $env }}
  {{- /*
  Names the co-located Issuer Service advertises to callers in other namespaces.
  These are separate from XROAD_HOST on purpose: XROAD_HOST is the CS's own
  identity (the management-service cert's common name, and the base for the
  alt-names above), while these three end up in artifacts other namespaces have
  to resolve — the issuer DID and issuance endpoint the Security Servers'
  identity hubs fetch, and the status-list URL baked into issued credentials.
  Defaults only: a values-supplied value wins.
  */}}
  {{- $issuerEnv := dict
      "XROAD_DATASPACE_ISSUER_HOST" $svcFqdn
      "EDC_HOSTNAME" $svcFqdn
      "EDC_STATUSLIST_CALLBACK_ADDRESS" (printf "https://%s:6187/statuslist" $svcFqdn) }}
  {{- $env = merge $env $issuerEnv }}
  {{- end }}
  {{- toYaml $env | nindent 2 }}
{{- end }}
