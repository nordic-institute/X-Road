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
  replicas: 1
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
      {{- with .root.Values.imagePullSecrets }}
      imagePullSecrets:
        {{- range . }}
        - name: {{ . }}
        {{- end }}
      {{- end }}
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
          {{- if or .config.volumeMounts .config.extraVolumeMounts }}
          volumeMounts:
            {{- with .config.volumeMounts }}
            {{- toYaml . | nindent 12 }}
            {{- end }}
            {{/* extraVolumeMounts for not overriding volumeMounts defaults */}}
            {{- with .config.extraVolumeMounts }}
            {{- toYaml . | nindent 12 }}
            {{- end }}
          {{- end }}
          readinessProbe:
            httpGet:
              path: {{ .config.readinessProbe.path }}
              port: {{ (index .config.ports 0).port }}
              scheme: {{ .config.readinessProbe.scheme }}
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 1
            successThreshold: 1
            failureThreshold: 3
      {{- if or .config.volumes .config.extraVolumes }}
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
        {{/* extraVolumes for not overriding volumes defaults */}}
        {{- range .config.extraVolumes }}
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
{{- if and .config.rbac (hasKey .config.rbac "extraRules") }}
{{- toYaml .config.rbac.extraRules | nindent 0 }}
{{- end }}
{{- end }}