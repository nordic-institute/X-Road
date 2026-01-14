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

package org.niis.xroad.test.framework.core.asserts;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Utility class for JSON Path operations.
 */
public final class JsonPathUtils {

    private JsonPathUtils() {
        // Utility class
    }

    /**
     * Evaluates a JSON path expression against various JSON input types.
     *
     * @param json the JSON input (String, byte[], File, URL, InputStream, or Object)
     * @param jsonPath the JSON path expression
     * @param predicates optional predicates for filtering
     * @param <T> the return type
     * @return the evaluated result
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("unchecked")
    public static <T> T evaluate(Object json, String jsonPath, Predicate... predicates) throws IOException {
        if (json instanceof String) {
            return (T) JsonPath.read((String) json, jsonPath, predicates);
        } else if (json instanceof byte[]) {
            return (T) JsonPath.read(new ByteArrayInputStream((byte[]) json), jsonPath, predicates);
        } else if (json instanceof File) {
            return (T) JsonPath.read((File) json, jsonPath, predicates);
        } else if (json instanceof URL) {
            return (T) JsonPath.read(((URL) json).openStream(), jsonPath, predicates);
        } else if (json instanceof InputStream) {
            return (T) JsonPath.read((InputStream) json, jsonPath, predicates);
        } else {
            return (T) JsonPath.read(json, jsonPath, predicates);
        }
    }
}
