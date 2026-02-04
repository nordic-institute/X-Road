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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TestDatabasePropertyService {
    private final TestDatabaseService testDatabaseService;

    public Optional<String> getProperty(String key) {
        return getProperty(key, null);
    }

    public Optional<String> getProperty(String key, String scope) {
        var sql = "SELECT property_value FROM configuration_properties WHERE property_key = :key AND scope "
                + (scope == null ? "IS NULL" : "= :scope");

        var params = new MapSqlParameterSource("key", key);
        if (scope != null) {
            params.addValue("scope", scope);
        }

        try {
            return Optional.ofNullable(testDatabaseService.getServerconfTemplate()
                    .queryForObject(sql, params, String.class));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void putProperty(String key, String value) {
        putProperty(key, value, null);
    }

    public void putProperty(String key, String value, String scope) {
        String sql;
        if (scope == null) {
            sql = "INSERT INTO configuration_properties (property_key, property_value, scope) "
                    + "VALUES (:key, :value, NULL) "
                    + "ON CONFLICT (property_key) WHERE scope IS NULL "
                    + "DO UPDATE SET property_value = EXCLUDED.property_value";
        } else {
            sql = "INSERT INTO configuration_properties (property_key, property_value, scope) "
                    + "VALUES (:key, :value, :scope) "
                    + "ON CONFLICT (property_key, scope) WHERE scope IS NOT NULL "
                    + "DO UPDATE SET property_value = EXCLUDED.property_value";
        }

        var params = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("value", value)
                .addValue("scope", scope);

        testDatabaseService.getServerconfTemplate().update(sql, params);
    }
}
