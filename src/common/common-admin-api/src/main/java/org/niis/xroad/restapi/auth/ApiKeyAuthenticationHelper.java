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
package org.niis.xroad.restapi.auth;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.repository.ApiKeyRepository;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AuthenticationHelper class.
 * This class does not require authentication.
 */
@Slf4j
@Service
@Transactional
public class ApiKeyAuthenticationHelper {
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyRepository apiKeyRepository;

    @Autowired
    public ApiKeyAuthenticationHelper(PasswordEncoder passwordEncoder, ApiKeyRepository apiKeyRepository) {
        this.passwordEncoder = passwordEncoder;
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * get matching key
     * @param key plaintext key
     * @return
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType getForPlaintextKey(String key) throws ApiKeyService.ApiKeyNotFoundException {
        String encodedKey = passwordEncoder.encode(key);
        List<PersistentApiKeyType> keys = apiKeyRepository.getAllApiKeys();
        for (PersistentApiKeyType apiKeyType : keys) {
            if (apiKeyType.getEncodedKey().equals(encodedKey)) {
                return apiKeyType;
            }
        }
        throw new ApiKeyService.ApiKeyNotFoundException();
    }
}
