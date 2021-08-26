package org.niis.xroad.centralserver.restapi.service;

import org.junit.Test;
import org.niis.xroad.centralserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.centralserver.restapi.entity.SystemParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.INSTANCE_IDENTIFIER;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CentralServerSystemParameterServiceTest extends AbstractFacadeMockingTestContext {




    @Autowired
    CentralServerSystemParameterService centralServerSystemParameterService;

    @Test
    public void mockContextLoads() {
        assertTrue(true);
    }

    @Test
    public void systemParameterValueStored() {
        final String instanceTestValue = "VALID_INSTANCE";
        SystemParameter systemParameter = centralServerSystemParameterService
                .updateOrCreateParameter(
                        INSTANCE_IDENTIFIER,
                        instanceTestValue
                );
        assertEquals(instanceTestValue, systemParameter.getValue());
        String storedSystemParameterValue = centralServerSystemParameterService
                .getParameterValue(
                        INSTANCE_IDENTIFIER,
                        "not-from-db");
        assertNotEquals("not-from-db", storedSystemParameterValue);
        assertEquals(instanceTestValue, storedSystemParameterValue);
    }

}
