# Development tools

Module contains development-related classes and configurations.
These will not be packaged inside the real application jar.

# Building

```
../gradlew build
```

This builds devtools-enabled package `build/libs/proxy-ui-api-devtools-1.0.jar`

## Development authentication

You can use dummy in-memory authentication by activating profile `devtools-test-auth`.

Logins for development authentication:
- u: security-officer p: password
- u: registration-officer p: password
- u: service-admin p: password
- u: system-admin p: password
- u: observer p: password
- u: full-admin p: password
- u: roleless p: password

All these have a single role matching the username, except `full-admin` has all roles
and `roleless` has none.

## Hot deployment

Devtools-enabled package supports Spring devtools code hot deployment. To use it,
activate profile `devtools-http`. This will run the application in http://localhost:4000, and
disable https. Hot deployment feature is not currently configured to support https.