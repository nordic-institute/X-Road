/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.signer.tokenmanager.token.AbstractTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.HardwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.HardwareTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;

import iaik.pkcs.pkcs11.DefaultInitializeArgs;
import iaik.pkcs.pkcs11.InitializeArgs;
import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Slot;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.Functions;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.moduleGetInstance;

/**
 * Module worker for hardware tokens.
 */
@Slf4j
public class HardwareModuleWorker extends AbstractModuleWorker {
    private final HardwareModuleType module;

    private Module pkcs11Module;

    public HardwareModuleWorker(HardwareModuleType moduleType) {
        super(moduleType);
        this.module = moduleType;
    }

    @Override
    public void start() {
        if (pkcs11Module != null) {
            return;
        }

        log.info("Initializing module '{}' (library: {})", module.getType(), module.getPkcs11LibraryPath());

        try {
            pkcs11Module = moduleGetInstance(module.getPkcs11LibraryPath());

            pkcs11Module.initialize(getInitializeArgs(module.getLibraryCantCreateOsThreads(), module.getOsLockingOk()));
        } catch (Throwable t) {
            // Note that we catch all serious errors here since we do not want Signer to crash if the module could
            // not be loaded for some reason.
            throw new RuntimeException(t);
        }
    }

    private static InitializeArgs getInitializeArgs(Boolean libraryCantCreateOsThreads, Boolean osLockingOk) {
        if (libraryCantCreateOsThreads == null && osLockingOk == null) {
            return null;
        }

        return new DefaultInitializeArgs(null,
                libraryCantCreateOsThreads == null ? false : libraryCantCreateOsThreads,
                osLockingOk == null ? false : osLockingOk);
    }

    @Override
    public void stop() {
        super.stop();
        if (pkcs11Module == null) {
            return;
        }

        log.info("Stopping module '{}' (library: {})", module.getType(), module.getPkcs11LibraryPath());

        try {
            pkcs11Module.finalize(null);
        } catch (TokenException e) {
            throw translateException(e);
        } finally {
            pkcs11Module = null;
        }
    }

    @Override
    public void reload() {
        log.info("Reloading {}", module);
        try {
            stop();
        } catch (Exception e) {
            log.warn("Failed to stop module {}.", module.getType());
        }
        start();

        super.reload();
    }

    @Override
    protected List<TokenType> listTokens() throws Exception {
        log.trace("Listing tokens on module '{}'", module.getType());

        if (pkcs11Module == null) {
            log.warn("Module {} not initialized before listTokens(). Reinitializing module.", module.getType());
            throw new PKCS11Exception(PKCS11Constants.CKR_CRYPTOKI_NOT_INITIALIZED);
        }

        Slot[] slots = pkcs11Module.getSlotList(Module.SlotRequirement.TOKEN_PRESENT);

        if (slots.length == 0) {
            log.warn("Did not get any slots from module '{}'. Reinitializing module.", module.getType());

            // Error code doesn't really matter as long as it's PKCS11Exception
            throw new PKCS11Exception(PKCS11Constants.CKR_GENERAL_ERROR);
        }

        log.info("Module '{}' got {} slots", module.getType(), slots.length);
        for (int i = 0; i < slots.length; i++) {
            log.debug("Module '{}' Slot {} ID: {} (0x{})", module.getType(), i, slots[i].getSlotID(),
                    Functions.toHexString(slots[i].getSlotID()));
        }

        // HSM slot ids defined in module data
        Set<Long> slotIds = module.getSlotIds();
        log.debug("Slot configuration for module '{}' defined as {}", module.getType(), slotIds.toString());

        Map<String, TokenType> tokens = new HashMap<>();
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slotIds.isEmpty() || slotIds.contains(slots[slotIndex].getSlotID())) {
                TokenType token = createToken(slots, slotIndex);
                TokenType previous = tokens.putIfAbsent(token.getId(), token);
                if (previous == null) {
                    log.info("Module '{}' slot #{} has token with ID '{}': {}", module.getType(), slotIndex,
                            token.getId(), token);
                } else {
                    log.info("Module '{}' slot #{} has token with ID '{}' but token with that ID is "
                            + " already registered", module.getType(), slotIndex, token.getId());
                }
            }
        }
        return new ArrayList<>(tokens.values());
    }

    private TokenType createToken(Slot[] slots, int slotIndex) throws Exception {
        Slot slot = slots[slotIndex];

        iaik.pkcs.pkcs11.Token pkcs11Token = slot.getToken();
        iaik.pkcs.pkcs11.TokenInfo tokenInfo = pkcs11Token.getTokenInfo();

        return new HardwareTokenType(
                module.getType(),
                module.getTokenIdFormat(),
                pkcs11Token,
                module.isForceReadOnly() || tokenInfo.isWriteProtected(),
                slotIndex,
                tokenInfo.getSerialNumber().trim(),
                tokenInfo.getLabel().trim(), // PKCS11 gives us only 32 bytes.
                module.isPinVerificationPerSigning(),
                module.isBatchSigningEnabled(),
                module.getSignMechanismName(),
                module.getPrivKeyAttributes(),
                module.getPubKeyAttributes()
        );
    }

    @Override
    protected AbstractTokenWorker createWorker(ee.ria.xroad.signer.protocol.dto.TokenInfo tokenInfo, TokenType tokenType) {
        return new HardwareTokenWorker(tokenInfo, tokenType);
    }
}
