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
package org.niis.xroad.restapi.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.entity.ConfigurationPropertyEntity;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
import org.niis.xroad.restapi.service.ConfigurablePropertiesService;
import org.niis.xroad.restapi.service.ConfigurablePropertySource;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationPropertySharedRowsIntegrationTest extends AbstractSpringMvcTest {

    private static final String NODE_1 = "node_1";
    private static final String NODE_2 = "node_2";

    private static final Prefix PREFIX = Prefix.of(Category.PROXY, "xroad.test-shared-rows");
    private static final ConfigKey<Integer> KEY = PREFIX.integer("retry-count")
            .withDefaultValue(60).exposedInUi().build();
    private static final ConfigKey<Integer> NODE_ONLY_KEY = PREFIX.integer("node-only")
            .withDefaultValue(7).exposedInUi().build();

    private static final ConfigurablePropertySource SOURCE = new ConfigurablePropertySource() {
        @Override
        public List<ConfigKeyProvider> getConfigKeyProviders() {
            return List.of(ConfigKeyProvider.forPrefix(PREFIX));
        }

        @Override
        public String categoryToScope(Category category) {
            return "proxy";
        }
    };

    @Autowired
    private ConfigurationPropertyRepository repository;

    @Autowired
    private AuditDataHelper auditData;

    @PersistenceContext
    private EntityManager entityManager;

    private ConfigurablePropertiesService service;

    @BeforeEach
    void setUp() {
        service = new ConfigurablePropertiesService(auditData, repository, SOURCE);
    }

    @Test
    void findByPropertyKeyReturnsTheSharedRowNextToNodeRows() {
        save(KEY.key(), "100", null);
        save(KEY.key(), "200", NODE_1);
        save(KEY.key(), "300", NODE_2);

        Optional<ConfigurationPropertyEntity> found = repository.findByPropertyKey(KEY.key());

        assertTrue(found.isPresent());
        assertEquals("100", found.get().getPropertyValue());
        assertNull(found.get().getHaNodeName());
    }

    @Test
    void findAllReturnsOneSharedEntryPerKey() {
        save(KEY.key(), "100", null);
        save(KEY.key(), "200", NODE_1);

        List<ConfigurationPropertyEntity> all = repository.findAll().stream()
                .filter(e -> KEY.key().equals(e.getPropertyKey()))
                .toList();

        assertEquals(1, all.size());
        assertEquals("100", all.getFirst().getPropertyValue());
    }

    @Test
    void keyWithOnlyANodeRowIsAbsentFromLookupAndListing() {
        save(NODE_ONLY_KEY.key(), "8", NODE_1);

        assertTrue(repository.findByPropertyKey(NODE_ONLY_KEY.key()).isEmpty());
        assertTrue(repository.findAll().stream()
                .noneMatch(e -> NODE_ONLY_KEY.key().equals(e.getPropertyKey())));
        assertNull(findDto(NODE_ONLY_KEY.key()).getCurrentValue());
    }

    @Test
    void listingShowsTheSharedValueNextToNodeRows() {
        save(KEY.key(), "100", null);
        save(KEY.key(), "200", NODE_1);

        assertEquals("100", findDto(KEY.key()).getCurrentValue());
    }

    @Test
    void updateChangesTheSharedRowAndLeavesTheNodeRowUnchanged() {
        save(KEY.key(), "100", null);
        save(KEY.key(), "200", NODE_1);

        service.updateConfigurableProperty(KEY.key(), "150");

        assertEquals("150", valueOf(KEY.key(), null));
        assertEquals("200", valueOf(KEY.key(), NODE_1));
        assertEquals(2, rowCount(KEY.key()));
    }

    @Test
    void createWritesASharedRowAndLeavesTheNodeRowUnchanged() {
        save(KEY.key(), "200", NODE_1);

        service.updateConfigurableProperty(KEY.key(), "150");

        assertEquals("150", valueOf(KEY.key(), null));
        assertEquals("200", valueOf(KEY.key(), NODE_1));
        assertEquals(2, rowCount(KEY.key()));
    }

    private void save(String key, String value, String node) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(key);
        entity.setPropertyValue(value);
        entity.setHaNodeName(node);
        repository.save(entity);
    }

    private ConfigurablePropertyDto findDto(String key) {
        return service.getConfigurationProperties().stream()
                .filter(p -> key.equals(p.getPropertyName()))
                .findFirst()
                .orElseThrow();
    }

    private String valueOf(String key, String node) {
        var query = node == null
                ? "SELECT e.propertyValue FROM ConfigurationPropertyEntity e "
                        + "WHERE e.propertyKey = :key AND e.haNodeName IS NULL"
                : "SELECT e.propertyValue FROM ConfigurationPropertyEntity e "
                        + "WHERE e.propertyKey = :key AND e.haNodeName = :node";
        var typed = entityManager.createQuery(query, String.class).setParameter("key", key);
        if (node != null) {
            typed.setParameter("node", node);
        }
        return typed.getSingleResult();
    }

    private long rowCount(String key) {
        return entityManager.createQuery(
                        "SELECT COUNT(e) FROM ConfigurationPropertyEntity e WHERE e.propertyKey = :key", Long.class)
                .setParameter("key", key)
                .getSingleResult();
    }
}
