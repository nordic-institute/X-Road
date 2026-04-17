# =============================================================================
# DSP Mock Data Seeding
# Seeds ds-control-plane with participant context, asset, policy, and contract
# definition on both xrd-ss0 (consumer) and xrd-ss1 (provider).
# Must run AFTER setup.hurl (ensured by underscore in filename: '_' > '.' in ASCII).
# =============================================================================

# --- xrd-ss0 (consumer) ---

# 1. Create participant context on ss0
POST http://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqvyLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZpDz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContext",
    "identity": "test-identity-1",
    "@id": "test-part-ctx"
}

HTTP *
[Asserts]
status < 500

# 2. Create participant context config on ss0
POST http://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/config
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqvyLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZpDz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContextConfig",
    "entries": {
        "edc.participant.id": "test-participant-id"
    },
    "privateEntries": {}
}

HTTP *
[Asserts]
status < 500

# 3. Create asset on ss0 — asset @id MUST match ServiceId from proxy path /r1/DEV/COM/1234/TestService/mock1
POST http://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/assets
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhcnQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAPjfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRpni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtGYDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@id": "DEV:COM:1234:TestService:mock1",
    "@type": "Asset",
    "properties": {
        "name": "mock DSP service",
        "contenttype": "application/json"
    },
    "dataAddress": {
        "@type": "DataAddress",
        "type": "HttpData",
        "name": "Mock service backend",
        "baseUrl": "http://xrd-is/integration/mock_1",
        "proxyPath": "true"
    }
}

HTTP *
[Asserts]
status < 500

# 4. Create allow-all policy on ss0
POST http://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/policydefinitions
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhcnQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAPjfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRpni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtGYDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@id": "policy-allow-all",
    "@type": "PolicyDefinition",
    "policy": {
        "@context": "http://www.w3.org/ns/odrl.jsonld",
        "@type": "Set",
        "permission": [{"action": "use"}]
    }
}

HTTP *
[Asserts]
status < 500

# 5. Create contract definition on ss0
POST http://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/contractdefinitions
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhcnQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAPjfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRpni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtGYDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@id": "contract-def-1",
    "@type": "ContractDefinition",
    "accessPolicyId": "policy-allow-all",
    "contractPolicyId": "policy-allow-all",
    "assetsSelector": [{
        "@type": "Criterion",
        "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
        "operator": "=",
        "operandRight": "DEV:COM:1234:TestService:mock1"
    }]
}

HTTP *
[Asserts]
status < 500

# --- xrd-ss1 (provider) ---
# Identical seeding on provider so catalog/negotiation resolves when consumer queries provider

# 6. Create participant context on ss1
POST http://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqvyLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZpDz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContext",
    "identity": "test-identity-1",
    "@id": "test-part-ctx"
}

HTTP *
[Asserts]
status < 500

# 7. Create participant context config on ss1
POST http://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/config
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqvyLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZpDz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContextConfig",
    "entries": {
        "edc.participant.id": "test-participant-id"
    },
    "privateEntries": {}
}

HTTP *
[Asserts]
status < 500

# 8. Create asset on ss1 — same asset @id so consumer catalog lookup resolves on provider
POST http://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/assets
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhcnQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAPjfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRpni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtGYDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@id": "DEV:COM:1234:TestService:mock1",
    "@type": "Asset",
    "properties": {
        "name": "mock DSP service",
        "contenttype": "application/json"
    },
    "dataAddress": {
        "@type": "DataAddress",
        "type": "HttpData",
        "name": "Mock service backend",
        "baseUrl": "http://xrd-is/integration/mock_1",
        "proxyPath": "true"
    }
}

HTTP *
[Asserts]
status < 500

# 9. Create allow-all policy on ss1
POST http://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/policydefinitions
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhcnQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAPjfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRpni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtGYDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@id": "policy-allow-all",
    "@type": "PolicyDefinition",
    "policy": {
        "@context": "http://www.w3.org/ns/odrl.jsonld",
        "@type": "Set",
        "permission": [{"action": "use"}]
    }
}

HTTP *
[Asserts]
status < 500

# 10. Create contract definition on ss1
POST http://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/mgmt/v4alpha/participants/test-part-ctx/contractdefinitions
Authorization: Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhcnQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAPjfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRpni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtGYDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@id": "contract-def-1",
    "@type": "ContractDefinition",
    "accessPolicyId": "policy-allow-all",
    "contractPolicyId": "policy-allow-all",
    "assetsSelector": [{
        "@type": "Criterion",
        "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
        "operator": "=",
        "operandRight": "DEV:COM:1234:TestService:mock1"
    }]
}

HTTP *
[Asserts]
status < 500
