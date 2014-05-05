package ee.cyber.sdsb.proxy.testsuite;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MonitorAgentProvider;

public class TestSuiteMonitorAgent implements MonitorAgentProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestSuiteMonitorAgent.class);

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
            if (obj instanceof ApiCall &&
                    (((ApiCall) obj).params == null || params == null)) {
                return ((ApiCall) obj).name.equals(name);
            }

            return EqualsBuilder.reflectionEquals(this, obj);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    private final List<ApiCall> expectedApiCalls = new ArrayList<>();

    private boolean apiCalled;

    public void expectSuccess() {
        expectedApiCalls.add(new ApiCall(SUCCESS));
    }

    public void expectSuccess(MessageInfo messageInfo) {
        expectedApiCalls.add(new ApiCall(SUCCESS,
                new Object[] { messageInfo.origin, messageInfo.client,
                    messageInfo.service }));
    }

    public void expectServerProxyFailed() {
        expectedApiCalls.add(new ApiCall(SERVER_PROXY_FAILED));
    }

    public void expectServerProxyFailed(MessageInfo messageInfo) {
        expectedApiCalls.add(new ApiCall(SERVER_PROXY_FAILED,
                new Object[] { messageInfo.origin, messageInfo.client,
                    messageInfo.service }));
    }

    public void expectFailure() {
        expectedApiCalls.add(new ApiCall(FAILURE));
    }

    public void expectFailure(MessageInfo messageInfo, String faultCode) {
        expectedApiCalls.add(new ApiCall(FAILURE,
                new Object[] { messageInfo.origin, messageInfo.client,
                    messageInfo.service, faultCode }));
    }

    public void verifyAPICalls() {
        if (!apiCalled) {
            throw new RuntimeException("MonitorAgent expected API calls");
        }
    }

    @Override
    public void success(MessageInfo messageInfo, Date startTime, Date endTime) {
        LOG.info("success({}, {}, {})",
                new Object[] { messageInfo, startTime, endTime });

        if (messageInfo == null) {
            assertApiCall(new ApiCall(SUCCESS));
        } else {
            assertApiCall(new ApiCall(SUCCESS,
                    new Object[] { messageInfo.origin, messageInfo.client,
                        messageInfo.service }));
        }
    }

    @Override
    public void serverProxyFailed(MessageInfo messageInfo) {
        LOG.info("serverProxyFailed({})", messageInfo);

        if (messageInfo == null) {
            assertApiCall(new ApiCall(SERVER_PROXY_FAILED));
        } else {
            assertApiCall(new ApiCall(SERVER_PROXY_FAILED,
                    new Object[] { messageInfo.origin, messageInfo.client,
                        messageInfo.service }));
        }
    }

    @Override
    public void failure(MessageInfo messageInfo, String faultCode,
            String faultMessage) {
        LOG.info("failure({}, {}, {})",
                new Object[] { messageInfo, faultCode, faultMessage });

        if (messageInfo == null) {
            assertApiCall(new ApiCall(FAILURE, new Object[] { faultCode }));
        } else {
            assertApiCall(new ApiCall(FAILURE,
                    new Object[] { messageInfo.origin, messageInfo.client,
                        messageInfo.service, faultCode }));
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
