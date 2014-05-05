package ee.cyber.sdsb.asyncsender;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import ee.cyber.sdsb.common.message.SoapFault;

public class TestUtils {

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
