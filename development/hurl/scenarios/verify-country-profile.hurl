# Verify that ss0 runs with the country profile ss0_country_profile (country code, e.g. FO): at least one system
# parameter reports that profile as its source.
# Run after setup.hurl: run-hurl.sh concatenates the scenario files in file name order.
# A failed assertion fails at once: the requests set retry to 0 over the runner's retry option.

# Log in to ss0
POST https://{{ss0_host}}:4000/login
[Options]
retry: 0
[FormParams]
username: {{ui_username}}
password: {{ui_password}}

HTTP 200
[Captures]
country_profile_xsrf_token: cookie "XSRF-TOKEN"

# At least one parameter reports the expected country profile as its source
GET https://{{ss0_host}}:4000/api/v1/system/property
X-XSRF-TOKEN: {{country_profile_xsrf_token}}
[Options]
retry: 0

HTTP 200
[Asserts]
jsonpath "$[?(@.country_profile=='{{ss0_country_profile}}')]" exists
