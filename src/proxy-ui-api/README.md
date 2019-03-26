# Building

Default build does frontend `npm run build` and packages the built frontend assets from `frontend/dist`
inside the spring boot jar. You can access the frontend from `http://localhost:8020/index.html`

```
../gradlew clean build
```

For faster builds, you can skip frontend build with `skip-frontend-build` parameter.
In this case the spring boot jar does not contain frontend assets, but can be used with frontend
that is being served from webpack development server.
```
../gradlew clean build -Pskip-frontend-build
```

Parameter `activate-devtools-do-not-use-in-production` can be used to build a jar that contains spring
devtools, which enable remote hot deployment of code. In this case, jar needs to be started with spring
profile `development`.

```
../gradlew clean build -Pactivate-devtools-do-not-use-in-production
```
```
java -jar proxy-ui-api-1.0.jar --spring.profiles.active=development
```
`activate-devtools-do-not-use-in-production` also makes it possible to use development login
with hard coded users. This is activated with profile `dev-test-auth`.

# Running
```
../gradlew bootRun --console plain
```

browser: `https://localhost:8553`

# Api key database tables

To prepare `serverconf` database to store API keys, execute one sql file.
Executing SQL file by hand is a temporary solution, and will be replaced when
packaging is implemented.

Copy sql script to target, in this example a docker container:

```
$ docker cp src/main/resources/create_apikey_tables.sql <docker-container-name>:/
```
Execute sql script, in this example inside docker container, as `root`:
```
$ su -c "psql -d serverconf -a -f /create_apikey_tables.sql" postgres
```

# TLS

Application listens to https in port 8553.

Since it uses a self-signed certificate (currently in `nginx.p12`
keystore embedded in the build), clients need to trust this certificate. In browser access this means manually allow exception for
"your connection is not secure" warning. For `curl` commands this means `-k` parameter (which you can see used in the examples).

# Api key administration

Api keys can be created, listed and revoked through an administration API. Administration API access
has restrictions:

- user needs to have role `XROAD_SYSTEM_ADMINISTRATOR`
- access is allowed only from localhost
- authentication is done with basic auth (username & password)

Create a new key with a POST request to `/api/api-key`.
Request body should contain list of roles as array of strings.
Response contains the key. The key is not stored and can not be retrieved after this.

```
$ curl -X POST -u <username>:<password> https://localhost:8553/api/api-key --data '["XROAD_SERVICE_ADMINISTRATOR","XROAD_SECURITYSERVER_OBSERVER","XROAD_REGISTRATION_OFFICER"]' --header "Content-Type: application/json" -k
{
  "roles": [
    "XROAD_SECURITYSERVER_OBSERVER",
    "XROAD_REGISTRATION_OFFICER",
    "XROAD_SERVICE_ADMINISTRATOR"
  ],
  "id": 27,
  "key": "40ddbdd1-ee46-4b0c-b812-a736a409cc32"
}
```
List all api keys with a GET request to `/api/api-key`.

```
$ curl -u <username>:<password> https://localhost:8553/api/api-key -k
[
...
  {
    "id": 27,
    "roles": [
      "XROAD_SECURITYSERVER_OBSERVER",
      "XROAD_REGISTRATION_OFFICER",
      "XROAD_SERVICE_ADMINISTRATOR"
    ]
  }
]
```
Delete a key with a DELETE request to `/api/api-key/<id>`.
```
$ curl -X DELETE -u <username>:<password> https://localhost:8553/api/api-key/27 -k
{
  "status": 200,
  "errorCode": null
}
```


# Example of using API key

Provide api key with `Authorization: X-Road-ApiKey token=<api key>` header.

```
$ curl --header "Authorization: X-Road-ApiKey token=481e50de-a93f-46d8-9748-1bca86eea454" "https://docker-ss.local:8553/api/clients" -k
[{"id":"XRD2:GOV:M1:SUB1","member_name":"member1","member_class":"GOV","member_code":"M1","subsystem_code":"SUB1","status":"saved"},{"id":"XRD2:GOV:M4:SS1","member_name":"member4","member_class":"GOV","member_code":"M4","subsystem_code":"SS1","status":"registered"},{"id":"XRD2:GOV:M4","member_name":"member4","member_class":"GOV","member_code":"M4","subsystem_code":null,"status":"registered"}]
```

# Authentication

There is two possible authentication mechanims
- PAM authentication
- Development authentication with static users

## PAM login

PAM login is active by default.

PAM login is done using unix user and password. There's some requirements
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

## Development authentication

You can use dummy in-memory authentication by building a development tools -enabled version
with `activate-devtools-do-not-use-in-production` parameter and then activating
profile `dev-test-auth`.

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
curl -X POST -k -d "username=admin&password=password" -D - https://localhost:8553/login
HTTP/1.1 200 
Set-Cookie: XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335; Path=/
Set-Cookie: JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4; Path=/; HttpOnly
```
Using the cookies and CSRF header correctly
```
curl -D -k - https://localhost:8553/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 200 
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

Actual CSRF token value does not matter
```
curl -D - -k https://localhost:8553/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: foo"
HTTP/1.1 200 
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

But it needs to exist and match the value from cookie
```
curl -D - -k https://localhost:8553/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: bar"
HTTP/1.1 403 
```

```
curl -D - -k https://localhost:8553/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo"
HTTP/1.1 403 
```

```
curl -D - -k https://localhost:8553/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 403 
```
