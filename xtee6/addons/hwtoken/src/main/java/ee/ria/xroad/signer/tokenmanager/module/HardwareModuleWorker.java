/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.tokenmanager.module;

import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Slot;
import iaik.pkcs.pkcs11.TokenInfo;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import akka.actor.Props;

import ee.ria.xroad.signer.tokenmanager.token.HardwareToken;
import ee.ria.xroad.signer.tokenmanager.token.HardwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;

import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.moduleGetInstance;

/**
 * Module worker for hardware tokens.
 */
@Slf4j
@RequiredArgsConstructor
public class HardwareModuleWorker extends AbstractModuleWorker {

    private final HardwareModuleType module;

    private Module pkcs11Module;

    @Override
    protected void initializeModule() throws Exception {
        if (pkcs11Module != null) {
            return;
        }

        log.trace("Initializing module {} ({})", module.getType(),
                module.getPkcs11LibraryPath());
        try {
            pkcs11Module = moduleGetInstance(module.getPkcs11LibraryPath());
            pkcs11Module.initialize(null);
        } catch (Throwable t) {
            // Note that we catch all serious errors here since we do not
            // want Signer to crash if the module could not be loaded for
            // some reason.
            throw new RuntimeException(t);
        }
    }

    @Override
    protected void deinitializeModule() throws Exception {
        if (pkcs11Module == null) {
            return;
        }

        log.trace("Deinitializing module {} ({})", module.getType(),
                module.getPkcs11LibraryPath());

        pkcs11Module.finalize(null);
    }

    @Override
    protected List<TokenType> listTokens() throws Exception {
        List<TokenType> tokens = new ArrayList<>();

        Slot[] slots = pkcs11Module.getSlotList(
                Module.SlotRequirement.TOKEN_PRESENT);
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            Slot slot = slots[slotIndex];

            iaik.pkcs.pkcs11.Token token = slot.getToken();
            TokenInfo tokenInfo = token.getTokenInfo();

            String serialNumber = tokenInfo.getSerialNumber().trim();
            // PKCS#11 gives us only 32 bytes.
            String label = tokenInfo.getLabel().trim();

            boolean readOnly = module.isForceReadOnly()
                    || tokenInfo.isWriteProtected();

            TokenType t = new HardwareTokenType(module.getType(), token,
                    readOnly, slotIndex, serialNumber, label,
                    module.isPinVerificationPerSigning(),
                    module.isBatchSingingEnabled());

            log.trace("Module '{}' slot #{} has token: {}",
                    new Object[] {module.getPkcs11LibraryPath(), slotIndex,
                    t });
            tokens.add(t);
        }

        return tokens;
    }

    @Override
    protected Props props(ee.ria.xroad.signer.protocol.dto.TokenInfo tokenInfo,
            TokenType tokenType) {
        return Props.create(HardwareToken.class, tokenInfo, tokenType);
    }
}
