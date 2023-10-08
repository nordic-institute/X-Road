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

import com.nortal.test.testcontainers.TestContainerService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.niis.xroad.ss.test.ui.container.Port;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.Container;

import java.sql.Connection;
import java.sql.DriverManager;

@Service
@RequiredArgsConstructor
public class TestDatabaseService implements DisposableBean {

    private final TestContainerService testContainerService;

    private Connection messagelogConnection;
    private NamedParameterJdbcTemplate messagelogNamedParameterJdbcTemplate;

    @SneakyThrows
    public NamedParameterJdbcTemplate getMessagelogTemplate() {
        if (messagelogNamedParameterJdbcTemplate == null) {
            messagelogConnection = DriverManager.getConnection(getJdbcUrl("messagelog"),
                    getParamValue("messagelog.hibernate.connection.username"), getParamValue("messagelog.hibernate.connection.password"));
            final SingleConnectionDataSource dataSource = new SingleConnectionDataSource(messagelogConnection, true);
            messagelogNamedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        }
        return messagelogNamedParameterJdbcTemplate;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private String getJdbcUrl(String database) {
        return String.format("jdbc:postgresql://%s:%d/%s",
                testContainerService.getHost(),
                testContainerService.getMappedPort(Port.DB),
                database);
    }

    @SneakyThrows
    private String getParamValue(String param) {
        final Container.ExecResult result = testContainerService.getContainer().execInContainer("grep", param, "/etc/xroad/db.properties");
        return result.getStdout().split("=")[1].trim();
    }

    @Override
    public void destroy() throws Exception {
        if (messagelogConnection != null) {
            messagelogConnection.close();
        }
    }
}
