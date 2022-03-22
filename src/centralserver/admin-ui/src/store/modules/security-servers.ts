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
} from '@/openapi-types';
import { defineStore } from 'pinia';

function sortField2SortParam(field: string): string {
  let searchParam;
  switch (field) {
    case 'serverCode':
      searchParam = 'server_code';
      break;
    case 'serverOwnerName':
      searchParam = 'owner_name';
      break;
    case 'serverOwnerCode':
      searchParam = 'owner_name';
      break;
    case 'serverOwnerClass':
      searchParam = 'owner_name';
      break;
    default:
      searchParam = '';
  }
  return searchParam;
}

export interface State {
  securityServers: SecurityServer[];
  securityServerPagingOptions: PagingMetadata;
}

export const useSecurityServerStore = defineStore('securityServer', {
  state: (): State => ({
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
    async find(findOptions: {
      page: number;
      itemsPerPage: number;
      sortBy: string[];
      sortDesc: boolean[];
      q: string;
    }) {
      const searchParams = {
        offset: findOptions.page - 1,
        limit: findOptions.itemsPerPage,
        sort: sortField2SortParam(findOptions.sortBy[0]),
        desc: findOptions.sortDesc[0],
        q: findOptions.q,
      };

      return axios
        .get<PagedSecurityServers>('/security-servers/', {
          params: searchParams,
        })
        .then((resp) => {
          this.securityServers = resp.data.clients || [];
          this.securityServerPagingOptions = resp.data.paging_metadata;
        });
    },
  },
});
