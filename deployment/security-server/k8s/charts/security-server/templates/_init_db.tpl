{{/*
Common DB init job template
*/}}
{{- define "xroad.db.init.job" -}}
{{- $name := .name }}
{{- $config := .config }}
{{- $root := .root }}

{{- if and (eq $name "serverconf") (hasKey $config "proxyUiSuperuserPassword") }}
apiVersion: v1
kind: Secret
metadata:
  name: {{ $name }}-db-init-secret
  annotations:
      "helm.sh/hook": pre-install,pre-upgrade
      "helm.sh/hook-weight": "-10"  # Lower weight execution than db-init job, as it depends on given secret being present
data:
  proxy_ui_superuser_password: {{ $config.proxyUiSuperuserPassword | b64enc | quote }}
{{- end }}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ $name }}-db-init
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "-5"
    "helm.sh/hook-delete-policy": before-hook-creation,hook-succeeded
spec:
  backoffLimit: 3
  template:
    spec:
      containers:
        - name: {{ $name }}-db-init
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
              valueFrom:
                secretKeyRef:
                  name: db-{{ $name }}
                  key: postgres-password
            - name: LIQUIBASE_COMMAND_URL
              value: "jdbc:postgresql://{{ $config.host }}:{{ $config.port }}/{{ $config.database }}"
              {{ $config.url | quote }}
            - name: db_schema
              value: {{ $config.schema | quote }}
            - name: db_user
              value: {{ $config.username | quote }}
            {{- if and (eq $name "serverconf") (hasKey $config "proxyUiSuperuserPassword") }}
            - name: PROXY_UI_SUPERUSER
              value: {{ $config.proxyUiSuperuser | quote }}
            - name: PROXY_UI_SUPERUSER_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ $name }}-db-init-secret
                  key: proxy_ui_superuser_password
            {{- end }}
      initContainers:
        - name: check-db-ready
          image: "postgres:17"
          command: ['sh', '-c',
                    'until pg_isready -h {{ $config.host }} -p {{ $config.port }}; do echo waiting for database; sleep 1; done;']
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
---
{{- if (index .Values "services" "op-monitor" "enabled") }}
    {{- include "xroad.db.init.job" (dict "root" $root "name" "opmonitor" "config" .Values.init.opmonitor) }}
{{- end }}
{{- end }}
