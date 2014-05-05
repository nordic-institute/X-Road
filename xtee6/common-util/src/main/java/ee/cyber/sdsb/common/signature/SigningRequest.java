package ee.cyber.sdsb.common.signature;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import lombok.ToString;
import lombok.Value;

import org.bouncycastle.cert.ocsp.OCSPResp;

@Value
@ToString(exclude = { "signingCert", "extraCertificates", "ocspResponses" })
public final class SigningRequest implements Serializable {

    private final X509Certificate signingCert;

    private final List<PartHash> parts;

    private final List<X509Certificate> extraCertificates = new ArrayList<>();
    private final List<OCSPResp> ocspResponses = new ArrayList<>();

    public boolean isSingleMessage() {
        return parts.size() == 1;
    }
}
