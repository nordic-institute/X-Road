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
profile `development`
```
../gradlew clean build -Pactivate-devtools-do-not-use-in-production
```
```
java -jar proxy-ui-api-1.0.jar --spring.profiles.active=development
```
# Running
```
../gradlew bootRun --console plain
```

browser: `http://localhost:8020`

# Api auth examples
```
$ curl --header "Authorization: naive-api-key-1" localhost:8020/api/cities
{"timestamp":"2018-11-27T07:09:03.991+0000","status":500,"error":"Internal Server Error","message":"The API key was not found or not the expected value.","path":"/api/adminCities"}

$ curl -X POST -u admin:password docker-ss.local:8020/api/create-api-key --data '["XROAD_SECURITY_OFFICER"]' --header "Content-Type: application/json"
{"key":"naive-api-key-1","roles":["XROAD_SECURITY_OFFICER"]}

$ curl -X POST -u admin:password docker-ss.local:8020/api/create-api-key --data '["XROAD_SYSTEM_ADMINISTRATOR"]' --header "Content-Type: application/json"
{"key":"naive-api-key-2","roles":["XROAD_SYSTEM_ADMINISTRATOR"]}

$ curl -X POST -u admin:password docker-ss.local:8020/api/create-api-key --data '["XROAD_SECURITY_OFFICER", "XROAD_SYSTEM_ADMINISTRATOR"]' --header "Content-Type: application/json"
{"key":"naive-api-key-3","roles":["XROAD_SECURITY_OFFICER","XROAD_SYSTEM_ADMINISTRATOR"]}

$ curl --header "Authorization: naive-api-key-1" "docker-ss.local:8020/api/clients"
[{"id":"XRD2:GOV:M1:SUB1","member_name":"member1","member_class":"GOV","member_code":"M1","subsystem_code":"SUB1","status":"saved"},{"id":"XRD2:GOV:M4:SS1","member_name":"member4","member_class":"GOV","member_code":"M4","subsystem_code":"SS1","status":"registered"},{"id":"XRD2:GOV:M4","member_name":"member4","member_class":"GOV","member_code":"M4","subsystem_code":null,"status":"registered"}]

$ curl --header "Authorization: naive-api-key-2" "docker-ss.local:8020/api/clients"
{"timestamp":"2018-11-27T07:08:22.398+0000","status":403,"error":"Forbidden","message":"Forbidden","path":"/api/clients"}
```

# PAM login

PAM login is active by default. You can use dummy in-memory authentication with parameter `proto.pam=false`:

```
../gradlew bootRun --console plain -Pargs=--proto.pam=false
```

Logins for dummy in-memory authentication: 
- user/password
- admin/password etc

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

sudo passwd xroad-admin-user
sudo passwd xroad-admin
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
curl -X POST -d "username=admin&password=password" -D - localhost:8020/login
HTTP/1.1 200 
Set-Cookie: XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335; Path=/
Set-Cookie: JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4; Path=/; HttpOnly
```
Using the cookies and CSRF header correctly
```
curl -D - localhost:8020/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 200 
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

Actual CSRF token value does not matter
```
curl -D - localhost:8020/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: foo"
HTTP/1.1 200 
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

But it needs to exist and match the value from cookie
```
curl -D - localhost:8020/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: bar"
HTTP/1.1 403 
```

```
curl -D - localhost:8020/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo"
HTTP/1.1 403 
```

```
curl -D - localhost:8020/api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 403 
```
