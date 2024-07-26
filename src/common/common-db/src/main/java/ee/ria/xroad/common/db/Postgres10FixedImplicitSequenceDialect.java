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
package ee.ria.xroad.common.db;

import ee.ria.xroad.common.util.NoCoverage;

import org.hibernate.dialect.PostgreSQLDialect;

/**
 * Postgres10FixedSequenceDialect is needed to make <u><b><code>hibernate.ddl-auto = validate</code></b></u> work.
 *
 * Otherwise {@link org.hibernate.dialect.PostgreSQLDialect#getQuerySequencesString()} gets called which returns
 * results from <u><b><code>information_schema.sequences</code></b></u> view. Since PostgreSQL 10 sequences can be
 * defined by <b><code>GENERATED BY DEFAULT AS IDENTITY</code></b> which is finally following SQL standard but these
 * sequences are not returned by <u><b><code>information_schema.sequences</code></b></u> view but
 * <u><b><code>pg_catalog.pg_sequences</code></b></u> view.
 */
@NoCoverage
public class Postgres10FixedImplicitSequenceDialect extends PostgreSQLDialect {

    @SuppressWarnings("checkstyle:MagicNumber")
    @Override
    public String getQuerySequencesString() {
        if (getVersion().getDatabaseMajorVersion() >= 10) {
            return String.join(" ",
                    "SELECT",
                    "    current_catalog AS sequence_catalog,",
                    "    schemaname AS sequence_schema,",
                    "    sequencename AS sequence_name,",
                    "    start_value,",
                    "    min_value AS minimum_value,",
                    "    max_value AS maximum_value,",
                    "    increment_by AS increment",
                    "FROM pg_catalog.pg_sequences",
                    "WHERE schemaname NOT IN (SELECT schema_name",
                    "                         FROM INFORMATION_SCHEMA.schemata",
                    "                         WHERE schema_name <> 'public' AND",
                    "                               schema_owner = 'postgres' AND",
                    "                               schema_name IS NOT NULL)");
        } else {
            return super.getQuerySequencesString();
        }
    }

}
