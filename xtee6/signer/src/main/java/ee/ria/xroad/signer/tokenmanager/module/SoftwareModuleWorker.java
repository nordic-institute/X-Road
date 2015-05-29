package ee.ria.xroad.signer.tokenmanager.module;

import java.util.Collections;
import java.util.List;

import akka.actor.Props;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareToken;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;

/**
 * Worker for software module. Always lists only one software token.
 */
public class SoftwareModuleWorker extends AbstractModuleWorker {

    private static final List<TokenType> TOKENS =
            Collections.singletonList((TokenType) new SoftwareTokenType());

    @Override
    protected void initializeModule() throws Exception {
        // nothing to do
    }

    @Override
    protected void deinitializeModule() throws Exception {
        // nothing to do
    }

    @Override
    protected List<TokenType> listTokens() throws Exception {
        return TOKENS;
    }

    @Override
    protected Props props(TokenInfo tokenInfo, TokenType tokenType) {
        return Props.create(SoftwareToken.class, tokenInfo, tokenType);
    }

}
