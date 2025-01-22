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
package ee.ria.xroad.common.conf.serverconf;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.db.TransactionCallback;

import org.hibernate.Session;

/**
 * Server conf database context.
 */
public final class ServerConfDatabaseCtx {

    private static final DatabaseCtx CTX = new DatabaseCtx("serverconf");

    private ServerConfDatabaseCtx() {
    }

    /**
     * @return the database context instance
     */
    public static DatabaseCtx get() {
        return CTX;
    }

    /**
     * @return shortcut for a session
     */
    public static Session getSession() {
        return get().getSession();
    }

    /**
     * Executes the unit of work transactionally.
     * @param callback the unit of work callback
     * @param <T> the type of the result
     * @return the result of the callback
     * @throws Exception if an error occurs
     */
    public static <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        return CTX.doInTransaction(callback);
    }

}
