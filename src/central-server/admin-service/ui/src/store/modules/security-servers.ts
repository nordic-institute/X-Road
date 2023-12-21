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
import axios from 'axios';
import {
  PagedSecurityServers,
  PagingMetadata,
  SecurityServer,
  SecurityServerAddress,
} from '@/openapi-types';
import { defineStore } from 'pinia';
import { DataQuery } from '@/ui-types';

export interface State {
  currentSecurityServerLoading: boolean;
  currentSecurityServer: SecurityServer | null;
  securityServers: SecurityServer[];
  securityServerPagingOptions: PagingMetadata;
}

export const useSecurityServer = defineStore('securityServer', {
  state: (): State => ({
    currentSecurityServerLoading: false,
    currentSecurityServer: null,
    securityServers: [],
    securityServerPagingOptions: {
      total_items: 0,
      items: 0,
      limit: 25,
      offset: 0,
    },
  }),
  persist: true,
  actions: {
    async find(dataOptions: DataQuery) {
      const offset = dataOptions?.page == null ? 0 : dataOptions.page - 1;
      const searchUrlParams = {
        offset: offset,
        limit: dataOptions.itemsPerPage,
        sort: dataOptions.sortBy,
        desc: dataOptions.sortOrder === 'desc',
        q: dataOptions.search,
      };

      return axios
        .get<PagedSecurityServers>('/security-servers/', {
          params: searchUrlParams,
        })
        .then((resp) => {
          this.securityServers = resp.data.items || [];
          this.securityServerPagingOptions = resp.data.paging_metadata;
        });
    },
    async loadById(securityServerId: string) {
      this.currentSecurityServerLoading = true;
      return axios
        .get<SecurityServer>(`/security-servers/${securityServerId}`)
        .then((resp) => {
          this.currentSecurityServer = resp.data;
        })
        .finally(() => (this.currentSecurityServerLoading = false));
    },
    async updateAddress(securityServerId: string, address: string) {
      this.currentSecurityServerLoading = true;
      const securityServerAddress: SecurityServerAddress = {
        server_address: address,
      };
      return axios
        .patch<SecurityServer>(
          `/security-servers/${securityServerId}`,
          securityServerAddress,
        )
        .then((resp) => {
          this.currentSecurityServer = resp.data;
        })
        .finally(() => (this.currentSecurityServerLoading = false));
    },
    async delete(securityServerId: string) {
      return axios.delete(`/security-servers/${securityServerId}`).then(() => {
        this.currentSecurityServer = null;
      });
    },
  },
});
