package ee.cyber.sdsb.signer.tokenmanager.token;

import akka.actor.Props;

import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

/**
 * Hardware token.
 */
public class HardwareToken extends AbstractToken {

    private static final String DISPATCHER = "token-worker-dispatcher";

    private final HardwareTokenType tokenType;

    /**
     * @param tokenInfo the token info
     * @param tokenType the token type
     */
    public HardwareToken(TokenInfo tokenInfo, HardwareTokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
    }

    @Override
    protected Props createSigner() {
        return Props.create(TokenSigner.class);
    }

    @Override
    protected Props createWorker() {
        return Props.create(HardwareTokenWorker.class,
                tokenInfo, tokenType).withDispatcher(DISPATCHER);
    }

}
