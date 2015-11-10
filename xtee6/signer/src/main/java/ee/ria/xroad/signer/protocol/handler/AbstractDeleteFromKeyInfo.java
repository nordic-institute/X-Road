package ee.ria.xroad.signer.protocol.handler;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.DeleteCert;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;

@Slf4j
abstract class AbstractDeleteFromKeyInfo<T> extends AbstractRequestHandler<T> {

    protected void deleteCertOnToken(DeleteCert deleteCert) {
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (deleteCert.getCertId().equals(certInfo.getId())) {
                        tellToken(deleteCert, tokenInfo.getId());
                        return;
                    }
                }
            }
        }
    }

    protected void deleteKeyFile(String tokenId, DeleteKey message) {
        tellToken(message, tokenId);
    }

    protected Object deleteCertRequest(String certId) throws Exception {
        String keyId = TokenManager.removeCertRequest(certId);
        if (keyId != null) {
            log.info("Deleted certificate request under key '{}'", keyId);
            return success();
        }

        throw CodedException.tr(X_CSR_NOT_FOUND,
                "csr_not_found", "Certificate request '%s' not found", certId);
    }
}
