{{/*
Common labels — includes a fixed marker label so every rendered resource
self-identifies as non-production (mirrors the Chart.yaml annotation).
Identical to the central-server chart's copy — kept per-chart since Helm
templates aren't shared across charts without a library chart dependency.
*/}}
{{- define "xroad.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
xroad.niis.org/production-ready: "false"
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
  selector:
    app: xroad-{{ .service }}
{{- end }}

{{/*
Deployment template.

Unlike the security-server chart's version, this does not project a
rootless/read-only-rootfs securityContext by default: most of these fixtures
are third-party test-double images with their own user/filesystem
assumptions. See values.yaml's securityContext comment.
*/}}
{{- define "xroad.deployment" -}}
{{- $replicas := 1 -}}
{{- if hasKey .config "replicas" -}}{{- $replicas = .config.replicas | int -}}{{- end -}}
{{- if gt $replicas 0 -}}
{{- if not .config.image -}}
{{- $_ := required (printf "services.%s.imageName required when replicas>0 (unless services.%s.image is set)" .service .service) .config.imageName -}}
{{- end -}}
{{- end -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .service }}
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
spec:
  replicas: {{ $replicas | int }}
  selector:
    matchLabels:
      app: xroad-{{ .service }}
  template:
    metadata:
      labels:
        {{- include "xroad.labels" .root | nindent 8 }}
        app: xroad-{{ .service }}
      {{- with .root.Values.global.extraPodAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      {{- with .root.Values.securityContext.pod }}
      securityContext:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      serviceAccountName: {{ .service }}-sa
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
          image: {{ if .config.image }}{{ .config.image | quote }}{{ else }}{{ printf "%s/%s:%s" .root.Values.global.image.registry .config.imageName .root.Values.global.image.tag | quote }}{{ end }}
          imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
          {{- if .config.command }}
          command:
            {{- toYaml .config.command | nindent 12 }}
          {{- end }}
          {{- with .root.Values.securityContext.container }}
          securityContext:
            {{- toYaml . | nindent 12 }}
          {{- end }}
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
          resources:
            {{- toYaml .config.resources | nindent 12 }}
          envFrom:
            - configMapRef:
                name: {{ .root.Release.Name }}-{{ .service }}-env
          {{- if .config.envFromSecrets }}
          env:
            {{- range .config.envFromSecrets }}
            - name: {{ .name }}
              valueFrom:
                secretKeyRef:
                  name: {{ if .releaseNamePrefix }}{{ $.root.Release.Name }}-{{ .secretName }}{{ else }}{{ .secretName }}{{ end }}
                  key: {{ .key }}
            {{- end }}
          {{- end }}
          {{- if or .config.volumeMounts .config.extraVolumeMounts }}
          volumeMounts:
            {{- if .config.volumeMounts }}
            {{- toYaml .config.volumeMounts | nindent 12 }}
            {{- end }}
            {{- if .config.extraVolumeMounts }}
            {{- toYaml .config.extraVolumeMounts | nindent 12 }}
            {{- end }}
          {{- end }}
          readinessProbe:
            httpGet:
              path: {{ .config.readinessProbe.path }}
              port: {{ .config.readinessProbe.port | default (index .config.ports 0).port }}
              scheme: {{ .config.readinessProbe.scheme | default "HTTP" }}
            initialDelaySeconds: {{ .config.readinessProbe.initialDelaySeconds | default 10 }}
            periodSeconds: {{ .config.readinessProbe.periodSeconds | default 5 }}
            timeoutSeconds: {{ .config.readinessProbe.timeoutSeconds | default 1 }}
            successThreshold: 1
            failureThreshold: {{ .config.readinessProbe.failureThreshold | default 3 }}
          {{- if .config.livenessProbe }}
          livenessProbe:
            httpGet:
              path: {{ .config.livenessProbe.path }}
              port: {{ .config.livenessProbe.port | default (index .config.ports 0).port }}
              scheme: {{ .config.livenessProbe.scheme | default "HTTP" }}
            initialDelaySeconds: {{ .config.livenessProbe.initialDelaySeconds | default 30 }}
            periodSeconds: {{ .config.livenessProbe.periodSeconds | default 10 }}
            timeoutSeconds: {{ .config.livenessProbe.timeoutSeconds | default 3 }}
            successThreshold: 1
            failureThreshold: {{ .config.livenessProbe.failureThreshold | default 3 }}
          {{- end }}
      {{- if .config.volumes }}
      volumes:
        {{- toYaml .config.volumes | nindent 8 }}
      {{- end }}
{{- end }}

{{/*
ServiceAccount + Role + RoleBinding for a service. Scoped to reading its own
env ConfigMap plus configmaps/pods generally, matching the central-server/
security-server charts' convention.
*/}}
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

{{- define "xroad.serviceAccountRules" -}}
- apiGroups: [""]
  resources: ["configmaps", "pods"]
  verbs: ["get", "watch", "list"]
- apiGroups: [""]
  resources: ["configmaps"]
  resourceNames:
    - {{ printf "%s-%s-env" .root.Release.Name .service | quote }}
  verbs: ["get", "watch", "list"]
{{- if and .config.rbac (hasKey .config.rbac "extraRules") }}
{{- toYaml .config.rbac.extraRules | nindent 0 }}
{{- end }}
{{- end }}
