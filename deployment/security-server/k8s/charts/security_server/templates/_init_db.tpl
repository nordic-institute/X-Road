{{/*
Common DB init job template
*/}}
{{- define "xroad.db.init.job" -}}
{{- $jobName := .name }}
{{- $config := .config }}
{{- $root := .root }}
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
          imagePullPolicy: {{ $config.imagePullPolicy }}
          resources:
            requests:
              memory: "128Mi"
            limits:
              memory: "512Mi"
          env:
            - name: LIQUIBASE_COMMAND_USERNAME
              value: "postgres"
            - name: LIQUIBASE_COMMAND_PASSWORD
              value: {{ $config.postgres_password | quote }}
            - name: LIQUIBASE_COMMAND_URL
              value: "jdbc:postgresql://{{ $config.host }}:{{ $config.port }}/{{ $config.database }}"
              {{ $config.url | quote }}
            - name: db_schema
              value: {{ $config.schema | quote }}
            - name: db_user
              value: {{ $config.db_username | quote }}
      initContainers:
        - name: check-db-ready
          image: "postgres:17"
          command: ['sh', '-c',
                    'until pg_isready -h {{ $config.host }} -p {{ $config.port }}; do echo waiting for database; sleep 2; done;']
      restartPolicy: Never
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
{{- end }}
