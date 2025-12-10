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
Generate volume name from path (e.g., /var/log/xroad -> var-log-xroad)
*/}}
{{- define "xroad.volumeName" -}}
{{- . | trimPrefix "/" | replace "/" "-" -}}
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
      securityContext:
        {{- toYaml .root.Values.securityContext.pod | nindent 8 }}
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
          image: {{ .root.Values.global.image.registry }}/{{ .config.imageName }}:{{ .root.Values.global.image.tag }}
          imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
          securityContext:
            {{- toYaml .root.Values.securityContext.container | nindent 12 }}
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
          volumeMounts:
            - mountPath: /tmp
              name: tmp-volume
            {{- $skipMounts := .config.skipDefaultMounts | default list }}
            {{- range .root.Values.writablePaths }}
            {{- if not (has . $skipMounts) }}
            - mountPath: {{ . }}
              name: {{ include "xroad.volumeName" . }}
            {{- end }}
            {{- end }}
            {{- if .config.volumeMounts }}
            {{- toYaml .config.volumeMounts | nindent 12 }}
            {{- end }}
            {{- if .config.extraVolumeMounts }}
            {{- toYaml .config.extraVolumeMounts | nindent 12 }}
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

      volumes:
        - name: tmp-volume
          emptyDir: {}
        {{- $skipMounts := .config.skipDefaultMounts | default list }}
        {{- range .root.Values.writablePaths }}
        {{- if not (has . $skipMounts) }}
        - name: {{ include "xroad.volumeName" . }}
          emptyDir: {}
        {{- end }}
        {{- end }}
        {{- if .config.volumes }}
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
        {{- if .config.extraVolumes }}
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