# Integration test development practices

**Basic rules:**
* Integration scenarios should cover main business cases (including happy paths, api validation, errors, etc.).
* Corner cases, scenarios which could be easily covered by a unit test should not be present here.
* Always follow GivenWhenThen style. https://martinfowler.com/bliki/GivenWhenThen.html
* Scenario steps should be easily readable. Avoid hard-coding data in step definition, if possible expose it in
  scenario.
* Mark scenarios which alter application state (ex: database) as @Modifying.

**General notes:**
* Docker is used to run application in isolation.
* Most (unless specified otherwise) scenarios are executed in parallel.

## Scenario tagging

### General guidelines

Each scenario should be tagged either at feature or scenario level. Tags should represent part of functionality they're
testing.

PLEASE USE EXISTING TAGS BEFORE CREATING NEW ONES.

### Tags with hardcoded behaviour:

Some tags are not only for sorting and filtering, but they also add additional behaviour while executing.

| Tag        | Description                                                                                                                                                                                                                          |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| @Modifying | This means that scenario has made modifications in the database. <br/>Test framework will drop and recreate schema after these scenarios.<br/>**Modifying scenarios will always execute sequentially and isolated from other tests** |

## Scenario step data management

Step data ideally should be passed by variables within StepDefs classes. Example:
```java
public class SystemApiStepDefs extends BaseStepDefs {
    @Autowired
    private FeignSystemApi feignSystemApi;

    private HttpStatus responseStatus;

    @Then("System status is requested")
    public void systemStatusIsRequested() {
        var response = feignSystemApi.getSystemStatus();

        responseStatus = response.getStatusCode();
    }

    @Then("System status is validated")
    public void systemStatusIsValidated() {
        Assertions.assertThat(responseStatus.is2xxSuccessful()).isTrue();
    }
}
```

In cases where data is shared cross StepDefs use stepData management methods from `BaseStepDefs` class.
Step data key is an enum, any new key must be defined in `BaseStepDefs.StepDataKey`

Example:
```java
public class SystemApiStepDefs extends BaseStepDefs {
    @Autowired
    private FeignSystemApi feignSystemApi;

    @Then("System status is requested")
    public void systemStatusIsRequested() {
        var response = feignSystemApi.getSystemStatus();

        putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCode());
    }

    @Then("System status is validated")
    public void systemStatusIsValidated() {
        HttpStatus responseStatus = getRequiredStepData(StepDataKey.RESPONSE_STATUS);

        Assertions.assertThat(responseStatus.is2xxSuccessful()).isTrue();
    }
}

```

## Test data management
Test data is managed by liquibase changesets which are defined in `test-data/centerui-int-test-changelog.xml`
When adding something, keep in mind that this data is used by all scenarios.

Data which might break other scenarios should be part of scenario itself.
If particular data cannot be added through API - consider writing unit test.

