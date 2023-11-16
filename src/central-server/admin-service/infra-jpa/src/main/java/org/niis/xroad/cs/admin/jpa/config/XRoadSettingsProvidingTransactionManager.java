/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.admin.jpa.config;

import jakarta.persistence.EntityManagerFactory;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static java.lang.String.valueOf;

/**
 * Sets xroad.current_ha_node_name and xroad.user_name settings for each transaction.
 * Required for history table stored procedures.
 */
@Component("xRoadSettingsTransactionManager")
public class XRoadSettingsProvidingTransactionManager extends JpaTransactionManager {

    private final HAConfigStatus haConfigStatus;

    public XRoadSettingsProvidingTransactionManager(EntityManagerFactory emf, HAConfigStatus haConfigStatus) {
        super(emf);
        this.haConfigStatus = haConfigStatus;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        final String sql = "SELECT "
                + " set_config('xroad.current_ha_node_name', ?, true), "
                + " set_config('xroad.user_name', ?, true)";

        try (PreparedStatement stmt = ((JdbcTransactionObjectSupport) transaction).getConnectionHolder()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, haConfigStatus.getCurrentHaNodeName());
            stmt.setString(2, getCurrentUsername());
            stmt.execute();
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Unable to create transaction", e);
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Authentication is null if transaction was not due to authenticated user doing something -
        // e.g. authentication itself created transaction to load api keys from db
        String username = "unknown_user";
        if (authentication != null) {
            // for PreAuthenticatedAuthenticationToken (session cookie auth) and
            // UsernamePasswordAuthenticationToken (api key auth), principal
            // is simply a String that contains what we want
            username = valueOf(authentication.getPrincipal());
        }
        return username;
    }

}
