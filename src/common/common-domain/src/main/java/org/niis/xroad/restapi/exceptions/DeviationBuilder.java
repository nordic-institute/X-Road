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
package org.niis.xroad.restapi.exceptions;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public interface DeviationBuilder<D extends Deviation> {

    String code();

    /**
     * Get a deviation object with the given metadata items.
     * @param metadataItems Metadata items to be used in the deviation; values will be converted to Strings,
     *                      and only non-null values will be considered.
     * @return deviation object
     */
    D build(Object... metadataItems);

    /**
     * Get a deviation object with the given metadata items.
     * @param metadataItems Metadata items to be used in the deviation; values will be converted to Strings,
     *                      and only non-null values will be considered.
     * @return deviation object
     */
    D build(List<String> metadataItems);


    interface ErrorDeviationBuilder extends DeviationBuilder<ErrorDeviation> {
        @Override
        default ErrorDeviation build(Object... metadataItems) {
            var metadataList = (metadataItems == null ? Stream.of() : Stream.of(metadataItems))
                    .filter(Objects::nonNull)
                    .map(Objects::toString)
                    .toList();
            return build(metadataList);
        }

        @Override
        default ErrorDeviation build(List<String> metadataItems) {
            var filtered = metadataItems.stream()
                    .filter(Objects::nonNull)
                    .toList();
            return new ErrorDeviation(code().toLowerCase(), filtered);
        }
    }

    String TRANSLATABLE_PREFIX = "tr.";

    /**
     * Get a list of metadata items with the given code and optional metadata/arguments items.
     * @param code code
     * @param metadataItems metadata items
     * @return list of metadata items
     */
    static List<String> trMetadata(String code, Object... metadataItems) {
        var metadata = new LinkedList<String>();
        metadata.add(TRANSLATABLE_PREFIX + code);

        Stream.of(metadataItems)
                .map(Objects::toString)
                .forEach(metadata::add);

        return List.copyOf(metadata);
    }
}
