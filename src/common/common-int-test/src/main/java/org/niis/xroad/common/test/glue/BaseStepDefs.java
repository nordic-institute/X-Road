package org.niis.xroad.common.test.glue;

import com.nortal.test.core.report.TestReportService;
import com.nortal.test.core.services.CucumberScenarioProvider;
import com.nortal.test.core.services.ScenarioContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

public class BaseStepDefs {
    @Autowired
    protected CucumberScenarioProvider scenarioProvider;
    @Autowired
    protected ScenarioContext scenarioContext;
    @Autowired
    protected TestReportService testReportService;

    /**
     * Put a value in scenario context. Value can be accessed through getStepData.
     *
     * @param key   value key. Non-null.
     * @param value value
     */
    protected void putStepData(StepDataKey key, Object value) {
        scenarioContext.putStepData(key.name(), value);
    }

    /**
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     */
    protected <T> Optional<T> getStepData(StepDataKey key) {
        return Optional.ofNullable(scenarioContext.getStepData(key.name()));
    }

    /**
     * An enumerated key for data transfer between steps.
     */
    public enum StepDataKey {
        TOKEN_TYPE,
        MANAGEMENT_REQUEST_ID,
        DOWNLOADED_FILE,
        CERT_FILE
    }
}
