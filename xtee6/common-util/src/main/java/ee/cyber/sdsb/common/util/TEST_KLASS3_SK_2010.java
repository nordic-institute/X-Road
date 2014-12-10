package ee.cyber.sdsb.common.util;

import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

public class TEST_KLASS3_SK_2010 {

    public static String[] getSubjectIdentifier(X509Certificate cert)
            throws Exception {
        X500Principal p = cert.getSubjectX500Principal();
        return getSubjectIdentifier(new X500Name(p.getName()));
    }

    static String[] getSubjectIdentifier(X500Name x500name) throws Exception {
        String c = CertUtils.getRDNValue(x500name, BCStyle.C);
        if (c == null) {
            throw new Exception("Subject name does not contain country code");
        }

        if (!"EE".equalsIgnoreCase(c)) {
            throw new Exception("Unsupported country code: " + c);
        }

        String sn = CertUtils.getRDNValue(x500name, BCStyle.SERIALNUMBER);
        if (sn == null) {
            throw new Exception("Subject name does not contain registry code");
        }

        return new String[] { getMemberClass(sn), sn };
    }

    // Returns the hardcoded member class based on the first number in
    // the serial number.
    private static String getMemberClass(String sn) throws Exception {
        switch (sn.charAt(0)) {
            case '7':
                return "GOV";
            case '1': // Fall through
            case '8': // Fall through
            case '9': // Fall through
                return "COM";
            default:
                throw new Exception("Certificate does not match policy: "
                        + "registry code must start with 1, 7, 8 or 9");
        }
    }
}
