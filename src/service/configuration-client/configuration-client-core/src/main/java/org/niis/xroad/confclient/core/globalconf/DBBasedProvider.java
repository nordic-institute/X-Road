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

package org.niis.xroad.confclient.core.globalconf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class DBBasedProvider implements ConfigurationAnchorProvider {

    private static final String SELECT_SQL = "SELECT content FROM configuration_client WHERE name = 'configuration-anchor'";
    private static final String INSERT_SQL = "INSERT INTO configuration_client (name, content) VALUES ('configuration-anchor', ?) "
            + " ON CONFLICT (name) DO UPDATE SET content = EXCLUDED.content";

    private final DataSource dataSource;

    @Override
    public Optional<byte[]> get() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(SELECT_SQL);
            if (resultSet.next()) {
                return Optional.of(resultSet.getString("content").getBytes(StandardCharsets.UTF_8));
            }
        }

        return Optional.empty();
    }

    @Override
    public void save(byte[] content) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            stmt.setString(1, new String(content));
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error saving configuration", e);
        }
    }

    @Override
    public boolean isAnchorPresent() {
        try {
            return get().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String source() {
        return "DB";
    }
}
