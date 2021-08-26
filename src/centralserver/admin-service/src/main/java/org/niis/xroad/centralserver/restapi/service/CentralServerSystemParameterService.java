package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.restapi.config.HAConfigStatus;
import org.niis.xroad.centralserver.restapi.entity.SystemParameter;
import org.niis.xroad.centralserver.restapi.repository.SystemParameterRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.crypto.dsig.DigestMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor

/*
 *    Class for handling SystemParameter taking HA-setup into account
 *
 */
public class CentralServerSystemParameterService {

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
        List<SystemParameter> valueInDb;
        if (currentHaConfigStatus.isHaConfigured()
                && isNodeLocalParameter(key)
        ) {
            valueInDb = systemParameterRepository.findSystemParameterByKeyAndHaNodeName(
                    key,
                    currentHaConfigStatus.getCurrentHaNodeName()
            );
        } else {
            valueInDb = systemParameterRepository.findSystemParametersByKey(key);
        }
        if (!valueInDb.isEmpty()) {
            return valueInDb.iterator().next().getValue();
        }
        return defaultValue;
    }

    public SystemParameter updateOrCreateParameter(String lookupKey, String updateValue) {
        Optional<SystemParameter> systemParameter;
        if (currentHaConfigStatus.isHaConfigured() && isNodeLocalParameter(lookupKey)) {
            String haNodeName = currentHaConfigStatus.getCurrentHaNodeName();
            systemParameter = systemParameterRepository.findSystemParameterByKeyAndHaNodeName(lookupKey, haNodeName)
                    .stream().findFirst();

        } else {
            systemParameter = systemParameterRepository.findSystemParametersByKey(lookupKey)
                    .stream().findFirst();
        }
        if (systemParameter.isEmpty()) {
            SystemParameter newSystemParameter = new SystemParameter();
            newSystemParameter.setKey(lookupKey);
            // haNodeName will be inserted using database trigger.
            systemParameter = Optional.of(newSystemParameter);
        }
        systemParameter.get().setValue(updateValue);
        return systemParameterRepository.save(systemParameter.get());
    }
    private boolean isNodeLocalParameter(String key) {
        return Arrays.asList(NODE_LOCAL_PARAMETERS).contains(key);
    }
}
