# Verify that the Security Server installed with variant=fi runs the FI country profile.
# LXD only: runs after setup.hurl (file name order) and is listed in hurl_files only when ss0's variant is fi.

# Log in to the Security Server
POST https://{{ss0_host}}:4000/login
[FormParams]
username: {{ui_username}}
password: {{ui_password}}

HTTP 200
[Captures]
ss0_xsrf_token: cookie "XSRF-TOKEN"

# A key set by the FI profile reports the country profile as its source
GET https://{{ss0_host}}:4000/api/v1/system/property
X-XSRF-TOKEN: {{ss0_xsrf_token}}

HTTP 200
[Asserts]
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].value_source" == "COUNTRY_PROFILE"
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].country_profile" == "FI"
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].effective_value" == "3072"
