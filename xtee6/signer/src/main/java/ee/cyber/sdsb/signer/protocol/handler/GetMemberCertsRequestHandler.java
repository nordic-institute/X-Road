package ee.cyber.sdsb.signer.protocol.handler;

import java.util.ArrayList;
import java.util.List;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.GetMemberCerts;
import ee.cyber.sdsb.signer.protocol.message.GetMemberCertsResponse;

public class GetMemberCertsRequestHandler
        extends AbstractRequestHandler<GetMemberCerts> {

    @Override
    protected Object handle(GetMemberCerts message) throws Exception {
        List<CertificateInfo> memberCerts = new ArrayList<>();

        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION) {
                    continue;
                }

                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (containsMember(certInfo.getMemberId(),
                            message.getMemberId())) {
                        memberCerts.add(certInfo);
                    }
                }
            }
        }

        return new GetMemberCertsResponse(memberCerts);
    }

    private static boolean containsMember(ClientId first, ClientId second) {
        if (first == null || second == null) {
            return false;
        }

        return first.equals(second) || second.subsystemContainsMember(first);
    }

}
