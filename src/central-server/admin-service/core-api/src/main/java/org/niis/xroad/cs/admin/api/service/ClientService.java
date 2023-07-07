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
package org.niis.xroad.cs.admin.api.service;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.paging.PageRequestDto;

import java.util.List;

/**
 * Service for searching {@link FlattenedSecurityServerClientView}s
 */
public interface ClientService {

    Page<FlattenedSecurityServerClientView> find(ClientService.SearchParameters params, PageRequestDto pageRequest);

    List<FlattenedSecurityServerClientView> find(ClientService.SearchParameters params);

    List<FlattenedSecurityServerClientView> findAll();

    /**
     * Parameters that defined which clients are returned.
     * All given parameters must match (e.g. memberClass = GOV, memberCode = 123 will not return a client
     * with memberClass = GOV, memberCode = 456). Null / undefined parameters are ignored.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    class SearchParameters {
        private String multifieldSearch;
        private String instanceSearch;
        private String memberNameSearch;
        private String memberClassSearch;
        private String memberCodeSearch;
        private String subsystemCodeSearch;
        private XRoadObjectType clientType;
        private Integer securityServerId;
        private String excludingGroup;

        /**
         * Return clients that contain given parameter in member name. Case insensitive.
         */
        public SearchParameters setMemberNameSearch(String memberNameSearchParam) {
            this.memberNameSearch = memberNameSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in member name, member class, member code or
         * subsystem code. Case insensitive.
         */
        public SearchParameters setMultifieldSearch(String multifieldSearchParam) {
            this.multifieldSearch = multifieldSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in instance identifier. Case insensitive.
         */
        public SearchParameters setInstanceSearch(String instanceSearchParam) {
            this.instanceSearch = instanceSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in member class. Case insensitive.
         */
        public SearchParameters setMemberClassSearch(String memberClassSearchParam) {
            this.memberClassSearch = memberClassSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in member code. Case insensitive.
         */
        public SearchParameters setMemberCodeSearch(String memberCodeSearchParam) {
            this.memberCodeSearch = memberCodeSearchParam;
            return this;
        }

        /**
         * Return clients that contain given parameter in subsystem code. Case insensitive.
         */
        public SearchParameters setSubsystemCodeSearch(String subsystemCodeSearchParam) {
            this.subsystemCodeSearch = subsystemCodeSearchParam;
            return this;
        }

        /**
         * Return clients of given XRoadObjectType (either MEMBER or SUBSYSTEM).
         */
        public SearchParameters setClientType(XRoadObjectType clientTypeParam) {
            this.clientType = clientTypeParam;
            return this;
        }

        /**
         * Return clients that are clients of given security server
         *
         * @param securityServerIdParam security server ID
         */
        public SearchParameters setSecurityServerId(Integer securityServerIdParam) {
            this.securityServerId = securityServerIdParam;
            return this;
        }

        /**
         * Return clients that are not clients of given global group
         *
         * @param excludingGroupParam global group id
         */
        public SearchParameters setExcludingGroupParam(String excludingGroupParam) {
            this.excludingGroup = excludingGroupParam;
            return this;
        }
    }
}
