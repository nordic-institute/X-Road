{{/*
Builds the openssl `subjectAltName` value for the DS-HTTPS keystore-init Job:
`commonName` plus every entry in `extraSanDnsNames`, each prefixed `DNS:`.
*/}}
{{- define "e2eFixtures.dsHttpsSanList" -}}
{{- $names := prepend .Values.dsHttpsKeystore.extraSanDnsNames .Values.dsHttpsKeystore.commonName -}}
{{- $dns := list -}}
{{- range $names }}{{ $dns = append $dns (printf "DNS:%s" .) }}{{ end -}}
{{- join "," $dns -}}
{{- end -}}
