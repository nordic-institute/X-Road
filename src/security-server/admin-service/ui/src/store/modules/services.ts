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

import { defineStore } from 'pinia';
import {
  Endpoint,
  Service,
  ServiceClient,
  ServiceDescription,
} from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { sortServiceDescriptionServices } from '@/util/sorting';

export interface ServicesState {
  expandedServiceDescriptions: string[];
  service: Service;
  serviceClients: ServiceClient[];
  serviceDescriptions: ServiceDescription[];
}

export const useServices = defineStore('services', {
  state: (): ServicesState => {
    return {
      expandedServiceDescriptions: [],
      service: {
        id: '',
        service_code: '',
        timeout: 0,
        ssl_auth: undefined,
        url: '',
      },
      serviceClients: [],
      serviceDescriptions: [],
    };
  },
  persist: {
    paths: ['service', 'serviceClients', 'serviceDescriptions'],
  },
  getters: {
    descExpanded: (state) => (id: string) => {
      return state.expandedServiceDescriptions.includes(id);
    },
    endpoints: (state) => {
      if (!state.service.endpoints) {
        return [];
      }
      return state.service.endpoints.filter(
        (endpoint: Endpoint) =>
          !(endpoint.method === '*' && endpoint.path === '**'),
      );
    },
  },

  actions: {
    expandDesc(id: string) {
      const index = this.expandedServiceDescriptions.findIndex((element) => {
        return element === id;
      });

      if (index === -1) {
        this.expandedServiceDescriptions.push(id);
      }
    },

    hideDesc(id: string) {
      const index = this.expandedServiceDescriptions.findIndex((element) => {
        return element === id;
      });

      if (index >= 0) {
        this.expandedServiceDescriptions.splice(index, 1);
      }
    },

    setService(service: Service) {
      service.endpoints = sortEndpoints(service.endpoints);
      this.service = service;
    },

    setServiceClients(serviceClients: ServiceClient[]) {
      this.serviceClients = serviceClients;
    },

    fetchServiceDescriptions(clientId: string, sort = true) {
      return api
        .get<ServiceDescription[]>(
          `/clients/${encodePathParameter(clientId)}/service-descriptions`,
        )
        .then((res) => {
          const serviceDescriptions: ServiceDescription[] = res.data;
          this.serviceDescriptions = sort
            ? serviceDescriptions.map(sortServiceDescriptionServices)
            : serviceDescriptions;
        })
        .catch((error) => {
          throw error;
        });
    },
    deleteEndpoint(id: string) {
      return api
        .remove(`/endpoints/${encodePathParameter(id)}`)
        .then((res) => {
          this.service.endpoints?.forEach((item, index) => {
            if (item.id === id) {
              this.service.endpoints?.splice(index, 1);
            }
          });
        })
        .catch((error) => {
          throw error;
        });
    },
    updateEndpoint(endpoint: Endpoint) {
      if (!endpoint.id) {
        throw new Error('Unable to save endpoint: Endpoint id not defined!');
      }
      return api
        .patch<Endpoint>(
          `/endpoints/${encodePathParameter(endpoint.id)}`,
          endpoint,
        )
        .then((res) => {
          if (this.service.endpoints) {
            const endpointIndex = this.service.endpoints.findIndex(
              (e) => e.id === endpoint.id,
            );
            if (endpointIndex) {
              const endpoints = [...this.service.endpoints];
              endpoints[endpointIndex] = res.data;
              this.service.endpoints = sortEndpoints(endpoints);
            }
          }
        })
        .catch((error) => {
          throw error;
        });
    },
  },
});

function sortEndpoints(endpoints: Endpoint[] | undefined) {
  return endpoints?.sort((a: Endpoint, b: Endpoint) => {
    const sortByGenerated =
      a.generated === b.generated ? 0 : a.generated ? -1 : 1;
    const sortByPathSlashCount =
      a.path.split('/').length - b.path.split('/').length;
    const sortByPathLength = a.path.length - b.path.length;
    return sortByGenerated || sortByPathSlashCount || sortByPathLength;
  });
}
