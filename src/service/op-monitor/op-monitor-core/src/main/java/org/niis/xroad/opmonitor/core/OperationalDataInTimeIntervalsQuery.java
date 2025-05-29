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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.identifier.ClientId;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.niis.xroad.opmonitor.api.OpMonitoringData;

import java.util.List;

@SuppressWarnings("checkstyle:magicnumber")
final class OperationalDataInTimeIntervalsQuery {

    private final Session session;

    OperationalDataInTimeIntervalsQuery(Session session) {
        this.session = session;
    }

    public List<OperationalDataInTimeInterval> list(Long startTime,
                                                    Long endTime,
                                                    Long intervalInMinutes,
                                                    OpMonitoringData.SecurityServerType securityServerType,
                                                    Boolean succeeded,
                                                    ClientId clientId) {
        String sql = createSql(securityServerType, succeeded, clientId);
        NativeQuery<OperationalDataInTimeInterval> query =
                createQuery(sql, startTime, endTime, intervalInMinutes, securityServerType, succeeded, clientId);
        return query.getResultList();
    }

    private static String createSql(OpMonitoringData.SecurityServerType securityServerType, Boolean succeeded, ClientId clientId) {
        StringBuilder sql = new StringBuilder("""
                WITH time_buckets AS (
                    SELECT GENERATE_SERIES(
                        TO_TIMESTAMP(:startTimeMillis / 1000),
                        TO_TIMESTAMP(:endTimeMillis / 1000),
                        :interval * 60
                    ) AS time_interval_start
                ),
                request_counts AS (
                    SELECT
                        TO_TIMESTAMP(FLOOR(request_in_ts / 1000 / (:interval * 60)) * (:interval * 60)) AS time_interval_start,
                        COUNT(*) FILTER (WHERE succeeded = true) AS success_count,
                        COUNT(*) FILTER (WHERE succeeded = false) AS failure_count,
                        COUNT(*) FILTER (WHERE security_server_type = 'Producer') AS incoming_count,
                        COUNT(*) FILTER (WHERE security_server_type = 'Client') AS outgoing_count
                    FROM operational_data
                    WHERE request_in_ts BETWEEN :startTimeMillis AND :endTimeMillis
                """);
        addOptionalFilters(sql, securityServerType, succeeded, clientId);
        sql.append("""
                    GROUP BY time_interval_start
                )
                SELECT
                    tb.time_interval_start,
                    COALESCE(rc.success_count, 0) AS success_count,
                    COALESCE(rc.failure_count, 0) AS failure_count,
                    COALESCE(rc.incoming_count, 0) AS incoming_count,
                    COALESCE(rc.outgoing_count, 0) AS outgoing_count
                FROM time_buckets tb
                LEFT JOIN request_counts rc ON tb.time_interval_start = rc.time_interval_start
                ORDER BY tb.time_interval_start;
                """);
        return sql.toString();
    }

    private static void addOptionalFilters(StringBuilder sql,
                                           OpMonitoringData.SecurityServerType securityServerType,
                                           Boolean succeeded,
                                           ClientId clientId) {
        if (securityServerType != null) {
            sql.append(" AND security_server_type = :securityServerType");
        }
        if (succeeded != null) {
            sql.append(" AND succeeded = :succeeded");
        }
        if (clientId != null) {
            sql.append(" AND (");
            sql.append("     client_xroad_instance = :clientXroadInstance");
            sql.append(" AND client_member_class = :clientMemberClass");
            sql.append(" AND client_member_code = :clientMemberCode");
            if (clientId.isSubsystem()) {
                sql.append(" AND client_subsystem_code = :clientSubsystemCode");
            } else {
                sql.append(" AND client_subsystem_code IS NULL");
            }
            sql.append(") OR (");
            sql.append("     service_xroad_instance = :clientXroadInstance");
            sql.append(" AND service_member_class = :clientMemberClass");
            sql.append(" AND service_member_code = :clientMemberCode");
            if (clientId.isSubsystem()) {
                sql.append(" AND service_subsystem_code = :clientSubsystemCode");
            } else {
                sql.append(" AND service_subsystem_code IS NULL");
            }
            sql.append(")");
        }
    }

    private NativeQuery<OperationalDataInTimeInterval> createQuery(String sql,
                                                                   Long startTime,
                                                                   Long endTime,
                                                                   Long intervalInMinutes,
                                                                   OpMonitoringData.SecurityServerType securityServerType,
                                                                   Boolean succeeded,
                                                                   ClientId clientId) {
        NativeQuery<OperationalDataInTimeInterval> query =
                session.createNativeQuery(sql, OperationalDataInTimeInterval.class)
                        .setParameter("startTimeMillis", startTime)
                        .setParameter("endTimeMillis", endTime)
                        .setParameter("interval", intervalInMinutes);
        if (securityServerType != null) {
            query.setParameter("securityServerType", securityServerType.getTypeString());
        }
        if (succeeded != null) {
            query.setParameter("succeeded", succeeded);
        }
        if (clientId != null) {
            query.setParameter("clientXroadInstance", clientId.getXRoadInstance())
                    .setParameter("clientMemberClass", clientId.getMemberClass())
                    .setParameter("clientMemberCode", clientId.getMemberCode());
            if (clientId.isSubsystem()) {
                query.setParameter("clientSubsystemCode", clientId.getSubsystemCode());
            }
        }
        return query;
    }

}
