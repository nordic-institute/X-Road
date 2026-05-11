{{- define "xroad.db.init.all" -}}
{{- $root := . -}}
{{- include "xroad.db.init.job" (dict "root" $root "name" "confproxy" "config" .Values.init.confproxy) }}
{{- end }}
