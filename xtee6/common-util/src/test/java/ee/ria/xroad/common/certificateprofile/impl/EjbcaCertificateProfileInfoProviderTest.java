package ee.ria.xroad.common.certificateprofile.impl;

import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.util.Arrays;
import org.junit.Test;
import org.mockito.Mockito;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the default implementation of CertificateProfileInfoProvider.
 */
public class EjbcaCertificateProfileInfoProviderTest {

    @Test
    public void providerReturnsCorrectImplementations() {
        CertificateProfileInfoProvider provider = provider();
        assertTrue(
            "Must return instance of DefaultAuthCertificateProfileInfo",
            provider.getAuthCertProfile(
                new AuthCertificateProfileInfoParameters(
                    SecurityServerId.create("XX", "foo", "bar", "server"), "foo"
                )
            ) instanceof EjbcaAuthCertificateProfileInfo
        );

        assertTrue(
            "Must return instance of DefaultSignCertificateProfileInfo",
            provider.getSignCertProfile(
                new SignCertificateProfileInfoParameters(
                    ClientId.create("XX", "foo", "bar"), "foo"
                )
            ) instanceof EjbcaSignCertificateProfileInfo
        );
    }

    @Test
    public void signProfileSubjectFields() {
        DnFieldDescription[] expectedFields = {
            new DnFieldDescriptionImpl("C", "Instance Identifier (C)", "XX")
                .setReadOnly(true),
            new DnFieldDescriptionImpl("O", "Member Class (O)", "foo")
                .setReadOnly(true),
            new DnFieldDescriptionImpl("CN", "Member Code (CN)", "bar")
                .setReadOnly(true)
        };

        assertTrue(
            "Did not get expected fields",
            Arrays.areEqual(expectedFields, getSignProfile().getSubjectFields())
        );
    }

    @Test
    public void signProfileValidateFieldSuccessfully() throws Exception {
        getSignProfile().validateSubjectField(
            new DnFieldValueImpl("C", "XX")
        );
    }

    @Test(expected = Exception.class)
    public void signProfileFailToValidateUnknownField() throws Exception {
        getSignProfile().validateSubjectField(
            new DnFieldValueImpl("X", "foo")
        );
    }

    @Test(expected = Exception.class)
    public void signProfileFailToValidateBlankField() throws Exception {
        getSignProfile().validateSubjectField(
            new DnFieldValueImpl("O", "")
        );
    }

    @Test
    public void signProfileCreateSubjectDn() {
        assertEquals(
            new X500Principal("C=foo, O=bar, CN=baz"),
            getSignProfile().createSubjectDn(
                new DnFieldValue[] {
                    new DnFieldValueImpl("C", "foo"),
                    new DnFieldValueImpl("O", "bar"),
                    new DnFieldValueImpl("CN", "baz")
                }
            )
        );
    }

    @Test
    public void signProfileGetSubjectIdentifier() {
        X509Certificate mockCert = Mockito.mock(X509Certificate.class);
        Mockito.when(mockCert.getSubjectX500Principal()).thenReturn(
            new X500Principal("C=XX, O=Foo, CN=bar")
        );

        assertEquals(
            ClientId.create("XX", "Foo", "bar"),
            getSignProfile().getSubjectIdentifier(mockCert)
        );
    }

    @Test
    public void authProfileSubjectFields() {
        DnFieldDescription[] expectedFields = {
            new DnFieldDescriptionImpl("C", "Instance Identifier (C)", "XX")
                .setReadOnly(true),
            new DnFieldDescriptionImpl("CN", "Server Code (CN)", "server")
                .setReadOnly(true),
        };

        assertTrue(
            "Did not get expected fields",
            Arrays.areEqual(expectedFields, getAuthProfile().getSubjectFields())
        );
    }

    @Test
    public void authProfileValidateFieldSuccessfully() throws Exception {
        getAuthProfile().validateSubjectField(
            new DnFieldValueImpl("C", "XX")
        );
    }

    @Test(expected = Exception.class)
    public void authProfileFailToValidateUnknownField() throws Exception {
        getAuthProfile().validateSubjectField(
            new DnFieldValueImpl("O", "foo")
        );
    }

    @Test(expected = Exception.class)
    public void authProfileFailToValidateBlankField() throws Exception {
        getAuthProfile().validateSubjectField(
            new DnFieldValueImpl("CN", "")
        );
    }

    @Test
    public void authProfileCreateSubjectDn() {
        assertEquals(
            new X500Principal("C=foo, CN=baz"),
            getAuthProfile().createSubjectDn(
                new DnFieldValue[] {
                    new DnFieldValueImpl("C", "foo"),
                    new DnFieldValueImpl("CN", "baz")
                }
            )
        );
    }

    // ------------------------------------------------------------------------

    private CertificateProfileInfoProvider provider() {
        return new EjbcaCertificateProfileInfoProvider();
    }

    private EjbcaSignCertificateProfileInfo getSignProfile() {
        return new EjbcaSignCertificateProfileInfo(
            new SignCertificateProfileInfoParameters(
                ClientId.create("XX", "foo", "bar"),
                "foo"
            )
        );
    }

    private EjbcaAuthCertificateProfileInfo getAuthProfile() {
        return new EjbcaAuthCertificateProfileInfo(
            new AuthCertificateProfileInfoParameters(
                SecurityServerId.create("XX", "foo", "bar", "server"),
                "foo"
            )
        );
    }
}
