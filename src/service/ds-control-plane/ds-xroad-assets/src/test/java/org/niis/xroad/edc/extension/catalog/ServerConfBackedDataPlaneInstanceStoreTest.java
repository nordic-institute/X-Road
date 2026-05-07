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

import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerConfBackedDataPlaneInstanceStoreTest {

    private static final String INSTANCE_ID = "xroad-provider-dataplane";
    private static final String INSTANCE_URL = "http://localhost:19195/api/signaling/v1/dataflows";

    private ServerConfBackedDataPlaneInstanceStore store;

    @BeforeEach
    void setUp() {
        store = new ServerConfBackedDataPlaneInstanceStore();
    }

    @Test
    void saveThenGetAllReturnsSingleInstance() {
        var instance = buildInstance(INSTANCE_ID);
        store.save(instance);

        assertThat(store.getAll()).containsExactly(instance);
    }

    @Test
    void saveTwiceWithSameIdIsIdempotentUpsert() {
        var instance1 = buildInstance(INSTANCE_ID);
        var instance2 = buildInstance(INSTANCE_ID);
        store.save(instance1);
        store.save(instance2);

        assertThat(store.getAll()).hasSize(1);
    }

    @Test
    void findByIdExistingInstanceReturnsInstance() {
        var instance = buildInstance(INSTANCE_ID);
        store.save(instance);

        assertThat(store.findById(INSTANCE_ID)).isEqualTo(instance);
    }

    @Test
    void findByIdNonexistentReturnsNull() {
        assertThat(store.findById("nonexistent")).isNull();
    }

    @Test
    void deleteByIdExistingInstanceRemovesAndReturnsSuccess() {
        var instance = buildInstance(INSTANCE_ID);
        store.save(instance);

        var result = store.deleteById(INSTANCE_ID);

        assertThat(result.succeeded()).isTrue();
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    void deleteByIdNonexistentReturnsNotFound() {
        var result = store.deleteById("nonexistent");

        assertThat(result.failed()).isTrue();
    }

    @Test
    void nextNotLeasedReturnsEmptyList() {
        store.save(buildInstance(INSTANCE_ID));

        assertThat(store.nextNotLeased(10)).isEmpty();
    }

    @Test
    void findByIdAndLeaseExistingInstanceReturnsSuccess() {
        var instance = buildInstance(INSTANCE_ID);
        store.save(instance);

        var result = store.findByIdAndLease(INSTANCE_ID);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isEqualTo(instance);
    }

    @Test
    void findByIdAndLeaseNonexistentReturnsNotFound() {
        var result = store.findByIdAndLease("nonexistent");

        assertThat(result.failed()).isTrue();
    }

    private DataPlaneInstance buildInstance(String id) {
        return DataPlaneInstance.Builder.newInstance()
                .id(id)
                .url(INSTANCE_URL)
                .allowedTransferType("Xrd-PULL")
                .participantContextId("xroad-provider")
                .build();
    }
}
