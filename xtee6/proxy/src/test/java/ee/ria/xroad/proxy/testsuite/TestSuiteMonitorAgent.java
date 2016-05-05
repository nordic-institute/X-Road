/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.testsuite;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgentProvider;

/**
 * Monitor agent implementation for the testsuite.
 */
@Slf4j
public class TestSuiteMonitorAgent implements MonitorAgentProvider {

    private static final String FAILURE = "failure";
    private static final String SERVER_PROXY_FAILED = "serverProxyFailed";
    private static final String SUCCESS = "success";

    private class ApiCall {
        final String name;
        final Object[] params;

        ApiCall(String name) {
            this(name, (Object[]) null);
        }

        ApiCall(String name, Object[] params) {
            this.name = name;
            this.params = params;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ApiCall
                    && (((ApiCall) obj).params == null || params == null)) {
                return ((ApiCall) obj).name.equals(name);
            }

            return EqualsBuilder.reflectionEquals(this, obj);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    private final List<ApiCall> expectedApiCalls = new ArrayList<>();

    private boolean apiCalled;

    /**
     * Expect a success.
     */
    public void expectSuccess() {
        expectedApiCalls.add(new ApiCall(SUCCESS));
    }

    /**
     * Expect a success with the given message info.
     * @param messageInfo the message info
     */
    public void expectSuccess(MessageInfo messageInfo) {
        expectedApiCalls.add(new ApiCall(SUCCESS,
                new Object[] {messageInfo.getOrigin(), messageInfo.getClient(),
                    messageInfo.getService()}));
    }

    /**
     * Expect a server proxy failure.
     */
    public void expectServerProxyFailed() {
        expectedApiCalls.add(new ApiCall(SERVER_PROXY_FAILED));
    }

    /**
     * Expect a server proxy failure with the given message info.
     * @param messageInfo the message info
     */
    public void expectServerProxyFailed(MessageInfo messageInfo) {
        expectedApiCalls.add(new ApiCall(SERVER_PROXY_FAILED,
                new Object[] {messageInfo.getOrigin(), messageInfo.getClient(),
                    messageInfo.getService()}));
    }

    /**
     * Expect a failure.
     */
    public void expectFailure() {
        expectedApiCalls.add(new ApiCall(FAILURE));
    }

    /**
     * Expect a failure with the given message info and fault code.
     * @param messageInfo the message info
     * @param faultCode the fault code
     */
    public void expectFailure(MessageInfo messageInfo, String faultCode) {
        if (messageInfo == null) {
            expectedApiCalls.add(new ApiCall(FAILURE,
                    new Object[] {faultCode}));
        } else {
            expectedApiCalls.add(new ApiCall(FAILURE, new Object[] {
                    messageInfo.getOrigin(), messageInfo.getClient(),
                    messageInfo.getService(), faultCode}));
        }
    }

    /**
     * Verify that the monitor agent API calls were made.
     */
    public void verifyAPICalls() {
        if (!apiCalled) {
            throw new RuntimeException("MonitorAgent expected API calls");
        }
    }

    @Override
    public void success(MessageInfo messageInfo, Date startTime, Date endTime) {
        log.info("success({}, {}, {})",
                new Object[] {messageInfo, startTime, endTime});

        if (messageInfo == null) {
            assertApiCall(new ApiCall(SUCCESS));
        } else {
            assertApiCall(new ApiCall(SUCCESS, new Object[] {
                    messageInfo.getOrigin(), messageInfo.getClient(),
                    messageInfo.getService()}));
        }
    }

    @Override
    public void serverProxyFailed(MessageInfo messageInfo) {
        log.info("serverProxyFailed({})", messageInfo);

        if (messageInfo == null) {
            assertApiCall(new ApiCall(SERVER_PROXY_FAILED));
        } else {
            assertApiCall(new ApiCall(SERVER_PROXY_FAILED, new Object[] {
                    messageInfo.getOrigin(), messageInfo.getClient(),
                    messageInfo.getService()}));
        }
    }

    @Override
    public void failure(MessageInfo messageInfo, String faultCode,
            String faultMessage) {
        log.info("failure({}, {}, {})",
                new Object[] {messageInfo, faultCode, faultMessage});

        if (messageInfo == null) {
            assertApiCall(new ApiCall(FAILURE, new Object[] {faultCode}));
        } else {
            assertApiCall(new ApiCall(FAILURE, new Object[] {
                    messageInfo.getOrigin(), messageInfo.getClient(),
                    messageInfo.getService(), faultCode }));
        }
    }

    private void assertApiCall(ApiCall actual) {
        apiCalled = true;

        if (!expectedApiCalls.contains(actual)) {
            throw new RuntimeException(
                    "MonitorAgent got unexpected API call " + actual);
        }
    }

/*
    public static void main(String... args) {
        TestSuiteMonitorAgent a = new TestSuiteMonitorAgent();
        a.expectFailure();
        a.failure(null, "", "");

        a = new TestSuiteMonitorAgent();
        a.expectFailure(messageInfo("client", "service", "foo", "query1"),
                "code");
        a.failure(messageInfo("client", "service", "foo", "query1"),
                "code", "message");

        a = new TestSuiteMonitorAgent();
        a.expectSuccess();
        a.success(messageInfo("aa", "bb", "cc", "dd"), new Date(), new Date());
        a.success(null, null, null);

        System.out.println("OK");
    }

    private static MessageInfo messageInfo(String client, String service,
            String userId, String queryId) {
        return new MessageInfo(Origin.SERVER_PROXY,
                ClientId.create("EE", "BB", client),
                ServiceId.create("EE", "XX", "foobar", null, service),
                userId, queryId);
    }
*/
}
