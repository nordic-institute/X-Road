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
    {{- if kindIs "slice" .config.ports }}
    {{- range .config.ports }}
    - port: {{ .port }}
      targetPort: {{ .port }}
      name: {{ .name }}
    {{- end }}
    {{- else }}
    - port: {{ .config.port }}
      targetPort: {{ .config.port }}
      name: http
    {{- end }}
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
  {{- if and .config.dsComponent (not .root.Values.global.dataSpacesEnabled) }}
  replicas: 0
  {{- else }}
  replicas: 1
  {{- end }}
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
      containers:
        - name: {{ .service }}
          image: {{ .config.image }}
          imagePullPolicy: {{ .config.imagePullPolicy }}
          ports:
            {{- if kindIs "slice" .config.ports }}
            {{- range .config.ports }}
            - containerPort: {{ .port }}
              name: {{ .name }}
            {{- end }}
            {{- else }}
            - containerPort: {{ .config.port }}
              name: http
            {{- end }}
            {{- if .config.debugPort }}
            - containerPort: {{ .config.debugPort }}
              name: debug
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
          {{- if .config.volumeMounts }}
          volumeMounts:
            {{- toYaml .config.volumeMounts | nindent 12 }}
          {{- end }}
          readinessProbe:
            httpGet:
              path: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.path }}{{ else }}{{ .root.Values.readinessProbe.default.path }}{{ end }}
              port: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.port | default .root.Values.readinessProbe.default.port | default (index .config.ports 0).name }}{{ else }}{{ .root.Values.readinessProbe.default.port | default (index .config.ports 0).name }}{{ end }}
            initialDelaySeconds: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.initialDelaySeconds | default .root.Values.readinessProbe.default.initialDelaySeconds }}{{ else }}{{ .root.Values.readinessProbe.default.initialDelaySeconds }}{{ end }}
            periodSeconds: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.periodSeconds | default .root.Values.readinessProbe.default.periodSeconds }}{{ else }}{{ .root.Values.readinessProbe.default.periodSeconds }}{{ end }}
            timeoutSeconds: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.timeoutSeconds | default .root.Values.readinessProbe.default.timeoutSeconds }}{{ else }}{{ .root.Values.readinessProbe.default.timeoutSeconds }}{{ end }}
            successThreshold: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.successThreshold | default .root.Values.readinessProbe.default.successThreshold }}{{ else }}{{ .root.Values.readinessProbe.default.successThreshold }}{{ end }}
            failureThreshold: {{ if hasKey .config "readinessProbe" }}{{ .config.readinessProbe.failureThreshold | default .root.Values.readinessProbe.default.failureThreshold }}{{ else }}{{ .root.Values.readinessProbe.default.failureThreshold }}{{ end }}

      {{- if .config.volumes }}
      volumes:
        {{- range .config.volumes }}
        {{- if .persistentVolumeClaim }}
        - name: {{ .name }}
          persistentVolumeClaim:
            claimName: {{ $.root.Release.Name }}-{{ .persistentVolumeClaim.claimName }}
        {{- else }}
        - {{ toYaml . | nindent 10 }}
        {{- end }}
        {{- end }}
      {{- end }}
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