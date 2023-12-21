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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.CLIENT_MEMBER_CLASS;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.CLIENT_MEMBER_CODE;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.CLIENT_SUBSYSTEM_CODE;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.CLIENT_XROAD_INSTANCE;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SECURITY_SERVER_INTERNAL_IP;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SERVICE_MEMBER_CLASS;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SERVICE_MEMBER_CODE;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SERVICE_SUBSYSTEM_CODE;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SERVICE_XROAD_INSTANCE;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.MONITORING_DATA_TS;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.OUTPUT_FIELDS;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.PUBLIC_OUTPUT_FIELDS;

@SuppressWarnings("checkstyle:magicnumber")
final class OperationalDataRecordQuery {

    private final CriteriaBuilder cb;
    private final CriteriaQuery<Tuple> query;
    private final Root<OperationalDataRecord> from;
    private final Session session;

    @Setter
    private int maxRecords = Integer.MAX_VALUE;

    private Predicate pred;
    private List<Selection<?>> projection = new ArrayList<>();
    private jakarta.persistence.criteria.Order order = null;

    OperationalDataRecordQuery(Session session, ClientId clientFilter, ClientId serviceProviderFilter,
            Set<String> outputFields) {
        this.session = session;
        cb = this.session.getCriteriaBuilder();
        query = cb.createTupleQuery();
        from = query.from(OperationalDataRecord.class);
        pred = cb.conjunction();

        configureOutputFields(clientFilter != null, outputFields);
        configureClientAndServiceProviderFilters(clientFilter, serviceProviderFilter);
    }

    /**
     * Configures the projected output fields
     * @see #transform
     */
    private void configureOutputFields(boolean publicFieldsOnly, Set<String> outputFields) {
        if (publicFieldsOnly) {
            Set<String> fields;
            if (outputFields.isEmpty()) {
                fields = PUBLIC_OUTPUT_FIELDS;
            } else {
                fields = new HashSet<>(outputFields);
                fields.remove(SECURITY_SERVER_INTERNAL_IP);
                fields.add(MONITORING_DATA_TS);
            }
            fields.forEach(s -> projection.add(from.get(s).alias(s)));
        } else {
            if (!outputFields.isEmpty()) {
                outputFields.forEach(s -> projection.add(from.get(s).alias(s)));
                if (!outputFields.contains(MONITORING_DATA_TS)) {
                    projection.add(from.get(MONITORING_DATA_TS).alias(MONITORING_DATA_TS));
                }
            } else {
                OUTPUT_FIELDS.forEach(s -> projection.add(from.get(s).alias(s)));
            }
        }
    }

    private void configureClientAndServiceProviderFilters(ClientId client, ClientId serviceProvider) {
        if (client != null) {
            if (serviceProvider != null) {
                // Filter by both the client and the service provider
                // using the fields corresponding to their roles.
                pred = cb.and(pred, cb.and(
                        cb.or(getMemberCriterion(client, true), getMemberCriterion(client, false)),
                        getMemberCriterion(serviceProvider, false)));
            } else {
                // Filter by the client in either roles (client or
                // service provider).
                pred = cb.and(pred, cb.or(getMemberCriterion(client, true), getMemberCriterion(client, false)));
            }
        } else {
            if (serviceProvider != null) {
                // Filter by the service provider in its respective role.
                pred = cb.and(pred, getMemberCriterion(serviceProvider, false));
            }
        }
    }

    private Predicate getMemberCriterion(ClientId member, boolean isClient) {
        return cb.and(
                cb.equal(isClient ? from.get(CLIENT_XROAD_INSTANCE) : from.get(SERVICE_XROAD_INSTANCE),
                        member.getXRoadInstance()),
                cb.equal(isClient ? from.get(CLIENT_MEMBER_CLASS) : from.get(SERVICE_MEMBER_CLASS),
                        member.getMemberClass()),
                cb.equal(isClient ? from.get(CLIENT_MEMBER_CODE) : from.get(SERVICE_MEMBER_CODE),
                        member.getMemberCode()),
                member.getSubsystemCode() == null
                        ? cb.isNull(isClient ? from.get(CLIENT_SUBSYSTEM_CODE) : from.get(SERVICE_SUBSYSTEM_CODE))
                        : cb.equal(isClient ? from.get(CLIENT_SUBSYSTEM_CODE) : from.get(SERVICE_SUBSYSTEM_CODE),
                                member.getSubsystemCode()));
    }

    void addOverflowCriteria(long monitoringDataTs) {
        pred = cb.and(pred, cb.equal(from.get(MONITORING_DATA_TS), monitoringDataTs));
    }

    List<OperationalDataRecord> list() {
        query.multiselect(projection).where(pred);
        if (order != null) {
            query.orderBy(order);
        }
        return transform(session.createQuery(query)
                .setReadOnly(true)
                .setMaxResults(maxRecords)
                .getResultList());
    }

    void between(long fromTs, long toTs) {
        pred = cb.and(pred, cb.between(from.get(MONITORING_DATA_TS), fromTs, toTs));
    }

    void orderByAsc(String field) {
        order = cb.asc(from.get(field));
    }

    /**
     * Transforms a list of Tuple to list of OperationalDataRecord
     *
     * A tuple represents a partial OperationalDataRecord. Assumes that the column aliases in a tuple match the field
     * names and a setter for the field exists (uses reflection). Assumes that the values can
     * be directly assigned (no recursive tranformation).
     * @see #configureOutputFields
     * @see OperationalDataRecord
     */
    private static List<OperationalDataRecord> transform(List<Tuple> result) {
        List<OperationalDataRecord> tmp = new ArrayList<>(result.size());

        for (Tuple t : result) {
            final OperationalDataRecord record = new OperationalDataRecord();
            for (TupleElement<?> te : t.getElements()) {
                final Method method = SETTERS.get(te.getAlias());
                if (method != null) {
                    try {
                        method.invoke(record, t.get(te));
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new IllegalArgumentException("Unable to transform", e);
                    }
                }
            }
            tmp.add(record);
        }
        return tmp;
    }

    /*
     * Optimizes reflective transform by precomputing the setter methods of the
     * target class (OperationalDataRecord)
     */
    private static final Map<String, Method> SETTERS;

    static {
        final HashMap<String, Method> tmp = new HashMap<>();
        try {
            for (Method method : OperationalDataRecord.class.getMethods()) {
                if (method.getParameterCount() == 1
                        && method.getName().startsWith("set")
                        && method.getReturnType().equals(Void.TYPE)) {
                    tmp.put(StringUtils.uncapitalize(method.getName().substring(3)), method);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        SETTERS = Collections.unmodifiableMap(tmp);
    }
}
