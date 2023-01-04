/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.test.glue;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.nortal.test.asserts.Assertion;
import com.nortal.test.asserts.AssertionOperation;
import com.nortal.test.asserts.ValidationHelper;
import com.nortal.test.asserts.ValidationService;
import com.nortal.test.core.services.CucumberScenarioProvider;
import com.nortal.test.core.services.ScenarioContext;
import feign.FeignException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestPayload;
import org.niis.xroad.cs.test.api.FeignManagementRequestsApi;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.xml.sax.InputSource;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

/**
 * Base class for all step definitions. Provides convenience methods and most commonly used beans.
 */
@Slf4j
@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "checkstyle:MagicNumber"})
public abstract class BaseStepDefs {
    protected static MessageFactory messageFactory;

    @Autowired
    private ScenarioContext scenarioContext;
    @Autowired
    protected CucumberScenarioProvider cucumberScenarioProvider;
    @Autowired
    protected ValidationService validationService;
    @Autowired
    protected MockServerClient mockServerClient;
    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;

    protected Assertion equalsStatusCodeAssertion(HttpStatus expected) {
        return new Assertion.Builder()
                .message("Verify status code")
                .expression("statusCode")
                .expectedValue(expected)
                .build();
    }

    protected Assertion equalsStatusCodeAssertion(int actualValue, HttpStatus expectedValue) {
        return new Assertion.Builder()
                .message("Verify status code")
                .expression("=")
                .actualValue(actualValue)
                .expectedValue(expectedValue.value())
                .build();
    }

    protected ValidationHelper validate(Object context) {
        return new ValidationHelper(validationService, context, "Validate response");
    }

    protected Assertion xpathExists(String body, String expression) {
        return new Assertion.Builder()
                .expression("=")
                .operation(AssertionOperation.NOT_NULL)
                .actualValue(evalXpath(body, expression))
                .message("XPATH: " + expression)
                .build();
    }

    protected Assertion xpath(String body, String expression, String expectedValue) {
        return new Assertion.Builder()
                .expression("=")
                .expectedValue(expectedValue)
                .actualValue(evalXpath(body, expression))
                .message("XPATH: " + expression)
                .build();
    }

    @SneakyThrows
    private String evalXpath(String body, String xpath) {
        InputStream is = new ByteArrayInputStream(body.getBytes());

        XPath xpathEvaluator = XPathFactory.newInstance().newXPath();

        var namespace = new SimpleNamespaceContext();
        namespace.bindNamespaceUri("soap", "http://schemas.xmlsoap.org/soap/envelope/");
        namespace.bindNamespaceUri("xroad", "http://x-road.eu/xsd/xroad.xsd");
        xpathEvaluator.setNamespaceContext(namespace);

        InputSource inputSource = new InputSource(is);
        return xpathEvaluator.evaluate(xpath, inputSource);
    }

    protected void executeRequest(TestManagementRequestPayload payload) {
        ResponseEntity<String> responseEntity = null;
        try {
            responseEntity = managementRequestsApi.addManagementRequest(payload.getContentType(), payload.getPayload());
        } catch (FeignException e) {
            responseEntity = ResponseEntity.status(e.status()).body(e.contentUTF8());
        } catch (Exception e) {
            log.error("Unexpected feign client failure.", e);
        }
        putStepData(StepDataKey.RESPONSE, responseEntity);
    }

    protected ClientId.Conf resolveClientIdFromEncodedStr(String clientIdStr) {
        String[] clientIdSplit = clientIdStr.split(":");
        return ClientId.Conf.create(clientIdSplit[0], clientIdSplit[1], clientIdSplit[2]);
    }

    protected SecurityServerId.Conf resolveServerIdFromEncodedStr(String serverIdStr) {
        String[] serverIdSplit = serverIdStr.split(":");
        return SecurityServerId.Conf.create(serverIdSplit[0], serverIdSplit[1], serverIdSplit[2], serverIdSplit[3]);
    }

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
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     * @throws AssertionFailedError thrown if value is missing
     */
    protected <T> T getRequiredStepData(StepDataKey key) throws AssertionFailedError {
        return scenarioContext.getRequiredStepData(key.name());
    }

    /**
     * An enumerated key for data transfer between steps.
     */
    public enum StepDataKey {
        RESPONSE,
        RESPONSE_BODY,
        RESPONSE_STATUS,
        CERTIFICATION_SERVICE_ID,
        OCSP_RESPONDER_ID,
        NEW_OCSP_RESPONDER_URL
    }


    static {
        try {
            messageFactory = MessageFactory.newInstance();
        } catch (SOAPException e) {
            log.error("Unexpected error", e);
        }
    }
}
