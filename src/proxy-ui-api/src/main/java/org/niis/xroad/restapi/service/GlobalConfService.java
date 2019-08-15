/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * global configuration service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class GlobalConfService {

    /**
     * get member name
     */
    public String getMemberName(ClientId identifier) {
        return GlobalConf.getMemberName(identifier);
    }

    /**
     * get global members
     */
    public List<MemberInfo> getGlobalMembers(String... instanceIdentifiers) {
        return GlobalConf.getMembers(instanceIdentifiers);
    }

    /**
     * @param instanceIdentifiers the optional instance identifiers
     * @return member classes for given instance or all member classes if
     * no instance identifiers are specified
     */
    public Set<String> getMemberClasses(String...instanceIdentifiers) {
        return GlobalConf.getMemberClasses(instanceIdentifiers);
    }

    /**
     * @return the instance identifier for this configuration source
     */
    public String getInstanceIdentifier() {
        return GlobalConf.getInstanceIdentifier();
    }

    /**
     * @return member classes for current instance
     */
    public Set<String> getMemberClassesForThisInstance() {
        return GlobalConf.getMemberClasses(getInstanceIdentifier());
    }
}
