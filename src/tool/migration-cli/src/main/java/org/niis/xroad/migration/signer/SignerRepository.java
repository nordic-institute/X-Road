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

package org.niis.xroad.migration.signer;

import ee.ria.xroad.common.identifier.ClientId;

import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.migration.utils.DbCredentials;
import org.niis.xroad.signer.keyconf.CertRequestType;
import org.niis.xroad.signer.keyconf.CertificateType;
import org.niis.xroad.signer.keyconf.DeviceType;
import org.niis.xroad.signer.keyconf.KeyType;
import org.postgresql.ds.PGSimpleDataSource;

import javax.xml.datatype.XMLGregorianCalendar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Base64;
import java.util.Optional;

@SuppressWarnings("checkstyle:MagicNumber")
public class SignerRepository {
    private final Connection connection;
    private final String nodeName;

    public SignerRepository(DbCredentials dbCredentials, NodeIdentity nodeIdentity) {
        this.nodeName = nodeIdentity.name();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(dbCredentials.jdbcUrl());
        ds.setUser(dbCredentials.username());
        ds.setPassword(new String(dbCredentials.password()));
        if (StringUtils.isNotBlank(dbCredentials.schema())) {
            ds.setCurrentSchema(dbCredentials.schema());
        }

        try {
            this.connection = ds.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Signer DB connection", e);
        }
    }

    private Optional<Long> findId(String table, String externalId) throws SQLException {
        String sql = "SELECT id FROM " + table + " WHERE ha_node_name = ? AND external_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, nodeName);
            ps.setString(2, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong("id"));
                }
                return Optional.empty();
            }
        }
    }

    Optional<Long> getTokenId(String externalId) throws SQLException {
        return findId("signer_tokens", externalId);
    }

    long saveToken(DeviceType deviceType, byte[] pinHash) throws SQLException {
        String sql = "INSERT INTO signer_tokens(ha_node_name, external_id, type, friendly_name, serial_no, label, pin) VALUES "
                + "(?, ?, ?, ?, ?, ?, ?) RETURNING id";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nodeName); // ha_node_name
            statement.setString(2, deviceType.getId()); // external_id
            statement.setString(3, deviceType.getDeviceType()); // type
            statement.setString(4, deviceType.getFriendlyName()); // friendly_name
            statement.setString(5, deviceType.getTokenId()); // serial_no
            statement.setString(6, deviceType.getSlotId()); // label
            statement.setObject(7, pinHash, Types.BINARY); // pin hash

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                } else {
                    throw new SQLException("Failed to insert token.");
                }
            }
        }
    }

    Optional<Long> getKeyId(String externalId) throws SQLException {
        return findId("signer_keys", externalId);
    }

    long saveKey(KeyType keyType, long tokenId, boolean isSoftToken, byte[] keystore) throws SQLException {
        String sql = "INSERT INTO signer_keys(ha_node_name, external_id, token_id, type, friendly_name, label, "
                + "public_key, keystore, sign_mechanism_name, usage) VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nodeName); // ha_node_name
            statement.setString(2, keyType.getKeyId()); // external_id
            statement.setLong(3, tokenId); // token_id
            statement.setString(4, isSoftToken ? "SOFTWARE" : "HARDWARE"); // type
            statement.setString(5, keyType.getFriendlyName()); // friendly_name
            statement.setString(6, keyType.getLabel()); // label
            statement.setString(7, toBase64(keyType.getPublicKey())); // public_key
            statement.setObject(8, keystore, Types.BINARY); // keystore
            statement.setString(9, StringUtils.defaultIfBlank(keyType.getSignMechanismName(), "CKM_RSA_PKCS"));
            statement.setString(10, keyType.getUsage().name()); // usage

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                } else {
                    throw new SQLException("Failed to insert key.");
                }
            }
        }
    }

    Optional<Long> getCertificateId(String externalId) throws SQLException {
        return findId("signer_certificates", externalId);
    }

    void saveCertificate(CertificateType cert, long keyId) throws SQLException {
        String sql = "INSERT INTO signer_certificates(ha_node_name, external_id, key_id, member_id, data, status, active, "
                + " next_renewal_time, renewed_cert_hash, renewal_error, ocsp_verify_error) VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nodeName); // ha_node_name
            statement.setString(2, cert.getId()); // external_id
            statement.setLong(3, keyId); // key_id
            statement.setObject(4, ensureMemberId(cert.getMemberId()), Types.BIGINT); // member_id
            statement.setBytes(5, cert.getContents()); // data
            statement.setString(6, cert.getStatus()); // status
            statement.setBoolean(7, cert.isActive()); // active
            statement.setTimestamp(8, toTimestamp(cert.getNextRenewalTime())); // next_renewal_time
            statement.setString(9, cert.getRenewedCertHash()); // renewed_cert_hash
            statement.setString(10, cert.getRenewalError()); // renewal_error
            statement.setString(11, cert.getOcspVerifyBeforeActivationError()); // ocsp_verify_error

            statement.executeUpdate();
        }
    }

    Optional<Long> getCertificateRequestId(String externalId) throws SQLException {
        return findId("signer_certificate_requests", externalId);
    }

    void saveCertificateRequest(CertRequestType certRequest, long keyId) throws SQLException {
        String sql = "INSERT INTO signer_certificate_requests(ha_node_name, external_id, key_id, member_id, subject_name, "
                + " subject_alternative_name, certificate_profile) "
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nodeName);
            statement.setString(2, certRequest.getId());
            statement.setLong(3, keyId);
            statement.setObject(4, ensureMemberId(certRequest.getMemberId()), Types.BIGINT);
            statement.setString(5, certRequest.getSubjectName());
            statement.setString(6, certRequest.getSubjectAlternativeName());
            statement.setString(7, certRequest.getCertificateProfile());

            statement.executeUpdate();
        }
    }

    private String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private Timestamp toTimestamp(XMLGregorianCalendar xmlCal) {
        if (xmlCal == null) {
            return null;
        }
        return new Timestamp(xmlCal.toGregorianCalendar().getTimeInMillis());
    }


    Long ensureMemberId(ClientId.Conf memberId) throws SQLException {
        if (memberId == null) {
            return null;
        }
        String sql = "WITH existing AS ( "
                + "    SELECT id "
                + "    FROM identifier "
                + "    WHERE object_type = 'MEMBER' "
                + "      AND xroad_instance = ? "
                + "      AND member_class = ? "
                + "      AND member_code = ? "
                + "    LIMIT 1),"
                + "created AS (INSERT INTO identifier (object_type, xroad_instance, member_class, member_code) "
                + "             SELECT 'MEMBER', ?, ?, ? "
                + "             WHERE NOT EXISTS (SELECT 1 FROM existing) "
                + "             RETURNING id) "
                + " SELECT id FROM existing"
                + " UNION ALL"
                + " SELECT id FROM created";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId.getXRoadInstance());
            statement.setString(2, memberId.getMemberClass());
            statement.setString(3, memberId.getMemberCode());
            statement.setString(4, memberId.getXRoadInstance());
            statement.setString(5, memberId.getMemberClass());
            statement.setString(6, memberId.getMemberCode());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                } else {
                    throw new SQLException("Failed to insert or find member identifier.");
                }
            }
        }

    }

}
