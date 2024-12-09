
# Check that the Security Server UI is up and running
GET https://{{ss0_host}}:4000
HTTP 200

# Log in to the Security Server
POST https://{{ss0_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss0_xsrf_token: cookie "XSRF-TOKEN"

#Trigger DS asset update job
GET https://{{ss0_host}}:4000/api/v1/ds/trigger-asset-update
X-XSRF-TOKEN: {{ss0_xsrf_token}}

HTTP 200


# Check that the Security Server UI is up and running
GET https://{{ss1_host}}:4000
HTTP 200

# Log in to the Security Server
POST https://{{ss1_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss1_xsrf_token: cookie "XSRF-TOKEN"

#Trigger DS asset update job
GET https://{{ss1_host}}:4000/api/v1/ds/trigger-asset-update
X-XSRF-TOKEN: {{ss1_xsrf_token}}

HTTP 200
