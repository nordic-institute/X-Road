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
package org.niis.xroad.edc.extension.catalog;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.configuration.Config;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only {@link DataPlaneInstanceStore} that answers from the node's own configuration. It holds no
 * state, reads no database and has no registration step, so every node answers the same for every participant
 * immediately after start.
 *
 * <p>Each enabled configuration entry applies to every participant context alike. An instance is built on
 * demand with the id {@code <entry id>::<participant context id>}, the node's configured URL, the configured
 * allowed source and transfer types, state REGISTERED, no labels and no authorization profile. Only two
 * shapes are served: lookup by such an id, and a query whose filter is exactly one
 * {@code participantContextId = <ctx>} criterion (paging and sorting are ignored). Every other operation
 * throws {@link UnsupportedOperationException}.</p>
 *
 * <p>The store does not check that a participant context exists. Callers have already loaded a validated
 * participant context, transfer process or asset before they ask for its data plane, and a removed participant
 * context is rejected before the store is consulted. The participant context id is treated as opaque text.</p>
 */
@Slf4j
final class XRoadDataPlaneInstanceStore implements DataPlaneInstanceStore {

    static final String KEY_ID = "id";
    static final String KEY_URL = "url";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_ALLOWED_SOURCE_TYPES = "allowed-source-types";
    static final String KEY_ALLOWED_TRANSFER_TYPES = "allowed-transfer-types";
    static final String ID_SEPARATOR = "::";

    private static final String PARTICIPANT_CONTEXT_ID_FIELD = "participantContextId";
    private static final String EQUALS_OPERATOR = "=";

    private final Map<String, Entry> entriesById = new LinkedHashMap<>();

    XRoadDataPlaneInstanceStore(List<Config> entries) {
        rejectDuplicateIds(entries);
        entries.forEach(this::addEntry);
    }

    @Override
    public DataPlaneInstance findById(String id) {
        if (id == null) {
            return null;
        }
        var separatorIndex = id.indexOf(ID_SEPARATOR);
        if (separatorIndex < 0) {
            return null;
        }
        var participantContextId = id.substring(separatorIndex + ID_SEPARATOR.length());
        var entry = entriesById.get(id.substring(0, separatorIndex));
        if (entry == null || participantContextId.isEmpty()) {
            return null;
        }
        return newInstance(entry, participantContextId);
    }

    @Override
    public Stream<DataPlaneInstance> query(QuerySpec querySpec) {
        var participantContextId = requireParticipantContextIdFilter(querySpec.getFilterExpression());
        return entriesById.values().stream().map(entry -> newInstance(entry, participantContextId));
    }

    @Override
    public Stream<DataPlaneInstance> getAll() {
        throw unsupported("getAll");
    }

    @Override
    public StoreResult<DataPlaneInstance> deleteById(String instanceId) {
        throw unsupported("deleteById");
    }

    @Override
    public StoreResult<Void> save(DataPlaneInstance entity) {
        throw unsupported("save");
    }

    @Override
    public List<DataPlaneInstance> nextNotLeased(int max, Criterion... criteria) {
        throw unsupported("nextNotLeased");
    }

    @Override
    public StoreResult<DataPlaneInstance> findByIdAndLease(String id) {
        throw unsupported("findByIdAndLease");
    }

    @Override
    public StoreResult<Void> breakLease(DataPlaneInstance entity) {
        throw unsupported("breakLease");
    }

    private static void rejectDuplicateIds(List<Config> entries) {
        var nodesById = new HashMap<String, String>();
        for (var entry : entries) {
            var id = entry.getString(KEY_ID);
            var previousNode = nodesById.putIfAbsent(id, entry.currentNode());
            if (previousNode != null) {
                throw new EdcException("Data plane entries '%s' and '%s' share the id '%s'; entry ids must be unique"
                        .formatted(previousNode, entry.currentNode(), id));
            }
        }
    }

    private void addEntry(Config entry) {
        var node = entry.currentNode();
        var id = entry.getString(KEY_ID);
        if (id.contains(ID_SEPARATOR)) {
            throw new EdcException("Data plane entry '%s' has id '%s' containing '%s', which is reserved as the id separator"
                    .formatted(node, id, ID_SEPARATOR));
        }
        if (!entry.getBoolean(KEY_ENABLED, true)) {
            log.info("Data plane entry '{}' is disabled — skipping.", node);
            return;
        }
        entriesById.put(id, new Entry(id, parseUrl(node, entry.getString(KEY_URL)),
                multiValues(entry, KEY_ALLOWED_SOURCE_TYPES), multiValues(entry, KEY_ALLOWED_TRANSFER_TYPES)));
    }

    private static URL parseUrl(String node, String url) {
        try {
            return URI.create(url).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new EdcException("Data plane entry '%s' has an invalid url '%s'".formatted(node, url), e);
        }
    }

    private static Set<String> multiValues(Config entry, String key) {
        var value = entry.getString(key, "");
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireParticipantContextIdFilter(List<Criterion> filter) {
        if (filter.size() == 1) {
            var criterion = filter.getFirst();
            if (PARTICIPANT_CONTEXT_ID_FIELD.equals(criterion.getOperandLeft())
                    && EQUALS_OPERATOR.equals(criterion.getOperator())
                    && criterion.getOperandRight() instanceof String participantContextId
                    && !participantContextId.isEmpty()) {
                return participantContextId;
            }
        }
        throw new UnsupportedOperationException(
                "Data plane instance query supports only the single filter '%s %s <participant context id>', got: %s"
                        .formatted(PARTICIPANT_CONTEXT_ID_FIELD, EQUALS_OPERATOR, filter));
    }

    private static DataPlaneInstance newInstance(Entry entry, String participantContextId) {
        var instance = DataPlaneInstance.Builder.newInstance()
                .id(entry.id() + ID_SEPARATOR + participantContextId)
                .participantContextId(participantContextId)
                .url(entry.url())
                .allowedSourceTypes(entry.allowedSourceTypes())
                .allowedTransferType(entry.allowedTransferTypes())
                .build();
        instance.transitionToRegistered();
        return instance;
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(
                "XRoadDataPlaneInstanceStore is read-only and answers only by id or by participantContextId; '%s' is not supported"
                        .formatted(operation));
    }

    private record Entry(String id, URL url, Set<String> allowedSourceTypes, Set<String> allowedTransferTypes) {
    }
}
