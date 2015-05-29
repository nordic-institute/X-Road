package ee.ria.xroad.proxy.testsuite.testcases;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SECURITY_CATEGORY;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class SecurityCategory extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public SecurityCategory() {
        requestFileName = "getstate.query";
    }

    @Override
    public Set<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        Set<SecurityCategoryId> ret = new HashSet<>();

        ret.add(SecurityCategoryId.create("EE", "CAT1"));
        ret.add(SecurityCategoryId.create("EE", "CAT2"));

        return ret;
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories() {
        return Collections.singleton(SecurityCategoryId.create("EE", "CAT3"));
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SECURITY_CATEGORY);
    }
}
