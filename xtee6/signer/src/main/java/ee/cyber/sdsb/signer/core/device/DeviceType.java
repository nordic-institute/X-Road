package ee.cyber.sdsb.signer.core.device;

import java.util.List;


public interface DeviceType {

    String getType();

    List<TokenType> listTokens() throws Exception;

}
