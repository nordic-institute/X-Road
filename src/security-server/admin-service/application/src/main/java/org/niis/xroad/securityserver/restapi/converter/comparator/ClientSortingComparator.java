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
package org.niis.xroad.securityserver.restapi.converter.comparator;

import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Comparator for comparing Clients for sorting purposes.
 */
@Component
public class ClientSortingComparator implements Comparator<ClientDto> {

    /**
     * Compare Client objects using member name as the primary sort key, and client id
     * as the secondary sort key.
     * @param c1
     * @param c2
     * @return
     */
    @Override
    public int compare(ClientDto c1, ClientDto c2) {
        if (c1.getMemberName() == null && c2.getMemberName() == null) {
            return c1.getId().compareToIgnoreCase(c2.getId());
        } else if (c1.getMemberName() == null) {
            return 1;
        } else if (c2.getMemberName() == null) {
            return -1;
        }
        int compareTo = c1.getMemberName().compareToIgnoreCase(c2.getMemberName());
        if (compareTo == 0) {
            return c1.getId().compareToIgnoreCase(c2.getId());
        }
        return compareTo;
    }
}
