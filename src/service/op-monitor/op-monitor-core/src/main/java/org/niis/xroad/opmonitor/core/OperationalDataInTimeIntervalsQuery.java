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

import ee.ria.xroad.common.identifier.ServiceId;

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
                                                    Integer intervalInMinutes,
                                                    OpMonitoringData.SecurityServerType securityServerType,
                                                    ClientId memberId,
                                                    ServiceId serviceId) {
        String sql = createSql(securityServerType, memberId, serviceId);
        NativeQuery<OperationalDataInTimeInterval> query =
                createQuery(sql, startTime, endTime, intervalInMinutes, securityServerType, memberId, serviceId);
        return query.getResultList();
    }

    private static String createSql(OpMonitoringData.SecurityServerType securityServerType, ClientId memberId, ServiceId serviceId) {
        StringBuilder sql = new StringBuilder("""
                WITH time_buckets AS (
                    SELECT GENERATE_SERIES(
                        TO_TIMESTAMP(:startTimeMillis / 1000),
                        TO_TIMESTAMP(:endTimeMillis / 1000),
                        (:interval || ' minutes')::INTERVAL
                    ) AS time_interval_start
                ),
                request_counts AS (
                    SELECT
                        TO_TIMESTAMP(FLOOR(request_in_ts / 1000 / (:interval * 60)) * (:interval * 60)) AS time_interval_start,
                        COUNT(*) FILTER (WHERE succeeded = true) AS success_count,
                        COUNT(*) FILTER (WHERE succeeded = false) AS failure_count
                    FROM operational_data
                    WHERE request_in_ts BETWEEN :startTimeMillis AND :endTimeMillis
                """);
        addOptionalFilters(sql, securityServerType, memberId, serviceId);
        sql.append("""
                    GROUP BY time_interval_start
                )
                SELECT
                    tb.time_interval_start,
                    COALESCE(rc.success_count, 0) AS success_count,
                    COALESCE(rc.failure_count, 0) AS failure_count
                FROM time_buckets tb
                LEFT JOIN request_counts rc ON tb.time_interval_start = rc.time_interval_start
                ORDER BY tb.time_interval_start;
                """);
        return sql.toString();
    }

    private static void addOptionalFilters(StringBuilder sql,
                                           OpMonitoringData.SecurityServerType securityServerType,
                                           ClientId memberId,
                                           ServiceId serviceId) {
        if (securityServerType != null) {
            sql.append(" AND security_server_type = :securityServerType");
        }
        if (memberId != null) {
            sql.append(" AND ((");
            sql.append("     client_xroad_instance = :clientXroadInstance");
            sql.append(" AND client_member_class = :clientMemberClass");
            sql.append(" AND client_member_code = :clientMemberCode");
            if (memberId.isSubsystem()) {
                sql.append(" AND client_subsystem_code = :clientSubsystemCode");
            } else {
                sql.append(" AND client_subsystem_code IS NULL");
            }
            sql.append(") OR (");
            sql.append("     service_xroad_instance = :clientXroadInstance");
            sql.append(" AND service_member_class = :clientMemberClass");
            sql.append(" AND service_member_code = :clientMemberCode");
            if (memberId.isSubsystem()) {
                sql.append(" AND service_subsystem_code = :clientSubsystemCode");
            } else {
                sql.append(" AND service_subsystem_code IS NULL");
            }
            sql.append("))");
        }
        if (serviceId != null) {
            sql.append(" AND (");
            sql.append("     service_xroad_instance = :serviceXroadInstance");
            sql.append(" AND service_member_class = :serviceMemberClass");
            sql.append(" AND service_member_code = :serviceMemberCode");
            sql.append(" AND service_subsystem_code = :serviceSubsystemCode");
            sql.append(" AND service_code = :serviceCode");
            if (serviceId.getServiceVersion() != null) {
                sql.append(" AND service_version = :serviceVersion");
            }
            sql.append(")");
        }
    }

    private NativeQuery<OperationalDataInTimeInterval> createQuery(String sql,
                                                                   Long startTime,
                                                                   Long endTime,
                                                                   Integer intervalInMinutes,
                                                                   OpMonitoringData.SecurityServerType securityServerType,
                                                                   ClientId memberId,
                                                                   ServiceId serviceId) {
        long intervalInMillis = intervalInMinutes * 60L * 1000L;
        NativeQuery<OperationalDataInTimeInterval> query =
                session.createNativeQuery(sql, OperationalDataInTimeInterval.class)
                        .setParameter("startTimeMillis", Math.floorDiv(startTime, intervalInMillis) * intervalInMillis)
                        .setParameter("endTimeMillis",
                                Math.floorDiv(endTime, intervalInMillis) * intervalInMillis + intervalInMillis)
                        .setParameter("interval", intervalInMinutes);
        if (securityServerType != null) {
            query.setParameter("securityServerType", securityServerType.getTypeString());
        }
        if (memberId != null) {
            query.setParameter("clientXroadInstance", memberId.getXRoadInstance());
            query.setParameter("clientMemberClass", memberId.getMemberClass());
            query.setParameter("clientMemberCode", memberId.getMemberCode());
            if (memberId.isSubsystem()) {
                query.setParameter("clientSubsystemCode", memberId.getSubsystemCode());
            }
        }
        if (serviceId != null) {
            query.setParameter("serviceXroadInstance", serviceId.getXRoadInstance());
            query.setParameter("serviceMemberClass", serviceId.getMemberClass());
            query.setParameter("serviceMemberCode", serviceId.getMemberCode());
            query.setParameter("serviceSubsystemCode", serviceId.getSubsystemCode());
            query.setParameter("serviceCode", serviceId.getServiceCode());
            if (serviceId.getServiceVersion() != null) {
                query.setParameter("serviceVersion", serviceId.getServiceVersion());
            }
        }
        return query;
    }


}
