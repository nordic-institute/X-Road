# Verify that a Security Server runs the country profile it was set up with.
# Variables: country_profile_host (Security Server UI host) and country_profile_code (expected country code).
# Run after setup.hurl: run-hurl.sh concatenates the scenario files in file name order.
# A failed assertion fails at once: the requests set retry to 0 over the runner's retry option.

# Log in to the Security Server
POST https://{{country_profile_host}}:4000/login
[Options]
retry: 0
[FormParams]
username: {{ui_username}}
password: {{ui_password}}

HTTP 200
[Captures]
country_profile_xsrf_token: cookie "XSRF-TOKEN"

# A parameter set by the country profile reports the profile as its source
GET https://{{country_profile_host}}:4000/api/v1/system/property
X-XSRF-TOKEN: {{country_profile_xsrf_token}}
[Options]
retry: 0

HTTP 200
[Asserts]
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].value_source" == "COUNTRY_PROFILE"
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].country_profile" == "{{country_profile_code}}"
