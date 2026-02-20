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
package org.niis.xroad.ss.test.ui.container.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestTokenService {
    private final TestDatabaseService testDatabaseService;

    public void addInactiveSignerToken() {
        var sql = """
                INSERT INTO signer_tokens (external_id, type, friendly_name)
                VALUES ('1234', 'hardwareToken', 'hsmToken-for-deletion')
                """;

        testDatabaseService.getServerconfTemplate().update(sql, Map.of());
    }

    public int deleteHsmTokens() {
        var sql = """
                DELETE FROM signer_tokens
                WHERE type != 'softToken'
                """;

        return testDatabaseService.getServerconfTemplate().update(sql, Map.of());
    }

    public int deleteAllKeys() {
        var sql = """
                DELETE FROM signer_keys
                """;

        return testDatabaseService.getServerconfTemplate().update(sql, Map.of());
    }

    public void createSoftTokenDevice(String id, String friendlyName) {
        var sql = """
                INSERT INTO signer_tokens (external_id, type, friendly_name)
                VALUES (:id, 'softToken', :friendlyName)
                """;

        testDatabaseService.getServerconfTemplate().update(sql, Map.of(
                "id", id,
                "friendlyName", friendlyName));
    }

    public void addSoftwareKey(String externalId, String friendlyName, String usage, byte[] keystore) {
        var sql = """
                INSERT INTO signer_keys (external_id, token_id, type, public_key, keystore, sign_mechanism_name,
                                       friendly_name, label, usage)
                VALUES (:externalId, (SELECT id FROM signer_tokens WHERE external_id = '0'),
                        :type, :publicKey, :keystore, 'CKM_RSA_PKCS',
                        :friendlyName, :label, :usage)
                """;

        testDatabaseService.getServerconfTemplate().update(sql, Map.of(
                "externalId", externalId,
                "publicKey", getPublicKey(externalId),
                "keystore", keystore,
                "friendlyName", friendlyName,
                "label", friendlyName,
                "type", "SOFTWARE",
                "usage", usage));
    }

    public void addCertificate(String certExternalId, String keyExternalId, byte[] certData, String memberId) {
        var sql = """
                INSERT INTO signer_certificates (external_id, key_id, data, status, active, member_id)
                VALUES (:certExternalId,
                        (SELECT id FROM signer_keys WHERE external_id = :keyExternalId),
                        :certData,
                        'registered',
                        true,
                        :memberId)
                """;

        var params = new HashMap<String, Object>();
        params.put("certExternalId", certExternalId);
        params.put("keyExternalId", keyExternalId);
        params.put("certData", certData);

        if (memberId != null && !memberId.isEmpty()) {
            params.put("memberId", getOrCreateIdentifier(memberId));
        } else {
            params.put("memberId", null);
        }

        testDatabaseService.getServerconfTemplate().update(sql, params);
    }

    private String getPublicKey(String keyId) {
        // These values should be moved to a configuration file
        var publicKeys = Map.of(
                "DF9242D3CBDE6DAC8058D2878340C3B527041FD0",
                """
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApp1Ls34vBfJkD2bHtmnvb1HxhMBoBPP8rvwtcjGfVCTA7i+DlF3gTLV49k81FMi5gRHQNWLde1\
                NmLTKTzFSoPUerCT7ohvTCTAm4h5W/328xoMo6m2h/nGyuIoAIIUJi/CKf+Ih+zZCklsZqWaOd1f1QIPJOtjQkoMl+2olj2tw1o4/Biim8B03aVTYXfkGh\
                DRC2D6nZJm4Gi9EBZ+USMEAO6CCFobGLLThomWkHDUxjliSGsT4EJA3iR4h9gSuOfMpqHZv5/lY4X4axsR90c8oFEYMfuk9oZSL/dE0oqYpODW1mW7hEm/\
                8afUfTR/8ZtGsvYZFT70VcGcYNNdfoxwIDAQAB""",
                "1342B84B4829BB79226AB268B4D8E70B01068613",
                """
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArSTwszL4sROAQbi6WSuPoQ3+K/dPQoPTdLK/dZvCMkiWW5UmwZRx0PHCjNwUX+FtCYZZ6GF0V\
                /9yrCwMvud+WAuKct/5n9bJLq+FXijupEvhXeyC0I/r6NaOUWK2jyXdMMdQOoBXojQTkNHECj/v7C3NZgHG0QDaXcLvLEJeL8tpec+9qctF0wyKiMvnN9\
                hXiPYG3s9cOEouOn3QL+VYI02Hz/y3zxwDHFiGJ4FAHv2nxnYnhZgeCn5FVeH6aa1IUuS9YEAaqmYSCG6hOsaV5PiPiy51ZmsI8j8KpYTti79ejjN9TuG\
                iEfk1gTPod2iv43sQiszZpcm89kwF3ZHCIwIDAQAB""",
                "FA73509F9E9DFB7A3D92B3D34DA6BD20374A24B0",
                """
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwvzMECjq7ImY9NHu6pGJsAQ1JliHd7KSASVf40WTBEbeIOlPTLKHQeZwxzTWZ2kzuUlmKmPY9\
                S9jVhyJUrimB0vvqp1vu3UfTX9grJ4JyDXojn/gJfKeNmUTILWm+BU+VVv26UhOSMQKxZnX7ow+4NTy1tQWLRscTKjiMf3JtcI2HM7DpedBTHqGziCQzX\
                9jQSSpfag95LEnUv2UwKwtSK2q/CS/TYSWbUCjLv/LAlV26qh9fSWAzgM9UqxxIUWsV1OPUoSUpDBC/SsuP365Bz8n9qRdt17mDE3bVjWiKOSAeiHMmcM\
                EDrRLG0ajasfHZnQeYMQqrBc+rsZLk3cn4QIDAQAB"""
        );
        return publicKeys.get(keyId);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private Long getOrCreateIdentifier(String memberId) {
        //memberId example: DEV:COM:4321 which stands for XROADINSTANCE:MEMBERCLASS:MEMBERCODE
        String[] parts = memberId.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid member ID format. Expected format: XROADINSTANCE:MEMBERCLASS:MEMBERCODE");
        }

        String xroadInstance = parts[0];
        String memberClass = parts[1];
        String memberCode = parts[2];

        // First try to find existing identifier
        var findSql = """
                SELECT id FROM identifier
                WHERE object_type = 'MEMBER'
                AND xroad_instance = :xroadInstance
                AND member_class = :memberClass
                AND member_code = :memberCode
                """;

        var params = Map.of(
                "xroadInstance", xroadInstance,
                "memberClass", memberClass,
                "memberCode", memberCode
        );

        var results = testDatabaseService.getServerconfTemplate().query(
                findSql,
                params,
                (rs, rowNum) -> rs.getLong("id")
        );

        if (!results.isEmpty()) {
            return results.getFirst();
        }

        // If not found, create new identifier
        var insertSql = """
                INSERT INTO identifier (id, object_type, xroad_instance, member_class, member_code)
                VALUES (nextval('hibernate_sequence'), 'MEMBER', :xroadInstance, :memberClass, :memberCode)
                RETURNING id
                """;

        return testDatabaseService.getServerconfTemplate().queryForObject(
                insertSql,
                params,
                Long.class
        );
    }
}
