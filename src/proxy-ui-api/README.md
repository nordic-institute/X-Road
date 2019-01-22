# GIT

from https://bitbucket.niis.org/projects/X-ROAD/repos/x-road-ui-proto/browse
```
commit ef996ba39f1bd55c38e85f31522010e713f27346 (HEAD -> master, origin/master)
Author: Mikko Riippi <mikko.riippi@gofore.com>
Date:   Mon Jan 21 15:56:15 2019 +0200

    Fixed a typo
```

# Running
```
../gradlew bootRun --console plain
```

browser: `http://localhost:8020`

logins for dummy in-memory authentication: 
- user/password
- admin/password etc

# Api auth examples
```
$ curl --header "Authorization: naive-api-key-1" localhost:8020/test-api/cities
{"timestamp":"2018-11-27T07:09:03.991+0000","status":500,"error":"Internal Server Error","message":"The API key was not found or not the expected value.","path":"/test-api/adminCities"}

$ curl -X POST -u admin:password localhost:8020/test-api/create-api-key --data '["USER"]' --header "Content-Type: application/json"
{"key":"naive-api-key-1","roles":["USER"]}

$ curl -X POST -u admin:password localhost:8020/test-api/create-api-key --data '["ADMIN"]' --header "Content-Type: application/json"
{"key":"naive-api-key-2","roles":["ADMIN"]}

$ curl -X POST -u admin:password localhost:8020/test-api/create-api-key --data '["USER","ADMIN","GUGGU"]' --header "Content-Type: application/json"
{"key":"naive-api-key-3","roles":["GUGGU","ADMIN","USER"]}

curl --header "Authorization: naive-api-key-1" localhost:8020/test-api/roles
["ROLE_USER"]

curl --header "Authorization: naive-api-key-1" localhost:8020/test-api/cities
[{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]

curl --header "Authorization: naive-api-key-3" localhost:8020/test-api/adminCities
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]

curl --header "Authorization: naive-api-key-1" localhost:8020/test-api/adminCities
{"timestamp":"2018-11-27T07:08:22.398+0000","status":403,"error":"Forbidden","message":"Forbidden","path":"/test-api/adminCities"}
```

# PAM login

Start with parameter `proto.pam=true` to activate PAM login:

```
../gradlew bootRun --console plain -Pargs=--proto.pam=true
```

Login using unix user and password. There's some requirements
- application has to be run as user who can read `/etc/shadow`
- users with groups `xroad-auth-proto-admin` or `xroad-auth-proto-admin` can login and get USER/ADMIN roles

To set these up
```
sudo usermod -a -G shadow <user which runs the app>
```
Better logout / login at this point to make sure PAM works.

Then some users, lets say xroad-user, xroad-admin, xroad-admin-user:
```
sudo groupadd xroad-auth-proto-admin
sudo groupadd xroad-auth-proto-user

sudo useradd -G xroad-auth-proto-user xroad-user --shell=/bin/false
sudo useradd -G xroad-auth-proto-admin xroad-admin --shell=/bin/false
sudo useradd -G xroad-auth-proto-admin xroad-admin-user --shell=/bin/false
sudo usermod -a -G xroad-auth-proto-user xroad-admin-user

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
curl -D - localhost:8020/test-api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=45eeef1e-3d0a-4dea-9a65-b84f9a505335" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 200 
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

Actual CSRF token value does not matter
```
curl -D - localhost:8020/test-api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: foo"
HTTP/1.1 200 
[{"id":999,"name":"Admincity, from a method which requires 'ADMIN' role"},{"id":1,"name":"Tampere"},{"id":2,"name":"Ylojarvi"},{"id":3,"name":"Helsinki"},{"id":4,"name":"Vantaa"},{"id":5,"name":"Nurmes"}]
```

But it needs to exist and match the value from cookie
```
curl -D - localhost:8020/test-api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo" --header "X-XSRF-TOKEN: bar"
HTTP/1.1 403 
```

```
curl -D - localhost:8020/test-api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4;XSRF-TOKEN=foo"
HTTP/1.1 403 
```

```
curl -D - localhost:8020/test-api/adminCities --cookie "JSESSIONID=1BE8A92CFAD40516BA4E6008646882E4" --header "X-XSRF-TOKEN: 45eeef1e-3d0a-4dea-9a65-b84f9a505335"
HTTP/1.1 403 
```
