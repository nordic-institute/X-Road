package ee.cyber.sdsb.signer.core.device;

import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Slot;
import iaik.pkcs.pkcs11.TokenInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ee.cyber.sdsb.signer.core.token.SscdTokenUtil.moduleGetInstance;


@Data
public class SscdDeviceType implements DeviceType {

    private static final Logger LOG =
            LoggerFactory.getLogger(SscdDeviceType.class);

    private final String type;

    private final String pkcs11LibraryPath;

    private final boolean pinVerificationPerSigning;

    private final boolean batchSingingEnabled;

    private static final Map<String, Module> initializedModules =
            new HashMap<>();

    @Override
    public List<TokenType> listTokens() throws Exception {
        Module module = getModule();

        List<TokenType> tokens = new ArrayList<>();
        Slot[] slots = module.getSlotList(Module.SlotRequirement.TOKEN_PRESENT);
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            Slot slot = slots[slotIndex];

            iaik.pkcs.pkcs11.Token token = slot.getToken();
            TokenInfo tokenInfo = token.getTokenInfo();

            String serialNumber = tokenInfo.getSerialNumber().trim();
            // PKCS#11 gives us only 32 bytes.
            String label = tokenInfo.getLabel().trim();

            boolean readOnly = tokenInfo.isWriteProtected();

            tokens.add(new SscdTokenType(type, token, readOnly, slotIndex,
                    serialNumber, label, pinVerificationPerSigning,
                    batchSingingEnabled));
        }

        return tokens;
    }

    private Module getModule() throws Exception {
        if (initializedModules.containsKey(pkcs11LibraryPath)) {
            return initializedModules.get(pkcs11LibraryPath);
        } else {
            LOG.info("Initializing module {}...", pkcs11LibraryPath);

            Module module = null;
            try {
                module = moduleGetInstance(pkcs11LibraryPath);
                module.initialize(null);
            } catch (Throwable t) {
                // Note that we catch all serious errors here since we do not
                // want Signer to crash if the module could not be loaded for
                // some reason.
                throw new RuntimeException("Error initializing module "
                        + pkcs11LibraryPath, t);
            }

            initializedModules.put(pkcs11LibraryPath, module);
            return module;
        }
    }
}
