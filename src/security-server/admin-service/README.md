# Building

Default build does frontend `npm run build` and packages the built frontend assets from `frontend/dist`
inside the spring boot jar.
Build installs project-local versions of `node` and `npm` using
https://github.com/srs/gradle-node-plugin, and uses those to build the package.

```
../gradlew clean build
```

For faster builds, you can skip frontend build with `skip-frontend-build` parameter.
In this case the spring boot jar does not contain frontend assets, but can be used with frontend
that is being served from webpack development server.
```
../gradlew clean build -Pskip-frontend-build
```

# Running

For example
```
../gradlew bootRun --console plain
```

browser: `https://localhost:4000`

# Development profiles

- `development` profile toggles some "safe" development aspects. It enables more logging and supports
accessing signer from a remote IP.

# TLS

Application listens to https in port 4000.

Since it uses a self-signed certificate, clients need to trust this certificate. In browser access this means manually allow exception for
"your connection is not secure" warning. For `curl` commands this means `-k` parameter (which you can see used in the examples).

By default the certificate is read from keystore `/etc/xroad/ssl/proxy-ui-api.p12`.
This keystore is automatically created when linux packages are installed.
If this does not suit you (for example in local development environment), you can do one of:

1. copy `proxy-ui-api.p12` from an x-road that has been installed from packages, to local `/etc/xroad/ssl/`
2. create a new `proxy-ui-api.p12` with password `proxy-ui-api`, following for example [these instructions](https://www.baeldung.com/spring-boot-https-self-signed-certificate) and store this in local `/etc/xroad/ssl/`
3. store a keystore elsewhere, and configure it's location and password with `ssl.properties`: https://github.com/nordic-institute/X-Road-REST-UI/blob/develop/doc/Manuals/ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-rest-api

(explain certificate file)

# Api key administration

Api keys can be created, listed and revoked through an administration API.

For details and example commands, see
[Security server user guide](https://github.com/nordic-institute/X-Road-REST-UI/blob/XRDDEV-237/doc/Manuals/ug-ss_x-road_6_security_server_user_guide.md#19-management-rest-apis)

# Authentication

There are two possible authentication mechanims
- PAM authentication

## PAM authentication

PAM authentication is active by default.

PAM authentication is done using unix user and password. There's some requirements
- application has to be run as user who can read `/etc/shadow`
- roles are granted using linux groups `xroad-security-officer`,
`xroad-registration-officer`,
`xroad-service-administrator`,
`xroad-system-administrator`, and
`xroad-securityserver-observer` as in old implementation.

To set some test users up
```
sudo usermod -a -G shadow <user which runs the app>
```
Better logout / login at this point to make sure PAM works.

Then some users, lets say xrd-full-user and xrd-system-admin:
```
(if these groups do not exist. They do exist for an operational (dockerized) security server)
sudo groupadd xroad-security-officer
sudo groupadd xroad-registration-officer
sudo groupadd xroad-service-administrator
sudo groupadd xroad-system-administrator
sudo groupadd xroad-securityserver-observer

sudo useradd -G xroad-security-officer xrd-full-user --shell=/bin/false
sudo useradd -G xroad-system-administrator xrd-system-admin --shell=/bin/false
sudo usermod -a -G xroad-registration-officer,xroad-service-administrator,xroad-system-administrator,xroad-securityserver-observer xrd-full-user

sudo passwd xrd-full-user
sudo passwd xrd-system-admin
```

# CSRF protection

When using session cookie authentication for /test-api apis,
[Spring's CSRF prevention](https://docs.spring.io/spring-security/site/docs/5.1.1.RELEASE/reference/htmlsingle/#csrf)
mechanism is used.

CSRF prevention checks that http header `X-XSRF-TOKEN` matches cookie `XSRF-TOKEN`. This way attacker cannot execute
unwanted requests against the apis just by triggering hostile requests from browser to rest apis and relying on the fact
that browser will send session cookie automatically.

Frontend would use CSRF token like so:
1. Call /login, store `XSRF-TOKEN` cookie from the response
2. Call api, include
 - JESSIONID cookie for session
 - XSRF-TOKEN with original csrf token value
 - http header X-XSRF-TOKEN with original csrf token value

Implementation uses CookieCsrfTokenRepository and token does not have to be the original token as long as cookie
and header match. We could also use HttpSessionCsrfTokenRepository but the security level would not be
substantially different and it would probably make Vue+Login+RestAPI integration more complex.

This implements [Cookie-to-Header token pattern](https://medium.com/spektrakel-blog/angular2-and-spring-a-friend-in-security-need-is-a-friend-against-csrf-indeed-9f83eaa9ca2e)

Examples:

Login
```
curl -X POST -k -d "username=admin&password=password" -D - https://localhost:4000/login
HTTP/1.1 200
Set-Cookie: XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335; Path=/
Set-Cookie: JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4; Path=/; HttpOnly
```
Using the cookies and CSRF header correctly
```
curl -D -k - https://localhost:4000/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 200
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

Actual CSRF token value does not matter
```
curl -D - -k https://localhost:4000/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: foo"
HTTP/1.1 200
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

But it needs to exist and match the value from cookie
```
curl -D - -k https://localhost:4000/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: bar"
HTTP/1.1 403
```

```
curl -D - -k https://localhost:4000/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo"
HTTP/1.1 403
```

```
curl -D - -k https://localhost:4000/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 403
```
