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
package org.niis.xroad.signer.core.tokenmanager;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.KeyConfDeviceDaoImpl;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.mapper.TokenMapper;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds the current keys & certificates in XML.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenConf {
    private final ServerConfDatabaseCtx serverConfDatabaseCtx;
    private final KeyConfDeviceDaoImpl keyConfDeviceDao;
    private final TokenMapper tokenMapper;

    /**
     * Specialized exception instead of a generic exception for TokenConf errors.
     */
    public static class TokenConfException extends Exception {

        public TokenConfException(String message, Throwable cause) {
            super(message, cause);
        }

        public TokenConfException(String message) {
            super(message);
        }
    }

    /**
     * Saves the tokens to the XML file.
     *
     * @param loadedTokens the tokens to save
     * @throws Exception if an error occurs
     */
    synchronized void save(LoadedTokens loadedTokens) throws Exception {
        var entities = tokenMapper.toEntities(loadedTokens.tokens());
        var inMemoryTokenHashCode = entities.hashCode();
        if (loadedTokens.entitySetHashCode() != inMemoryTokenHashCode) {
            log.debug("Token configuration has changed, saving to DB.");
            serverConfDatabaseCtx.doInTransaction(session -> {
                keyConfDeviceDao.deleteAll(session);


                keyConfDeviceDao.saveTokens(session, entities);
                return null;
            });
        } else {
            log.debug("Token configuration has not changed, skipping save.");
        }
    }

    /**
     * Retrieves, <b>but does not load into memory</b> the tokens in the configuration file.
     *
     * @return
     */
    public LoadedTokens retrieveTokensFromDb() throws TokenConfException {
        try {
            return serverConfDatabaseCtx.doInTransaction(session -> {
                var deviceEntities = keyConfDeviceDao.findAll(session);
                return new LoadedTokens(tokenMapper.toTargets(deviceEntities), deviceEntities.hashCode());
            });
        } catch (Exception e) {
            throw new TokenConfException("Error while loading or validating key config", e);
        }
    }

    public boolean hasChanged(LoadedTokens loadedTokens) {
        try {
            return serverConfDatabaseCtx.doInTransaction(session -> {
                var deviceEntityIds = keyConfDeviceDao.findAllIds(serverConfDatabaseCtx.getSession());

                //Take the ids from the token list
                var tokenIds = loadedTokens.tokens().stream()
                        .map(Token::getInternalId)
                        .collect(Collectors.toSet());

                //Compare the two sets
                return !deviceEntityIds.equals(tokenIds);
            });
        } catch (Exception e) {
            log.error("Error while checking if key config has changed", e);
            return false;
        }
    }

    public record LoadedTokens(
            Set<Token> tokens,
            /*
             * Hash code of the token entities in the database
             */
            int entitySetHashCode) {
    }
}
