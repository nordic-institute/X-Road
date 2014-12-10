package ee.cyber.xroad.common.util;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.xroad.common.CodedException;
import ee.cyber.xroad.common.ErrorCodes;

import static ee.cyber.xroad.common.util.CryptoUtils.toDERObject;

public class CertUtils {

    private CertUtils() {
    }

    /**
     * Returns short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static String getSubjectCommonName(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());

        String cn = getRDNValue(x500name, BCStyle.CN);
        if (cn == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        return cn;
    }

    /**
     * Returns the SerialNumber component from the Subject field.
     */
    public static String getSubjectSerialNumber(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());
        return getRDNValue(x500name, BCStyle.SERIALNUMBER);
    }

    /**
     * Returns a fully constructed Client identifier from DN of the certificate.
     */
    public static ClientId getSubjectClientId(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());

        String c = getRDNValue(x500name, BCStyle.C);
        if (c == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain country code");
        }

        String o = getRDNValue(x500name, BCStyle.O);
        if (o == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain organization");
        }

        String cn = getRDNValue(x500name, BCStyle.CN);
        if (cn == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        return ClientId.create(c, o, cn);
    }

    /**
     * Checks if this certificate is an authentication certificate.
     * The certificate is an authentication certificate, if it has
     * ExtendedKeyUsage extension which contains
     * <pre>ClientAuthentication</pre> or if it has keyUsage extension
     * which has <pre>digitalSignature</pre>, <pre>keyEncipherment</pre>
     * or <pre>dataEncipherment</pre> bit set.
     *
     * @throws Exception if the cert has no keyUsage extension
     */
    public static boolean isAuthCert(X509Certificate cert) throws Exception {
        List<String> extendedKeyUsage = cert.getExtendedKeyUsage();
        if (extendedKeyUsage != null
                && extendedKeyUsage.contains("1.3.6.1.5.5.7.3.2")) {
            return true;
        }

        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage == null) {
            throw new RuntimeException(
                    "Certificate does not contain keyUsage extension");
        }

        return keyUsage[0] || keyUsage[2] || keyUsage[3];
    }

    /**
     * Checks if this certificate is a signing certificate.
     * The certificate is a signing certificate, if it has keyUsage extension
     * which has <pre>nonRepudiation</pre> bit set.
     */
    public static boolean isSigningCert(X509Certificate cert)
            throws Exception {
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage == null) {
            throw new RuntimeException(
                    "Certificate does not contain keyUsage extension");
        }

        return keyUsage[1];
    }

    /**
     * Returns OCSP responder URI from given certificate.
     */
    public static String getOcspResponderUriFromCert(X509Certificate subject)
            throws IOException {
        final byte[] extensionValue = subject.getExtensionValue(
                Extension.authorityInfoAccess.toString());
        if (extensionValue != null) {
            ASN1Primitive derObject = toDERObject(extensionValue);
            if (derObject instanceof DEROctetString) {
                DEROctetString derOctetString = (DEROctetString) derObject;
                derObject = toDERObject(derOctetString.getOctets());

                AuthorityInformationAccess authorityInformationAccess =
                        AuthorityInformationAccess.getInstance(derObject);
                AccessDescription[] descriptions =
                        authorityInformationAccess.getAccessDescriptions();
                for (AccessDescription desc : descriptions) {
                    if (desc.getAccessMethod().equals(
                            AccessDescription.id_ad_ocsp)) {
                        GeneralName generalName = desc.getAccessLocation();
                        return generalName.getName().toString();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns the RDN value from the X500Name.
     */
    static String getRDNValue(X500Name name, ASN1ObjectIdentifier id) {
        RDN[] cnList = name.getRDNs(id);
        if (cnList.length == 0) {
            return null;
        }

        return IETFUtils.valueToString(cnList[0].getFirst().getValue());
    }
}
