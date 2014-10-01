package ee.cyber.sdsb.common.signature;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimestampUtil {

    @SuppressWarnings("unchecked")
    public static TimeStampToken addSignerCertificate(
            TimeStampResponse tsResponse, X509Certificate signerCertificate)
                    throws Exception {
        CMSSignedData cms = tsResponse.getTimeStampToken().toCMSSignedData();

        List<X509Certificate> collection = Arrays.asList(signerCertificate);
        collection.addAll(cms.getCertificates().getMatches(null));

        return new TimeStampToken(CMSSignedData.replaceCertificatesAndCRLs(cms,
                new JcaCertStore(collection), cms.getAttributeCertificates(),
                cms.getCRLs()));
    }

}
