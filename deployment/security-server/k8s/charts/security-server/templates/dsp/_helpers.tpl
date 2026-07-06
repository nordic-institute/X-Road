{{/*
Dataspace (DSP) helpers.

DSP app workloads are folded into the `services.*` map and rendered via
the shared `xroad.deployment` / `xroad.service` helpers in
`templates/_helpers.tpl`. DSP-scoped PostgreSQL clusters are provisioned
outside the chart (by the cloudnative_pg ansible role in the dev stack);
this file keeps only the labels/gate helpers and the `xroad.dsp.wait.*`
initContainer emitters used by `templates/services/all.yaml` and the
seed Job in this directory.

Reuses existing chart helpers from templates/_helpers.tpl (`xroad.labels`)
rather than redefining them — see PRD §Helm Chart Architecture → Reuse of
existing helpers (NFR17).
*/}}

{{/*
Common labels for dsp-scoped resources.
Delegates to `xroad.labels` so dsp workloads inherit
app.kubernetes.io/instance, managed-by, helm.sh/chart — and therefore
will show up under `kubectl get pods -l app.kubernetes.io/instance=<release>`
alongside core Security Server workloads.

Call shape:
  {{ include "xroad.dsp.labels" . }}
*/}}
{{- define "xroad.dsp.labels" -}}
{{ include "xroad.labels" . }}
app.kubernetes.io/component: dsp
{{- end }}

{{/*
Temporary DSP feature gate — returns a non-empty string when any DSP app
service has `replicas > 0`, otherwise returns "". Drives conditional
rendering of DSP-scoped resources (passwords Secret, DB StatefulSets,
seed Job, init-scripts ConfigMap, mock-jwks ConfigMap).

Intentionally replica-driven (not a dedicated `dsp.enabled` flag): DSP
will become always-on once it ships, at which point this helper is
removed along with its callers. Until then, `replicas: 0` on every
`ds-*` service is the off switch.

Call shape:
  {{- if include "xroad.dsp.enabled" . }} ... {{- end }}
*/}}
{{- define "xroad.dsp.enabled" -}}
{{- $svc := .Values.services | default dict -}}
{{- $on := false -}}
{{- range $name := list "ds-control-plane" "ds-identity-hub" "ds-issuer-service" -}}
  {{- $s := index $svc $name -}}
  {{- if and $s (gt (int ($s.replicas | default 0)) 0) -}}
    {{- $on = true -}}
  {{- end -}}
{{- end -}}
{{- if $on }}true{{ end -}}
{{- end }}

{{/*
Single source of truth for the mock-jwks-server keys ConfigMap name.
Prefixed with `.Release.Name` per chart naming convention.

Call shape:
  {{ include "xroad.dsp.mockJwksServerKeysConfigMapName" . }}
*/}}
{{- define "xroad.dsp.mockJwksServerKeysConfigMapName" -}}
{{ .Release.Name }}-mock-jwks-server-keys
{{- end }}

{{/*
initContainer emitter: wait until a Postgres dependency is reachable.

Call shape:
  {{- include "xroad.dsp.wait.postgres" (dict "root" $ "host" "db-ds-control-plane" "name" "wait-db") | nindent 8 }}

Accepted top-level keys on the include dict:
  - `root` (required) root context `$` — used for Values
  - `host` (required) postgres host to poll
  - `name` (optional, default "wait-db") initContainer name
  - `user` (optional, default "postgres") POSTGRES_USER to pass as `-U`
*/}}
{{- define "xroad.dsp.wait.postgres" -}}
{{- $maxAttempts := .root.Values.waitGate.maxAttempts | default 120 -}}
{{- $periodSeconds := .root.Values.waitGate.periodSeconds | default 2 -}}
{{- $user := .user | default "postgres" -}}
- name: {{ .name | default "wait-db" }}
  image: {{ .root.Values.postgres.image | quote }}
  imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
  securityContext:
    {{- toYaml (.root.Values.securityContext.container | default dict) | nindent 4 }}
  command: ["sh", "-c"]
  args:
    - |
      i=0
      while [ $i -lt {{ $maxAttempts }} ]; do
        if pg_isready -h {{ .host }} -U {{ $user }} >/dev/null 2>&1; then exit 0; fi
        sleep {{ $periodSeconds }}
        i=$((i+1))
      done
      echo "timed out waiting for postgres at {{ .host }}" >&2
      exit 1
  resources:
    {{- toYaml .root.Values.waitGate.resources | nindent 4 }}
{{- end }}

