{{/*
Common DB init job template
*/}}
{{- define "xroad.db.init.job" -}}
{{- $jobName := .name }}
{{- $config := .config }}
{{- $root := .root }}
{{- if or (not .config.dsComponent) (and .config.dsComponent $root.Values.global.dataSpacesEnabled) }}
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ $jobName }}-db-init
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "-5"
    "helm.sh/hook-delete-policy": before-hook-creation,hook-succeeded
spec:
  backoffLimit: 3
  template:
    spec:
      containers:
        - name: {{ $jobName }}-db-init
          image: {{ $config.image }}
          imagePullPolicy: Never
          resources:
            requests:
              memory: "128Mi"
            limits:
              memory: "512Mi"
          env:
            - name: DB_HOST
              value: {{ $config.host | quote }}
            - name: DB_PORT
              value: {{ $config.port | quote }}
            - name: DB_NAME
              value: {{ $config.database | quote }}
            - name: DB_USERNAME
              value: "postgres"
            - name: DB_PASSWORD
              value: {{ $config.password | quote }}
            - name: db_schema
              value: {{ $config.schema | quote }}
            - name: db_user
              value: {{ $config.username | quote }}
      initContainers:
        - name: check-db-ready
          image: "postgres:17"
          command: ['sh', '-c',
                    'until pg_isready -h {{ $config.host }} -p {{ $config.port }}; do echo waiting for database; sleep 2; done;']
      restartPolicy: Never
{{- end }}
{{- end }}

{{/*
Generate all DB init jobs
*/}}
{{- define "xroad.db.init.all" -}}
{{- $root := . -}}

{{/* Regular components */}}
{{- include "xroad.db.init.job" (dict "root" $root "name" "serverconf" "config" .Values.init.serverconf) }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "messagelog" "config" .Values.init.messagelog) }}

{{/* Data Spaces components */}}
{{- if .Values.global.dataSpacesEnabled }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "ds-control-plane" "config" (merge .Values.init.dsControlPlane (dict "dsComponent" true))) }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "ds-data-plane" "config" (merge .Values.init.dsDataPlane (dict "dsComponent" true))) }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "ds-identity-hub" "config" (merge .Values.init.dsIdentityHub (dict "dsComponent" true))) }}
{{- end }}
{{- end }}
