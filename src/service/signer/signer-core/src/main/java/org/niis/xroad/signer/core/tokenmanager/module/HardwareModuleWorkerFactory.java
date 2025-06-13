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
package org.niis.xroad.signer.core.tokenmanager.module;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import iaik.pkcs.pkcs11.DefaultInitializeArgs;
import iaik.pkcs.pkcs11.InitializeArgs;
import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Slot;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.Functions;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.AbstractTokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenDefinition;
import org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil;
import org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenWorkerFactory;
import org.niis.xroad.signer.core.tokenmanager.token.TokenDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Module worker for hardware tokens.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HardwareModuleWorkerFactory {
    private final SignerProperties signerProperties;
    private final HardwareTokenWorkerFactory tokenWorkerFactory;
    private final TokenManager tokenManager;
    private final TokenLookup tokenLookup;

    public HardwareModuleWorker create(HardwareModuleType moduleType) {
        return new HardwareModuleWorker(moduleType);
    }

    public class HardwareModuleWorker extends AbstractModuleWorker {
        private final HardwareModuleType module;

        private Module pkcs11Module;

        public HardwareModuleWorker(HardwareModuleType moduleType) {
            super(moduleType,
                    HardwareModuleWorkerFactory.this.tokenManager,
                    HardwareModuleWorkerFactory.this.tokenLookup);
            this.module = moduleType;
        }

        @Override
        public void start() {
            if (pkcs11Module != null) {
                return;
            }

            log.info("Initializing module '{}' (library: {})", module.getType(), module.getPkcs11LibraryPath());

            try {
                pkcs11Module = HardwareTokenUtil.moduleGetInstance(module.getPkcs11LibraryPath(),
                        signerProperties.moduleInstanceProvider().orElse(null));

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
                    libraryCantCreateOsThreads != null && libraryCantCreateOsThreads,
                    osLockingOk != null && osLockingOk);
        }

        @Override
        public void destroy() {
            super.destroy();
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
                destroy();
            } catch (Exception e) {
                log.warn("Failed to stop module {}.", module.getType());
            }
            start();

            super.reload();
        }

        @Override
        protected List<TokenDefinition> listTokens() throws Exception {
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

            Map<String, TokenDefinition> tokens = new HashMap<>();
            for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
                if (slotIds.isEmpty() || slotIds.contains(slots[slotIndex].getSlotID())) {
                    TokenDefinition token = createToken(slots, slotIndex);
                    TokenDefinition previous = tokens.putIfAbsent(token.getId(), token);
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

        private TokenDefinition createToken(Slot[] slots, int slotIndex) throws Exception {
            Slot slot = slots[slotIndex];

            iaik.pkcs.pkcs11.Token pkcs11Token = slot.getToken();
            iaik.pkcs.pkcs11.TokenInfo tokenInfo = pkcs11Token.getTokenInfo();

            return new HardwareTokenDefinition(
                    module.getType(),
                    module.getTokenIdFormat(),
                    pkcs11Token,
                    module.isForceReadOnly() || tokenInfo.isWriteProtected(),
                    slotIndex,
                    tokenInfo.getSerialNumber().trim(),
                    tokenInfo.getLabel().trim(), // PKCS11 gives us only 32 bytes.
                    module.isPinVerificationPerSigning(),
                    module.isBatchSigningEnabled(),
                    Map.of(
                            KeyAlgorithm.RSA, module.getRsaSignMechanismName(),
                            KeyAlgorithm.EC, module.getEcSignMechanismName()
                    ),
                    module.getPrivKeyAttributes(),
                    module.getPubKeyAttributes()
            );
        }

        @Override
        protected AbstractTokenWorker createWorker(TokenInfo tokenInfo, TokenDefinition tokenDefinition) {
            return tokenWorkerFactory.create(tokenInfo, tokenDefinition);
        }
    }
}
