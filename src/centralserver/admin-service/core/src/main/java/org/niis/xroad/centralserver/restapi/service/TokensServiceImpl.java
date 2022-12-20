/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.CodedException;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.dto.converter.TokenInfoMapper;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.centralserver.restapi.service.exception.SignerProxyException;
import org.niis.xroad.centralserver.restapi.service.exception.TokenPinFinalTryException;
import org.niis.xroad.centralserver.restapi.service.exception.TokenPinLockedException;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.TokenInfo;
import org.niis.xroad.cs.admin.api.dto.TokenLoginRequest;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.TokensService;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.USER_PIN_FINAL_TRY;
import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.USER_PIN_LOCKED;
import static java.lang.Integer.parseInt;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.ERROR_GETTING_TOKENS;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.SIGNER_PROXY_ERROR;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_ACTIVATION_FAILED;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_DEACTIVATION_FAILED;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_INCORRECT_PIN_FORMAT;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_NOT_FOUND;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_PIN_FINAL_TRY;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_PIN_LOCKED;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.LOGIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.LOGOUT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TOKEN_FRIENDLY_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TOKEN_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TOKEN_SERIAL_NUMBER;

@Service
@Transactional
@RequiredArgsConstructor
public class TokensServiceImpl implements TokensService {

    private static final String TOKEN_NOT_FOUND_FAULT_CODE = "Signer.TokenNotFound";
    private static final String KEY_MIN_PIN_LENGTH = "Min PIN length";
    private static final String KEY_MAX_PIN_LENGTH = "Max PIN length";

    private final AuditDataHelper auditDataHelper;
    private final SignerProxyFacade signerProxyFacade;
    private final TokenActionsResolver tokenActionsResolver;

    private final TokenInfoMapper tokenInfoMapper;

    @Override
    public Set<TokenInfo> getTokens() {
        try {
            return signerProxyFacade.getTokens().stream()
                    .map(tokenInfoMapper::toTarget)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new SignerProxyException(ERROR_GETTING_TOKENS, e);
        }
    }

    @Override
    public TokenInfo login(TokenLoginRequest tokenLoginRequest) {
        final ee.ria.xroad.signer.protocol.dto.TokenInfo token = getToken(tokenLoginRequest.getTokenId());
        addAuditData(token);

        if (USER_PIN_LOCKED == token.getStatus()) {
            throw new TokenPinLockedException(TOKEN_PIN_LOCKED);
        }

        tokenActionsResolver.requireAction(LOGIN, token);
        validatePinMeetsTheTokenRequirements(token, tokenLoginRequest.getPassword());

        try {
            signerProxyFacade.activateToken(tokenLoginRequest.getTokenId(), tokenLoginRequest.getPassword().toCharArray());
        } catch (CodedException codedException) {
            final ee.ria.xroad.signer.protocol.dto.TokenInfo token1 = getToken(tokenLoginRequest.getTokenId());
            if (USER_PIN_FINAL_TRY == token1.getStatus()) {
                throw new TokenPinFinalTryException(TOKEN_PIN_FINAL_TRY);
            } else if (USER_PIN_LOCKED == token1.getStatus()) {
                throw new TokenPinLockedException(TOKEN_PIN_LOCKED);
            }
            throw new SignerProxyException(TOKEN_ACTIVATION_FAILED, codedException, codedException.getFaultCode());
        } catch (Exception exception) {
            throw new SignerProxyException(TOKEN_ACTIVATION_FAILED, exception);
        }

        return tokenInfoMapper.toTarget(getToken(tokenLoginRequest.getTokenId()));
    }

    @Override
    public TokenInfo logout(String tokenId) {
        final ee.ria.xroad.signer.protocol.dto.TokenInfo token = getToken(tokenId);
        addAuditData(token);
        tokenActionsResolver.requireAction(LOGOUT, token);
        try {
            signerProxyFacade.deactivateToken(tokenId);
        } catch (CodedException codedException) {
            throw new SignerProxyException(TOKEN_DEACTIVATION_FAILED, codedException, codedException.getFaultCode());
        } catch (Exception exception) {
            throw new SignerProxyException(TOKEN_DEACTIVATION_FAILED, exception);
        }

        return tokenInfoMapper.toTarget(getToken(tokenId));
    }

    private void addAuditData(ee.ria.xroad.signer.protocol.dto.TokenInfo token) {
        auditDataHelper.put(TOKEN_ID, token.getId());
        auditDataHelper.put(TOKEN_SERIAL_NUMBER, token.getSerialNumber());
        auditDataHelper.put(TOKEN_FRIENDLY_NAME, token.getFriendlyName());
    }

    private void validatePinMeetsTheTokenRequirements(ee.ria.xroad.signer.protocol.dto.TokenInfo token, String password) {
        for (final Map.Entry<String, String> entry : token.getTokenInfo().entrySet()) {
            if (KEY_MIN_PIN_LENGTH.equals(entry.getKey())
                    && isInt(entry.getValue()) && password.length() < parseInt(entry.getValue())) {
                throw new ValidationFailureException(TOKEN_INCORRECT_PIN_FORMAT);
            } else if (KEY_MAX_PIN_LENGTH.equals(entry.getKey())
                    && isInt(entry.getValue()) && password.length() > parseInt(entry.getValue())) {
                throw new ValidationFailureException(TOKEN_INCORRECT_PIN_FORMAT);
            }
        }
    }

    @Override
    public ee.ria.xroad.signer.protocol.dto.TokenInfo getToken(String tokenId) {
        try {
            return signerProxyFacade.getToken(tokenId);
        } catch (CodedException codedException) {
            if (causedByNotFound(codedException)) {
                throw new NotFoundException(TOKEN_NOT_FOUND);
            }
            throw new SignerProxyException(SIGNER_PROXY_ERROR, codedException, codedException.getFaultCode());
        } catch (Exception exception) {
            throw new SignerProxyException(SIGNER_PROXY_ERROR, exception);
        }
    }

    private boolean causedByNotFound(CodedException codedException) {
        return TOKEN_NOT_FOUND_FAULT_CODE.equals(codedException.getFaultCode());
    }

    private boolean isInt(String value) {
        try {
            parseInt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
