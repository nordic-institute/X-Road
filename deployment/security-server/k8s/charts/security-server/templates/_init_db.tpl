{{/*
Common DB init job template
*/}}
{{- define "xroad.db.init.job" -}}
{{- $name := .name }}
{{- $config := .config }}
{{- $root := .root }}

apiVersion: batch/v1
kind: Job
metadata:
  name: {{ $name }}-db-init
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "-5"
    "helm.sh/hook-delete-policy": before-hook-creation
spec:
  backoffLimit: 3
  template:
    spec:
      securityContext:
        {{- toYaml .root.Values.securityContext.pod | nindent 8 }}
      {{- with .root.Values.imagePullSecrets }}
      imagePullSecrets:
        {{- range . }}
        - name: {{ . }}
        {{- end }}
      {{- end }}
      containers:
        - name: {{ $name }}-db-init
          image: {{ .root.Values.global.image.registry }}/{{ .root.Values.init.imageName }}:{{ .root.Values.global.image.tag }}
          imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
          securityContext:
            {{- toYaml .root.Values.init.securityContext | nindent 12 }}
          resources:
            requests:
              memory: "128Mi"
            limits:
              memory: "512Mi"
          env:
            - name: JDBC_URL
              value: "jdbc:postgresql://{{ $config.host }}:{{ $config.port }}/{{ $config.database }}"
            - name: JDBC_USER
              {{- if $config.userSecretKey }}
              valueFrom:
                secretKeyRef:
                  name: db-{{ $name }}
                  key: {{ $config.userSecretKey }}
              {{- else }}
              value: "postgres"
              {{- end }}
            - name: JDBC_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-{{ $name }}
                  key: {{ $config.passwordSecretKey | default "postgres-password" }}
            - name: CHANGELOG
              value: {{ ($config.changelogName | default $name) | quote }}
            - name: DEFAULT_SCHEMA_NAME
              value: {{ $config.schema | quote }}
            {{- if $config.username }}
            - name: PROP_DB_USER
              value: {{ $config.username | quote }}
            {{- end }}
          volumeMounts:
            - mountPath: /tmp
              name: tmp-volume
      initContainers:
        - name: check-db-ready
          image: "postgres:18"
          securityContext:
            {{- toYaml .root.Values.securityContext.container | nindent 12 }}
          command: ['sh', '-c',
                    'until pg_isready -h {{ $config.host }} -p {{ $config.port }}; do echo waiting for database; sleep 1; done;']
          volumeMounts:
            - mountPath: /tmp
              name: tmp-volume
      volumes:
        - name: tmp-volume
          emptyDir: {}
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
{{- /* Dataspace components, rendered only when the service runs */}}
{{- if gt (int (index .Values "services" "ds-control-plane" "replicas")) 0 }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "ds-control-plane" "config" .Values.init.dsControlPlane) }}
{{- end }}
{{- if gt (int (index .Values "services" "ds-identity-hub" "replicas")) 0 }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "ds-identity-hub" "config" .Values.init.dsIdentityHub) }}
{{- end }}
{{- if gt (int (index .Values "services" "ds-issuer-service" "replicas")) 0 }}
---
{{- include "xroad.db.init.job" (dict "root" $root "name" "ds-issuer-service" "config" .Values.init.dsIssuerService) }}
{{- end }}
{{- end }}
