{{- define "xroad.configmap.env" -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .root.Release.Name }}-{{ .service }}-env
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
data:
  {{- toYaml .config.env | nindent 2 }}
  XROAD_DEPLOYMENT_TYPE: "containerized"
  XROAD_APPLICATION_TYPE: "ss"
  XROAD_ADDITIONAL_PROFILES: "kubernetes"
  QUARKUS_PROFILE: "kubernetes,containerized,secret-store"
  QUARKUS_KUBERNETES_CONFIG_CONFIG_MAPS: "{{ .root.Release.Name }}-{{ .service }}-config,{{ .root.Release.Name }}-common-xroad-config"
  QUARKUS_KUBERNETES_CONFIG_NAMESPACE: "{{ .root.Release.Namespace }}"
  SPRING_CLOUD_KUBERNETES_CONFIG_SOURCES_0_NAME: {{ .root.Release.Name }}-{{ .service }}-config
  SPRING_CLOUD_KUBERNETES_CONFIG_SOURCES_1_NAME: {{ .root.Release.Name }}-common-xroad-config
  SPRING_CLOUD_KUBERNETES_CONFIG_NAMESPACE: {{ .root.Release.Namespace }}
{{- end }}

{{- define "xroad.configmap.spring" -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .root.Release.Name }}-{{ .service }}-config
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-{{ .service }}
data:
  {{- $configFile := printf "config/%s-kubernetes.yaml" .service }}
  {{- if $.root.Files.Glob $configFile }}
  application.yaml: |
    {{- $.root.Files.Get $configFile | nindent 4 }}
  {{- else }}
  {{- end }}
{{- end }}

{{- define "xroad.configmap.spring.common" -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .root.Release.Name }}-common-xroad-config
  labels:
    {{- include "xroad.labels" .root | nindent 4 }}
    app: xroad-common
data:
  {{- $configFile := printf "config/application.yaml" }}
  {{- if $.root.Files.Glob $configFile }}
  application.yaml: |
    {{- $.root.Files.Get $configFile | nindent 4 }}
  {{- else }}
  {{- end }}
{{- end }}
