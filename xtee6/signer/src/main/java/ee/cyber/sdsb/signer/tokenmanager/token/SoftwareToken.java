package ee.cyber.sdsb.signer.tokenmanager.token;

import java.util.HashMap;
import java.util.Map;

import akka.actor.Props;

import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

public class SoftwareToken extends AbstractToken {

    private static final String DISPATCHER = "token-worker-dispatcher";

    private final SoftwareTokenType tokenType;

    public SoftwareToken(TokenInfo tokenInfo, SoftwareTokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        initTokenInfo(tokenInfo);
    }

    @Override
    protected Props createWorker() {
        return Props.create(SoftwareTokenWorker.class,
                tokenInfo, tokenType).withDispatcher(DISPATCHER);
    }

    @Override
    protected Props createSigner() {
        return Props.create(TokenSigner.class);
    }

    private void initTokenInfo(TokenInfo tokenInfo) {
        Map<String, String> info = new HashMap<>();
        info.put("Type", "Software");

        TokenManager.setTokenInfo(tokenInfo.getId(), info);
    }

}
