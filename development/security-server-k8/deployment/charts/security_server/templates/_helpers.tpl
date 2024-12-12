{{/*
Common labels
*/}}
{{- define "xroad.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}


{{/*
Service template
*/}}
{{- define "xroad.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ .service }}
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
spec:
  ports:
    - port: {{ .config.port }}
      targetPort: {{ .config.port }}
      name: http
    {{- if .config.debugPort }}
    - port: {{ .config.debugPort }}
      targetPort: {{ .config.debugPort }}
      name: debug
    {{- end }}
  selector:
    app: xroad-{{ .service }}
{{- end }}

{{/*
Deployment template
*/}}
{{- define "xroad.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .service }}
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
spec:
  selector:
    matchLabels:
      app: xroad-{{ .service }}
  template:
    metadata:
      labels:
        {{- include "xroad.labels" .root | nindent 8 }}
        app: xroad-{{ .service }}
    spec:
      serviceAccountName: {{ .service }}-sa
      # Add DNS settings
      dnsPolicy: ClusterFirst
      dnsConfig:
        options:
          - name: ndots
            value: "1"

{{/*      {{- if .config.dependencies }}*/}}
{{/*      initContainers:*/}}
{{/*        - name: wait-for-dependencies*/}}
{{/*          image: busybox:1.36*/}}
{{/*          command: ['sh', '-c']*/}}
{{/*          args:*/}}
{{/*            - |*/}}
{{/*              # Wait for DNS to be ready*/}}
{{/*              echo "Waiting for DNS to be ready..."*/}}
{{/*              until nslookup kubernetes.default.svc.cluster.local; do*/}}
{{/*                echo "DNS not ready yet..."*/}}
{{/*                sleep 2*/}}
{{/*              done*/}}

{{/*              {{- range .config.dependencies }}*/}}
{{/*              echo "Waiting for {{ . }} service..."*/}}
{{/*              until nslookup {{ . }}.{{ $.root.Release.Namespace }}.svc.cluster.local >/dev/null 2>&1; do*/}}
{{/*                echo "Service {{ . }} not found yet..."*/}}
{{/*                sleep 5*/}}
{{/*              done*/}}

{{/*              # More resilient health check with timeout*/}}
{{/*              for i in $(seq 1 30); do*/}}
{{/*                if wget -T 5 -q --spider http://{{ . }}.{{ $.root.Release.Namespace }}.svc.cluster.local:{{ $.root.Values.services.config.port }}/actuator/health; then*/}}
{{/*                  echo "Service {{ . }} is healthy"*/}}
{{/*                  break*/}}
{{/*                fi*/}}
{{/*                if [ $i -eq 30 ]; then*/}}
{{/*                  echo "Service {{ . }} health check failed after 30 attempts"*/}}
{{/*                  exit 1*/}}
{{/*                fi*/}}
{{/*                echo "Waiting for service {{ . }} to be healthy... attempt $i/30"*/}}
{{/*                sleep 5*/}}
{{/*              done*/}}
{{/*              {{- end }}*/}}
{{/*      {{- end }}*/}}
      containers:
        - name: {{ .service }}
          image: {{ .config.image }}
          imagePullPolicy: {{ .config.imagePullPolicy }}
          ports:
            - containerPort: {{ .config.port }}
            {{- if .config.debugPort }}
            - containerPort: {{ .config.debugPort }}
            {{- end }}
          resources:
            {{- toYaml .config.resources | nindent 12 }}
          envFrom:
            - configMapRef:
                name: {{ .root.Release.Name }}-{{ .service }}-env
          env:
            {{- range .config.envFromSecrets }}
            - name: {{ .name }}
              valueFrom:
                secretKeyRef:
                  name: {{ .secretName }}
                  key: {{ .key }}
            {{- end }}
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: {{ .config.port }}
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 1
            successThreshold: 1
            failureThreshold: 3
{{- end }}
{{- define "xroad.serviceaccount" -}}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ .service }}-sa
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
  {{- with .config.serviceAccount.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: {{ .service }}-role
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
rules:
  {{- include "xroad.serviceAccountRules" . | nindent 2 }}
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: {{ .service }}-rolebinding
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: {{ .service }}-role
subjects:
  - kind: ServiceAccount
    name: {{ .service }}-sa
    namespace: {{ .root.Release.Namespace }}
{{- end }}
{{/* Service Account rules template */}}
{{- define "xroad.serviceAccountRules" -}}
- apiGroups: [""]
  resources: ["configmaps", "pods"]
  verbs: ["get", "watch", "list"]
- apiGroups: [""]
  resources: ["configmaps"]
  resourceNames:
    - {{ printf "%s-common-xroad-config" .root.Release.Name | quote }}
    - {{ printf "%s-%s-config" .root.Release.Name .service | quote }}
  verbs: ["get", "watch", "list"]
{{- end }}