{{/*
initContainer emitter: wait until a TCP dependency accepts a connection.

Call shape:
  {{- include "xroad.dsp.wait.tcp" (dict "root" $ "host" "mock-jwks-server" "port" 8080 "name" "wait-mock-jwks") | nindent 8 }}

Accepted top-level keys on the include dict:
  - `root` (required) root context `$` — used for Values
  - `host` (required) hostname to connect to
  - `port` (required) TCP port to connect to
  - `name` (required) initContainer name
*/}}
{{- define "xroad.dsp.wait.tcp" -}}
{{- $maxAttempts := .root.Values.waitGate.maxAttempts | default 120 -}}
{{- $periodSeconds := .root.Values.waitGate.periodSeconds | default 2 -}}
- name: {{ .name }}
  image: {{ .root.Values.postgres.image | quote }}
  imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
  securityContext:
    {{- toYaml (.root.Values.securityContext.container | default dict) | nindent 4 }}
  command: ["bash", "-c"]
  args:
    - |
      i=0
      while [ $i -lt {{ $maxAttempts }} ]; do
        if (exec 3<>/dev/tcp/{{ .host }}/{{ .port }}) >/dev/null 2>&1; then exec 3<&-; exec 3>&-; exit 0; fi
        sleep {{ $periodSeconds }}
        i=$((i+1))
      done
      echo "timed out waiting for TCP endpoint {{ .host }}:{{ .port }}" >&2
      exit 1
  resources:
    {{- toYaml .root.Values.waitGate.resources | nindent 4 }}
{{- end }}

{{/*
Dispatcher: render one typed wait spec (an entry from
`services.<name>.waits` in values) to an initContainer YAML string.
Consumed by `services/all.yaml`, which collects the rendered strings
into the `.config.initContainers` list contract expected by
`xroad.deployment`.

Keeps `services/all.yaml` generic — no service-name hardcoding — by
pushing the wait-chain data into values and dispatching on `.spec.type`.

Supported wait types:
  - postgres         fields: host (required), name (required), user (optional)
  - tcp              fields: host (required), port (required), name (required)
  - serverconfSeed   fields: (none) — emits the canonical seed-row wait

Call shape (per-spec; wrap in a range):
  {{- include "xroad.dsp.wait.dispatch" (dict "root" $ "spec" $wait) }}
*/}}
{{- define "xroad.dsp.wait.dispatch" -}}
{{- $root := .root -}}
{{- $spec := .spec -}}
{{- if eq $spec.type "postgres" -}}
  {{- include "xroad.dsp.wait.postgres" (dict "root" $root "host" $spec.host "name" $spec.name "user" ($spec.user | default "")) -}}
{{- else if eq $spec.type "tcp" -}}
  {{- include "xroad.dsp.wait.tcp" (dict "root" $root "host" $spec.host "port" $spec.port "name" $spec.name) -}}
{{- else if eq $spec.type "serverconfSeed" -}}
  {{- include "xroad.dsp.wait.serverconfQuery" (dict
        "root" $root
        "name" "wait-serverconf-seed"
        "sql" "SELECT 1 FROM configuration_properties WHERE property_key='edc.iam.trusted-issuer.issuer.id' AND scope='ds-control-plane' LIMIT 1"
        "requireNonEmpty" true
        "timeoutMsg" "serverconf seed row not present (property_key='edc.iam.trusted-issuer.issuer.id' scope='ds-control-plane')."
        "timeoutHint" "kubectl logs -l app=xroad-db-serverconf-ds-control-plane-config-seed") -}}
{{- else -}}
  {{- fail (printf "xroad.dsp.wait.dispatch: unknown wait type %q — expected postgres | tcp | serverconfSeed" $spec.type) -}}
{{- end -}}
{{- end }}

