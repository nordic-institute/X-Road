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

import { defineStore } from 'pinia';
import { Endpoint, Service, ServiceClient, ServiceUpdate, ServiceClients } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export interface ServicesState {
  service?: Service;
  serviceClients: ServiceClient[];
}

function serviceBaseUrl(serviceId: string, appendPath = '') {
  const encodedId = encodePathParameter(serviceId);
  return `/services/${encodedId}` + appendPath;
}

function endpointsBaseUrl(endpointId: string, appendPath = '') {
  const encodedId = encodePathParameter(endpointId);
  return `/endpoints/${encodedId}` + appendPath;
}

export const useServices = defineStore('services', {
  state: (): ServicesState => {
    return {
      service: undefined,
      serviceClients: [],
    };
  },
  persist: {
    pick: ['service', 'serviceClients', 'serviceDescriptions'],
  },
  getters: {
    endpoints: (state) => {
      if (!state.service?.endpoints) {
        return [];
      }
      return state.service.endpoints.filter((endpoint: Endpoint) => !(endpoint.method === '*' && endpoint.path === '**'));
    },
  },

  actions: {
    async fetchService(serviceId: string) {
      return api.get<Service>(serviceBaseUrl(serviceId)).then((res) => this.setService(res.data));
    },
    setService(service: Service) {
      service.endpoints = sortEndpoints(service.endpoints);
      this.service = service;
      return service;
    },
    async updateService(serviceId: string, serviceUpdate: ServiceUpdate) {
      return api.patch<Service>(serviceBaseUrl(serviceId), serviceUpdate).then((res) => this.setService(res.data));
    },
    async fetchServiceClients(serviceId: string) {
      return api.get<ServiceClient[]>(serviceBaseUrl(serviceId, '/service-clients')).then((res) => (this.serviceClients = res.data));
    },
    async addServiceClients(serviceId: string, serviceClients: ServiceClients) {
      return api.post(serviceBaseUrl(serviceId, '/service-clients'), serviceClients);
    },
    async removeServiceClients(serviceId: string, serviceClients: ServiceClients) {
      return api.post(serviceBaseUrl(serviceId, '/service-clients/delete'), serviceClients);
    },
    async addEndpoint(serviceId: string, endpoint: Endpoint) {
      return api.post(serviceBaseUrl(serviceId, '/endpoints'), endpoint);
    },
    async deleteEndpoint(id: string) {
      return api
        .remove(endpointsBaseUrl(id))
        .then(() => {
          this.service?.endpoints?.forEach((item, index) => {
            if (item.id === id) {
              this.service?.endpoints?.splice(index, 1);
            }
          });
        })
        .catch((error) => {
          throw error;
        });
    },
    async updateEndpoint(endpoint: Endpoint) {
      if (!endpoint.id) {
        throw new Error('Unable to save endpoint: Endpoint id not defined!');
      }
      return api.patch<Endpoint>(endpointsBaseUrl(endpoint.id), endpoint).then((res) => {
        if (this.service?.endpoints) {
          const endpointIndex = this.service.endpoints.findIndex((e) => e.id === endpoint.id);
          if (endpointIndex) {
            const endpoints = [...this.service.endpoints];
            endpoints[endpointIndex] = res.data;
            this.service.endpoints = sortEndpoints(endpoints);
          }
        }
      });
    },
    async removeEndpointServiceClients(id: string, toDelete: ServiceClients) {
      return api.post(endpointsBaseUrl(id, '/service-clients/delete'), toDelete);
    },
    async addEndpointServiceClients(id: string, toAdd: ServiceClients) {
      return api.post<ServiceClient[]>(endpointsBaseUrl(id, '/service-clients'), toAdd).then((res) => res.data);
    },
    async fetchEndpoint(id: string) {
      return api.get<Endpoint>(endpointsBaseUrl(id)).then((res) => res.data);
    },
    async fetchEndpointServiceClients(id: string) {
      return api.get<ServiceClient[]>(endpointsBaseUrl(id, '/service-clients')).then((res) => res.data);
    },
  },
});

function sortEndpoints(endpoints: Endpoint[] | undefined) {
  return endpoints?.sort((a: Endpoint, b: Endpoint) => {
    const sortByGenerated = a.generated === b.generated ? 0 : a.generated ? -1 : 1;
    const sortByPathSlashCount = a.path.split('/').length - b.path.split('/').length;
    const sortByPathLength = a.path.length - b.path.length;
    return sortByGenerated || sortByPathSlashCount || sortByPathLength;
  });
}
