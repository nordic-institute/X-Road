package ee.ria.xroad.asyncsender;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import ee.ria.xroad.common.message.SoapFault;

final class TestUtils {

    private TestUtils() {
    }

    static InputStream getSimpleMessage() throws Exception {
        return new FileInputStream("src/test/resources/getstate.query");
    }

    static InputStream getFaultMessage(String faultCode, String faultString,
            String faultActor, String detail) throws Exception {
        return new ByteArrayInputStream(SoapFault.createFaultXml(faultCode,
                faultString, faultActor, detail).getBytes(
                        StandardCharsets.UTF_8));
    }
}
