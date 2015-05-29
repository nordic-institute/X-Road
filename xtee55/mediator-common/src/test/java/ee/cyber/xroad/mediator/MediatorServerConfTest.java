package ee.cyber.xroad.mediator;

import java.util.List;

import org.junit.Rule;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static org.junit.Assert.*;

/**
 * Tests to verify correct mediator server configuration behavior.
 */
public class MediatorServerConfTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Set up configuration.
     */
    //@BeforeClass
    public static void loadConf() {
    }

    /**
     * Test to ensure a X-Road 6.0 service is recognized.
     * @throws Exception in case of any unexpected errors
     */
    //@Test
    public void xroadService() throws Exception {
        ServiceId serviceId =
                ServiceId.create("EE", "riigiasutus", "ppa", null, "getState");
        assertTrue(MediatorServerConf.isXroadService(serviceId));
    }

    /**
     * Test to ensure a X-Road 5.0 service is recognized.
     * @throws Exception in case of any unexpected errors
     */
    //@Test
    public void xroadv5Service() throws Exception {
        ServiceId serviceId =
                ServiceId.create("EE", "foo", "bar", null, "getState");
        assertFalse(MediatorServerConf.isXroadService(serviceId));
    }

    /**
     * Test to ensure correct behavior in case of a service with unknown backend.
     * @throws Exception in case of any unexpected errors
     */
    //@Test
    public void unknownBackend() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        ServiceId serviceId =
                ServiceId.create("EE", "baz", "xxx", null, "getState");
        MediatorServerConf.isXroadService(serviceId);
    }

    /**
     * Test to ensure correct behavior in case of an unknown service.
     * @throws Exception in case of any unexpected errors
     */
    //@Test
    public void serviceNotFound() throws Exception {
        thrown.expectError(X_UNKNOWN_SERVICE);

        ServiceId serviceId =
                ServiceId.create("EE", "abc", "def", null, "getState");
        MediatorServerConf.isXroadService(serviceId);
    }

    /**
     * Test to ensure adapter WSDL URLs are retrieved correctly.
     * @throws Exception in case of any unexpected errors
     */
    //@Test
    public void getAdapterWSDLUrls() throws Exception {
        ClientId clientId = ClientId.create("EE", "foo", "bar");

        List<String> adapterWSDLUrls =
                MediatorServerConf.getAdapterWSDLUrls(clientId);

        assertEquals(1, adapterWSDLUrls.size());
        assertEquals(
                "http://iks2-test2.cyber.ee:8081/adapterwsdl",
                adapterWSDLUrls.get(0));
    }
}
