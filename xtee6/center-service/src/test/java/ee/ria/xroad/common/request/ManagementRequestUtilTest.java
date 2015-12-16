package ee.ria.xroad.common.request;

import org.hamcrest.Matcher;
import org.hamcrest.core.StringContains;
import org.junit.Test;

import ee.ria.xroad.common.message.SoapMessageImpl;
import static ee.ria.xroad.common.message.SoapMessageTestUtil.createRequest;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests that ensure management requests are handled properly.
 */
public class ManagementRequestUtilTest {

    /**
     * Test adding id to the response.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void addIdToResponse() throws Exception {
        // Given
        SoapMessageImpl request = createRequest("simple.query");
        int id = 10;

        // When
        SoapMessageImpl response = ManagementRequestUtil.toResponse(request, id);

        // Then
        assertThat(response.getXml(), containsRequestId());
    }

    private Matcher<? super String> containsRequestId() {
        return new StringContains("<xroad:requestId>10</xroad:requestId>"
                + "</ns1:testQueryResponse>");
    }
}
