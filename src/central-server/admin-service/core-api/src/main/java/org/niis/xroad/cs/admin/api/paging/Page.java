/**
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
package org.niis.xroad.cs.admin.api.paging;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Page<T> extends Iterable<T>, Supplier<Stream<T>> {
    /**
     * Returns the page content as {@link List}.
     */
    List<T> getContent();

    /**
     * Returns the number of the current {@link Page}. Is always non-negative.
     *
     * @return the number of the current {@link Page}.
     */
    int getNumber();

    /**
     * Returns the size of the {@link Page}.
     *
     * @return the size of the {@link Page}.
     */
    int getSize();

    /**
     * Returns the number of elements currently on this {@link Page}.
     *
     * @return the number of elements currently on this {@link Page}.
     */
    int getNumberOfElements();

    /**
     * Returns the number of total pages.
     *
     * @return the number of total pages
     */
    int getTotalPages();

    /**
     * Returns the total amount of elements.
     *
     * @return the total amount of elements
     */
    long getTotalElements();

    /**
     * Returns a new {@link Page} with the content of the current one mapped by the given {@link Function}.
     *
     * @param converter must not be {@literal null}.
     * @return a new {@link Page} with the content of the current one mapped by the given {@link Function}.
     */
    <U> Page<U> map(Function<? super T, ? extends U> converter);

    default Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
