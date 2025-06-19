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
                "E67CCA8E9B3DA52DB740CDCDC0926F356F431063",
                """
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8jWpRCjP+NCTTHdpab5DjCzt4Yi5KQi48vz6yjLAR4A5WhoN4PDi9sienylWQrXAT\
                T5ajjTlHaDEPcW8q0elUfD8f1wWqv/uGswfd4PHbydMNntRqyu1CobGFQrAwxr6a4Ikhv785q5aLxI/F3Ub161diubYC7/EEeTJJEmVpiv21M\
                60z27PcGI14g0hbUKQEGyukWQCvpcXDxZCSLJORhwMVScQ/JF81uDOHCYp9lw5X1nbddCryPRrGzYr45tOU+3mUwU/Og4UTQSOua1z19brS7Y\
                pgvGBhMOp5F9sJYYgQGZXXWy5HGeiJu5oVeWPhxUFQaxTVguhkEZY5OVCzQIDAQAB""",
                "056A952E76B40A46C07628C7B13E5934E39A9C78",
                """
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoR0Yz6qnGBLjGYjZR9D1gShOg0oC0OdBpoxYDBZ40N+/DY3JNUzzcLNeH7X3/E+QQQlKJl3cu\
                LuKxkHkkjymOTNdtTTAv4w35Ginbb4theZ/1ma3QGaPSdESKfw26/RZsHf4qIZMSmNM6+6DUf57AhyODemXyNolPPuHTp8Tq/LwcPFE+TwRR/BiCorj0y\
                afykSkg7hfHr+EAuilxk+kNFqThb08buYsHYeVfF8JoQAM5NL56wTxFMp6eSOO7EsirGMj+y0+TEMZ8a4ofiVEnPN69qJzIJnx9akUFxV2b5+YpWi1GuN\
                w3PqBxTx/8aNFeU9hoUpQ7W5h1soWWyrxqQIDAQAB""",
                "A1B0BEB1E088E3A291AEEC57FB04400BF17D3E0D",
                """
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzNqJJoIa1hcPGnY2768V8b/xLcgjdQQ9Dk7KQmAtKj/7EwqfFGbVyaltXZwCWE82yVtHlPTo\
                z1Mr73qXz+twZ/+j6SxWNBmJLlHpP0E7RV9OI6e7YuJWh4GgaCe5VT7Rywwrfp3vrtSM4C1Lg2dml32W1gX58Xd3fMHVGqh7GgtKQOVWpB4+8aq1NJEJ\
                p1F5+dSn+5039oV7iWyOSyJvWam4kXq/fgl93IiH0yfEuv/a+qUJEO5lG5v5tCHXPSmCMd83sfTqRL7O69AozagH5gEkCVg9m570SuL+OmSxh8PuZ3Fy\
                FW3Y2ibPruw9CnYMIJJPjcsEIpVXI3bftsx3LQIDAQAB"""
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
                WHERE discriminator = 'C'
                AND type = 'MEMBER'
                AND xroadinstance = :xroadInstance
                AND memberclass = :memberClass
                AND membercode = :memberCode
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
            return results.get(0);
        }

        // If not found, create new identifier
        var insertSql = """
                INSERT INTO identifier (id, discriminator, type, xroadinstance, memberclass, membercode)
                VALUES (nextval('hibernate_sequence'), 'C', 'MEMBER', :xroadInstance, :memberClass, :memberCode)
                RETURNING id
                """;

        return testDatabaseService.getServerconfTemplate().queryForObject(
                insertSql,
                params,
                Long.class
        );
    }
}
