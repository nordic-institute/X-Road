/**
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

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.restapi.config.HAConfigStatus;
import org.niis.xroad.centralserver.restapi.entity.SystemParameter;
import org.niis.xroad.centralserver.restapi.repository.SystemParameterRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import javax.xml.crypto.dsig.DigestMethod;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor

/**
 *    Class for handling SystemParameter taking HA-setup into account
 */
public class SystemParameterService {

    public static final String INSTANCE_IDENTIFIER = "instanceIdentifier";
    public static final String CENTRAL_SERVER_ADDRESS = "centralServerAddress";
    public static final String AUTH_CERT_REG_URL = "authCertRegUrl";
    public static final String DEFAULT_AUTH_CERT_REG_URL = "https://%{centralServerAddress}:4001/managementservice/";
    public static final String CONF_HASH_ALGO_URI = "confHashAlgoUri";
    public static final String DEFAULT_CONF_HASH_ALGO_URI = DigestMethod.SHA512;
    public static final String CONF_SIGN_CERT_HASH_ALGO_URI = "confSignCertHashAlgoUri";
    public static final String DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI = DigestMethod.SHA512;
    public static final String SECURITY_SERVER_OWNERS_GROUP = "securityServerOwnersGroup";
    public static final String DEFAULT_SECURITY_SERVER_OWNERS_GROUP = "security-server-owners";
    public static final String DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC = "Security server owners";
    public static final String CONF_SIGN_DIGEST_ALGO_ID = "confSignDigestAlgoId";
    public static final String DEFAULT_CONF_SIGN_DIGEST_ALGO_ID = CryptoUtils.SHA512_ID;
    public static final String OCSP_FRESHNESS_SECONDS = "ocspFreshnessSeconds";
    public static final Integer DEFAULT_OCSP_FRESHNESS_SECONDS = 3600;
    public static final String TIME_STAMPING_INTERVAL_SECONDS = "timeStampingIntervalSeconds";
    public static final Integer DEFAULT_TIME_STAMPING_INTERVAL_SECONDS = 60;
    public static final String CONF_EXPIRE_INTERVAL_SECONDS = "confExpireIntervalSeconds";
    public static final Integer DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS = 600;
    private static final String[] NODE_LOCAL_PARAMETERS = {CENTRAL_SERVER_ADDRESS};

    private final SystemParameterRepository systemParameterRepository;
    private final HAConfigStatus currentHaConfigStatus;

    public String getParameterValue(String key, String defaultValue) {
        log.debug("getParameterValue() - getting value for key:{} with default value:{}", key, defaultValue);
        Optional<SystemParameter> valueInDb = getSystemParameterOptional(key);
        return valueInDb.map(SystemParameter::getValue).orElse(defaultValue);
    }

    public SystemParameter updateOrCreateParameter(String lookupKey, String updateValue) {
        Optional<SystemParameter> systemParameter =
                getSystemParameterOptional(lookupKey);

        if (systemParameter.isEmpty()) {
            SystemParameter newSystemParameter = new SystemParameter();
            newSystemParameter.setKey(lookupKey);
            // now initial value for non-postgresql testing,
            // the real haNodeName will be inserted using Postgresql database trigger.
            newSystemParameter.setHaNodeName(currentHaConfigStatus.getCurrentHaNodeName());
            systemParameter = Optional.of(newSystemParameter);
        }
        SystemParameter systemParameterToStore = systemParameter.get();
        systemParameterToStore.setValue(updateValue);
        log.debug("updateOrCreateParameter(): storing Systemparameter of key:{} with value:{} for node:{}",
                lookupKey, updateValue, systemParameterToStore.getHaNodeName()
        );
        return systemParameterRepository.save(systemParameterToStore);
    }

    private Optional<SystemParameter> getSystemParameterOptional(String lookupKey) {
        Optional<SystemParameter> systemParameter;
        if (currentHaConfigStatus.isHaConfigured() && isNodeLocalParameter(lookupKey)) {
            String haNodeName = currentHaConfigStatus.getCurrentHaNodeName();
            systemParameter = systemParameterRepository.findByKeyAndHaNodeName(lookupKey, haNodeName)
                    .stream().findFirst();
        } else {
            systemParameter = systemParameterRepository.findByKey(lookupKey)
                    .stream().findFirst();
        }
        return systemParameter;
    }

    private boolean isNodeLocalParameter(String key) {
        return Arrays.asList(NODE_LOCAL_PARAMETERS).contains(key);
    }
}