{{/*
initContainer emitter: poll `db-serverconf` with a parameterized SELECT and
exit 0 when the success predicate is met. Replaces the former
`wait.postgresSchemaServerconf` and `wait.serverconfSeed` helpers — both
collapsed to one definition keyed by the success predicate.

psql exit-code handling is identical across callers: rc=0 means the query
ran (success iff predicate matches), rc=2 means connection/auth failure
(hard-fail immediately), anything else is a transient statement error and
retried until `waitGate.maxAttempts` runs out.

Call shape:
  {{- include "xroad.dsp.wait.serverconfQuery" (dict
        "root" $
        "name" "wait-db-serverconf"
        "sql" "SELECT 1 FROM configuration_properties LIMIT 1"
        "requireNonEmpty" false
        "timeoutMsg" "configuration_properties table not present in db-serverconf (Liquibase likely not finished)."
        "timeoutHint" "kubectl logs job/serverconf-db-init") | nindent 8 }}

Accepted top-level keys on the include dict:
  - `root` (required) root context `$` — used for Values
  - `name` (required) initContainer name; also used in psql error prefix
  - `sql`  (required) SELECT statement (runs after `SET search_path`)
  - `requireNonEmpty` (optional, bool, default false) when true, success
    requires rc=0 AND non-empty psql output — used for "row exists" waits
  - `timeoutMsg` (required) human message printed on timeout (stderr)
  - `timeoutHint` (optional) follow-up command to help diagnose timeout
*/}}
{{- define "xroad.dsp.wait.serverconfQuery" -}}
{{- $maxAttempts := 120 -}}
{{- if hasKey .root.Values.waitGate "maxAttempts" -}}
  {{- $maxAttempts = .root.Values.waitGate.maxAttempts -}}
{{- end -}}
{{- if le (int $maxAttempts) 0 }}{{ fail "waitGate.maxAttempts must be > 0" }}{{- end }}
{{- $periodSeconds := .root.Values.waitGate.periodSeconds | default 2 -}}
{{- $_ := required "xroad.dsp.wait.serverconfQuery: name is required" .name -}}
{{- $_ = required "xroad.dsp.wait.serverconfQuery: sql is required" .sql -}}
{{- $_ = required "xroad.dsp.wait.serverconfQuery: timeoutMsg is required" .timeoutMsg -}}
{{- $requireNonEmpty := .requireNonEmpty | default false -}}
- name: {{ .name }}
  image: {{ .root.Values.postgres.image | quote }}
  imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
  securityContext:
    {{- toYaml (.root.Values.securityContext.container | default dict) | nindent 4 }}
  env:
    - name: PGPASSWORD
      valueFrom:
        secretKeyRef:
          name: db-serverconf
          key: password
  command: ["sh", "-c"]
  args:
    - |
      i=0
      while [ $i -lt {{ $maxAttempts }} ]; do
        # Capture stdout+stderr together; root fs is read-only so we cannot use a temp file.
        # On rc=0 psql emits only the query result on stdout; on failure the combined
        # output is the diagnostic we want to surface.
        output=$(psql -h db-serverconf -U serverconf -d serverconf -v ON_ERROR_STOP=1 -tAc "SET search_path TO {{ .root.Values.init.serverconf.schema | default "public" }}, public; {{ .sql }}" 2>&1)
        rc=$?
        {{- if $requireNonEmpty }}
        if [ $rc -eq 0 ] && [ -n "$output" ]; then exit 0; fi
        {{- else }}
        if [ $rc -eq 0 ]; then exit 0; fi
        {{- end }}
        if [ $rc -eq 2 ]; then
          echo "{{ .name }}: psql connection/auth failure (exit 2). Check db-serverconf Service DNS + Secret 'db-serverconf/password'. psql output:" >&2
          [ -n "$output" ] && printf '%s\n' "$output" >&2
          exit 1
        fi
        # rc in (1, or 0-with-empty-result when requireNonEmpty) → retry
        sleep {{ $periodSeconds }}
        i=$((i+1))
      done
      echo "timed out: {{ .timeoutMsg }}{{ if .timeoutHint }} Diagnose: {{ .timeoutHint }}{{ end }}" >&2
      exit 1
  resources:
    {{- toYaml .root.Values.waitGate.resources | nindent 4 }}
{{- end }}
