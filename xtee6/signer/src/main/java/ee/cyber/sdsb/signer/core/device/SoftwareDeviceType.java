package ee.cyber.sdsb.signer.core.device;

import java.util.Arrays;
import java.util.List;

public class SoftwareDeviceType implements DeviceType {

    public static final String TYPE = "softToken";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public List<TokenType> listTokens() throws Exception {
        return Arrays.asList((TokenType) new SoftwareTokenType());
    }

}
