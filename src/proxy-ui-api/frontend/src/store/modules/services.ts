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
import { Endpoint, Service, ServiceClient } from '@/openapi-types';

export interface ServicesState {
  expandedServiceDescriptions: string[];
  service: Service;
  serviceClients: ServiceClient[];
}

export const useServicesStore = defineStore('servicesStore', {
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
    };
  },
  getters: {
    descExpanded: (state) => (id: string) => {
      return state.expandedServiceDescriptions.includes(id);
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
      service.endpoints = service.endpoints?.sort(
        (a: Endpoint, b: Endpoint) => {
          const sortByGenerated =
            a.generated === b.generated ? 0 : a.generated ? -1 : 1;
          const sortByPathSlashCount =
            a.path.split('/').length - b.path.split('/').length;
          const sortByPathLength = a.path.length - b.path.length;
          return sortByGenerated || sortByPathSlashCount || sortByPathLength;
        },
      );
      this.service = service;
    },

    setServiceClients(serviceClients: ServiceClient[]) {
      this.serviceClients = serviceClients;
    },
  },
});
