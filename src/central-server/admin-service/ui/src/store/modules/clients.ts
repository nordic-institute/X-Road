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
import axios, { AxiosRequestConfig } from 'axios';
import { Client, PagedClients, PagingMetadata } from '@/openapi-types';
import { defineStore } from 'pinia';
import { DataQuery, PagingOptions } from '@/ui-types';

export interface State {
  clients: Client[];
  pagingOptions: PagingMetadata;
}

export interface PagingParams {
  query: string | null;
  page: number;
  itemPerPage: number;
}

export const useClient = defineStore('client', {
  state: (): State => ({
    clients: [],
    pagingOptions: {
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

      const params: unknown = {
        limit: dataOptions.itemsPerPage,
        offset: offset,
        sort: dataOptions.sortBy,
        desc: dataOptions.sortOrder === 'desc',
        client_type: 'MEMBER',
        q: dataOptions.search,
      };
      const axiosParams: AxiosRequestConfig = { params };

      return axios.get<PagedClients>('/clients/', axiosParams).then((resp) => {
        this.clients = resp.data.clients || [];
        this.pagingOptions = resp.data.paging_metadata;
      });
    },
    getByExcludingGroup(
      groupId: string,
      query: string | null,
      dataOptions: PagingOptions,
    ) {
      const offset = dataOptions?.page == null ? 0 : dataOptions.page - 1;
      const params: unknown = {
        excluding_group: groupId,
        offset,
        limit: dataOptions.itemsPerPage,
        q: query,
      };
      const axiosParams: AxiosRequestConfig = { params };

      return axios
        .get<PagedClients>('/clients', axiosParams)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
    getByClientType(
      clientType: string,
      query: string | null,
      dataOptions: DataQuery,
    ) {
      const offset = dataOptions?.page == null ? 0 : dataOptions.page - 1;
      const params: unknown = {
        client_type: clientType,
        offset,
        limit: dataOptions.itemsPerPage,
        sort: dataOptions.sortBy,
        desc: dataOptions.sortOrder === 'desc',
        q: query,
      };
      const axiosParams: AxiosRequestConfig = { params };

      return axios
        .get<PagedClients>('/clients/', axiosParams)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
    getBySecurityServerId(serverId: string) {
      return axios
        .get<Client[]>(`/security-servers/${serverId}/clients`)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
  },
});
