package ee.cyber.sdsb.signer.core.token;

import akka.actor.Props;

import ee.cyber.sdsb.signer.core.device.SscdTokenType;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

public class SscdToken extends AbstractToken {

    private static final String DISPATCHER = "token-worker-dispatcher";

    private final SscdTokenType tokenType;

    public SscdToken(TokenInfo tokenInfo, SscdTokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
    }

    @Override
    protected Props createSigner() {
        return Props.create(TokenSigner.class);
    }

    @Override
    protected Props createWorker() {
        return Props.create(SscdTokenWorker.class,
                tokenInfo, tokenType).withDispatcher(DISPATCHER);
    }

}
