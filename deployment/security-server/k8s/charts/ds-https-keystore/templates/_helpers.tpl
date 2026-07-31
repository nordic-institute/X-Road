{{/*
Common labels — includes a fixed marker label so every rendered resource
self-identifies as non-production (mirrors the Chart.yaml annotation).
*/}}
{{- define "xroad.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
xroad.niis.org/production-ready: "false"
{{- end }}

{{/*
Builds the openssl `subjectAltName` value for the keystore cert: `commonName`
plus every entry in `extraSanDnsNames`, each prefixed `DNS:`.
*/}}
{{- define "dsHttpsKeystore.sanList" -}}
{{- $names := prepend .Values.dsHttpsKeystore.extraSanDnsNames .Values.dsHttpsKeystore.commonName -}}
{{- $dns := list -}}
{{- range $names }}{{ $dns = append $dns (printf "DNS:%s" .) }}{{ end -}}
{{- join "," $dns -}}
{{- end -}}
