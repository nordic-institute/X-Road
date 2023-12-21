## Naming conventions
### Feature naming
* Feature files are numbered as they're **executed in alphabetical order**.
* Kebab case must be used for files names.
* It is OK to make granular feature files per feature if that makes scenarios more readable.

### Scenario naming
* Scenarios must describe what they're testing.
* Scenarios are executing in sequential order. From top to bottom.
* Naming must be unique.

# Running

Default properties do not specify what environment should be used. This configuration should be passed through gradle or
env variables.

Example:

```
./gradlew :security-server:system-test:systemTest -PsystemTestSsPackageHost="<package-host>" -PsystemTestSsTags="@SecurityServer and not @Skip"
```

# Reports

Test execution generates HTML report which can be viewed from:

```
build/allure-report/index.html
```

NOTE: to avoid partially loaded report use any kind of web server. For example IntelliJ properly opens it if you use
embedded html server (index.html -> Open In -> Browser)

### Using embedded report viewer

Allure reporting has embedded web server which can serve latest report. To enable it add this to jvmArgs:
``
-Pe2eTestServeReport=true
``

See the logs for the address.

You can close embedded server with control + c.


