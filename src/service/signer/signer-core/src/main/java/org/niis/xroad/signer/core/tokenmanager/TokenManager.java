/*
 * The MIT License
 *
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
package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.CodedException;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.service.TokenWriteService;
import org.niis.xroad.signer.core.tokenmanager.token.TokenDefinition;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static org.niis.xroad.signer.core.util.SignerUtil.getDefaultFriendlyName;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenManager {
    private final TokenRegistry tokenRegistry;
    private final TokenWriteService tokenWriteService;
    private final TokenLookup tokenLookup;

    /**
     * Creates a new token with specified type.
     *
     * @param tokenDefinition the type
     * @return the new token
     */
    public TokenInfo createToken(TokenDefinition tokenDefinition) {
        tokenRegistry.writeRun(ctx -> {
            try {
                tokenWriteService.save(
                        tokenDefinition.getId(),
                        tokenDefinition.moduleType(),
                        getDefaultFriendlyName(tokenDefinition),
                        tokenDefinition.label(),
                        tokenDefinition.serialNumber());

            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR,
                        "Failed to create token " + tokenDefinition.getId(), e);
            } finally {
                ctx.invalidateCache();
            }
        });
        return tokenLookup.getTokenInfo(tokenDefinition.getId());
    }

    /**
     * Sets the token available.
     *
     * @param tokenDefinition the token type
     */
    public void enableToken(TokenDefinition tokenDefinition) {
        String tokenId = tokenDefinition.getId();

        log.trace("enableToken({})", tokenId);
        tokenRegistry.writeRun(ctx -> {
            RuntimeTokenImpl token = ctx.findToken(tokenId);
            token.setTokenDefinition(tokenDefinition);
            if (token.getStatus() == TokenStatusInfo.NOT_INITIALIZED) {
                token.setStatus(TokenStatusInfo.OK);
            }
        });
    }

    public void disableToken(String tokenId) {
        log.trace("disableToken({})", tokenId);

        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setTokenDefinition(null));
    }

    /**
     * Sets the token active (logged in) or not
     *
     * @param tokenId the token id
     * @param active  active flag
     */
    public void setTokenActive(String tokenId,
                               boolean active) {
        log.trace("setTokenActive({}, {})", tokenId, active);

        tokenRegistry.writeRun(ctx -> ctx.findToken(tokenId).setActive(active));
    }


    /**
     * Sets the token friendly name.
     *
     * @param tokenId      token id
     * @param friendlyName the friendly name
     */
    public void setTokenFriendlyName(String tokenId,
                                     String friendlyName) {
        log.trace("setTokenFriendlyName({}, {})", tokenId, friendlyName);

        tokenRegistry.writeRun(ctx -> {
            try {
                var token = ctx.findToken(tokenId);
                tokenWriteService.updateFriendlyName(token.id(), friendlyName);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException("Failed to update friendly name for token " + tokenId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Sets the token status info
     *
     * @param tokenId the token id
     * @param status  the status
     */
    public void setTokenStatus(String tokenId,
                               TokenStatusInfo status) {
        log.trace("setTokenStatus({}, {})", tokenId, status);

        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setStatus(status));
    }

    /**
     * Delete token.
     *
     * @param tokenId the token id
     */
    public void deleteToken(String tokenId) {
        log.trace("deleteToken({})", tokenId);

        tokenRegistry.writeRun(ctx -> {
            try {
                var token = ctx.findToken(tokenId);
                tokenWriteService.delete(token.id());
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to delete token " + tokenId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Sets the token info for the token.
     *
     * @param tokenId the token id
     * @param info    the token info
     */
    public void setTokenInfo(String tokenId, Map<String, String> info) {
        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setInfo(info));
    }

}
