/*
 * The MIT License
 *
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
package org.niis.xroad.ss.test.addons.glue;

import com.nortal.test.asserts.Assertion;
import com.nortal.test.asserts.ValidationHelper;
import com.nortal.test.asserts.ValidationService;
import com.nortal.test.core.services.ScenarioContext;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

public abstract class BaseStepDefs {

    @Autowired
    protected ScenarioContext scenarioContext;

    @Autowired
    protected ValidationService validationService;

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

    protected ValidationHelper validate(Object context) {
        return new ValidationHelper(validationService, context, "Validate response");
    }

    protected Assertion equalsStatusCodeAssertion(HttpStatus expected) {
        return new Assertion.Builder()
                .message("Verify status code")
                .expression("statusCode")
                .expectedValue(expected)
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
    protected String evalXpath(String body, String xpath) {
        InputStream is = new ByteArrayInputStream(body.getBytes());

        XPath xpathEvaluator = XPathFactory.newInstance().newXPath();

        var namespace = new SimpleNamespaceContext();
        namespace.bindNamespaceUri("soap", "http://schemas.xmlsoap.org/soap/envelope/");
        namespace.bindNamespaceUri("id", "http://x-road.eu/xsd/identifiers");
        namespace.bindNamespaceUri("xroad", "http://x-road.eu/xsd/xroad.xsd");
        namespace.bindNamespaceUri("monitoring", "http://x-road.eu/xsd/monitoring");
        xpathEvaluator.setNamespaceContext(namespace);

        InputSource inputSource = new InputSource(is);
        return xpathEvaluator.evaluate(xpath, inputSource);
    }

    /**
     * An enumerated key for data transfer between steps.
     */
    public enum StepDataKey {
        XROAD_SOAP_RESPONSE,
        XROAD_JMX_RESPONSE
    }

}
