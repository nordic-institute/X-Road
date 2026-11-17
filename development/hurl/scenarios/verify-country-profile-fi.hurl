# Verify that the Security Server installed with variant=fi runs the FI country profile.
# LXD only: run-hurl.sh concatenates the scenario files in file name order, so this runs after setup.hurl.
# The xroad-hurl role copies this file, and sets fi_host in vars.env, only when a host has variant=fi.

# Log in to the Security Server
POST https://{{fi_host}}:4000/login
[FormParams]
username: {{ui_username}}
password: {{ui_password}}

HTTP 200
[Captures]
fi_xsrf_token: cookie "XSRF-TOKEN"

# A key set by the FI profile reports the country profile as its source
GET https://{{fi_host}}:4000/api/v1/system/property
X-XSRF-TOKEN: {{fi_xsrf_token}}

HTTP 200
[Asserts]
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].value_source" == "COUNTRY_PROFILE"
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].country_profile" == "FI"
jsonpath "$[?(@.property_name=='xroad.signer.key-length')].effective_value" == "3072"
