# templates/_helpers.tpl
{{/* Add these additional helper templates */}}
{{/*
Common labels
*/}}
{{- define "vault-init.labels" -}}
helm.sh/chart: {{ include "vault-init.chart" . }}
{{ include "vault-init.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "vault-init.selectorLabels" -}}
app.kubernetes.io/name: {{ include "vault-init.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "vault-init.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}
