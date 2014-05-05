package ee.cyber.sdsb.proxy.securelog;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ee.cyber.sdsb.common.signature.SignatureManifest;
import ee.cyber.sdsb.common.util.CryptoUtils;

public class LogUtil {
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static byte[] bytes(String string) {
        return string.getBytes(CHARSET);
    }

    public static long getUnixTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    public static String concat(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean notFirst = false;
        for (String part : parts) {
            if (part == null) {
                continue;
            }

            if (notFirst) {
                sb.append(separator);
            } else {
                notFirst = true;
            }
            sb.append(part);
        }
        return sb.toString();
    }

    public static Element createReferenceInfoElement(Document doc,
            String tsManifest, String hashAlg) throws Exception {

        byte[] tsManifestDigest =
                CryptoUtils.calculateDigest(hashAlg, bytes(tsManifest));

        Element refInfoElement = SignatureManifest.createReferenceInfoElement(
                doc, hashAlg, CryptoUtils.encodeBase64(tsManifestDigest));

        doc.getDocumentElement().appendChild(refInfoElement);

        return refInfoElement;
    }
}
