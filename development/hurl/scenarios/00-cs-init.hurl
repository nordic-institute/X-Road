# =============================================================================
# Central Server initialization — runs FIRST, before any ss0/ss1-dependent step.
#
# WIP / MOCK SEED. The aux env (CS + issuer + anchor-init) starts before the
# ss0/ss1 envs (see EnvSetup: envAux.start() blocks before envSs0/envSs1). The
# aux `anchor-init` service gates `compose up` and can only download a signed
# global conf once the Central Server is initialized. So the CS init must run
# here, ahead of 00-setup-dsp.hurl (whose IH/CP participant-context creates
# target ss0/ss1, which are not up yet and would otherwise block the hurl and
# starve this init). setup.hurl re-asserts the init tolerantly later.
# =============================================================================

# Check that the Central Server UI is up and running
GET https://{{cs_host}}:4000

HTTP 200

# Log in to the Central Server
POST https://{{cs_host}}:4000/login
[FormParams]
username: xrd
password: secret123!

HTTP 200
[Captures]
cs_xsrf_token: cookie "XSRF-TOKEN"

# Initialize Central Server
POST https://{{cs_host}}:4000/api/v1/initialization
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "software_token_pin": "Secret1234",
  "instance_identifier": "DEV",
  "central_server_address": "{{cs_host}}"
}

HTTP *
[Asserts]
status < 500

# Log in the Central Server software token (required to generate signing keys).
PUT https://{{cs_host}}:4000/api/v1/tokens/0/login
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "password": "Secret1234"
}

HTTP *
[Asserts]
status < 500

# Generate INTERNAL + EXTERNAL configuration signing keys. Without these the
# global-conf anchor is not downloadable (anchor download 404s with "signing key
# may not exist yet"), so aux anchor-init + configuration-client can never feed
# the issuer a global conf. setup.hurl tolerates these being already present.
POST https://{{cs_host}}:4000/api/v1/configuration-sources/INTERNAL/signing-keys
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Internal signing key",
  "token_id": 0
}

HTTP *
[Asserts]
status < 500

POST https://{{cs_host}}:4000/api/v1/configuration-sources/EXTERNAL/signing-keys
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "key_label": "External signing key",
  "token_id": 0
}

HTTP *
[Asserts]
status < 500
