package ee.cyber.xroad.mediator;

import java.util.List;

import org.junit.Rule;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static org.junit.Assert.*;

public class MediatorServerConfTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    //@BeforeClass
    public static void loadConf() {
    }

    //@Test
    public void sdsbService() throws Exception {
        ServiceId serviceId =
                ServiceId.create("EE", "riigiasutus", "ppa", null, "getState");
        assertTrue(MediatorServerConf.isSdsbService(serviceId));
    }

    //@Test
    public void xroadv5Service() throws Exception {
        ServiceId serviceId =
                ServiceId.create("EE", "foo", "bar", null, "getState");
        assertFalse(MediatorServerConf.isSdsbService(serviceId));
    }

    //@Test
    public void unknownBackend() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        ServiceId serviceId =
                ServiceId.create("EE", "baz", "xxx", null, "getState");
        MediatorServerConf.isSdsbService(serviceId);
    }

    //@Test
    public void serviceNotFound() throws Exception {
        thrown.expectError(X_UNKNOWN_SERVICE);

        ServiceId serviceId =
                ServiceId.create("EE", "abc", "def", null, "getState");
        MediatorServerConf.isSdsbService(serviceId);
    }

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
