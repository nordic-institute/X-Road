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
import { ServiceDescription, ServiceDescriptionUpdate } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { sortServiceDescriptionServices } from '@/util/sorting';
import { ServiceTypeEnum } from '@/domain';

export interface ServicesState {
  expandedServiceDescriptions: string[];
  serviceDescriptions: ServiceDescription[];
  serviceDescription?: ServiceDescription;
}

export const useServiceDescriptions = defineStore('service-descriptions', {
  state: (): ServicesState => {
    return {
      serviceDescription: undefined,
      expandedServiceDescriptions: [],
      serviceDescriptions: [],
    };
  },
  persist: {
    pick: ['serviceDescriptions'],
  },
  getters: {
    descExpanded: (state) => (id: string) => {
      return state.expandedServiceDescriptions.includes(id);
    },
  },

  actions: {
    expandDesc(id: string) {
      this.expandedServiceDescriptions.push(id);
      this.expandedServiceDescriptions = [
        ...new Set(this.expandedServiceDescriptions),
      ];
    },

    hideDesc(id: string) {
      this.expandedServiceDescriptions =
        this.expandedServiceDescriptions.filter((item) => item !== id);
    },

    async fetchServiceDescriptions(
      clientId: string,
      sort = true,
    ): Promise<ServiceDescription[]> {
      const encodedId = encodePathParameter(clientId);
      return api
        .get<ServiceDescription[]>(`/clients/${encodedId}/service-descriptions`)
        .then((res) => {
          const serviceDescriptions: ServiceDescription[] = res.data;
          this.serviceDescriptions = sort
            ? serviceDescriptions.map(sortServiceDescriptionServices)
            : serviceDescriptions;
          return this.serviceDescriptions;
        });
    },
    async fetchServiceDescription(
      serviceDescriptionId: string,
    ): Promise<ServiceDescription> {
      const encodedId = encodePathParameter(serviceDescriptionId);
      return api
        .get<ServiceDescription>(`/service-descriptions/${encodedId}`)
        .then((res) => res.data)
        .then((data) => (this.serviceDescription = data));
    },
    async refreshServiceDescription(
      serviceDescriptionId: string,
      ignoreWarnings = false,
    ) {
      const encodedId = encodePathParameter(serviceDescriptionId);
      return api.put(`/service-descriptions/${encodedId}/refresh`, {
        ignore_warnings: ignoreWarnings,
      });
    },
    async enableServiceDescription(serviceDescriptionId: string) {
      const encodedId = encodePathParameter(serviceDescriptionId);
      return api.put(`/service-descriptions/${encodedId}/enable`, {});
    },
    async disableServiceDescription(
      serviceDescriptionId: string,
      notice: string,
    ) {
      const encodedId = encodePathParameter(serviceDescriptionId);
      return api.put(`/service-descriptions/${encodedId}/disable`, {
        disabled_notice: notice,
      });
    },
    async saveWsdl(clientId: string, url: string, ignoreWarnings = false) {
      const encodedId = encodePathParameter(clientId);
      return api.post(`/clients/${encodedId}/service-descriptions`, {
        url,
        type: ServiceTypeEnum.WSDL,
        ignore_warnings: ignoreWarnings,
      });
    },
    async saveRest(
      clientId: string,
      url: string,
      serviceCode: string,
      type: ServiceTypeEnum,
      ignoreWarnings = false,
    ) {
      const encodedId = encodePathParameter(clientId);
      return api.post(`/clients/${encodedId}/service-descriptions`, {
        url,
        rest_service_code: serviceCode,
        type: type,
        ignore_warnings: ignoreWarnings,
      });
    },
    async updateServiceDescription(
      serviceDescriptionId: string,
      update: ServiceDescriptionUpdate,
    ) {
      const encodedId = encodePathParameter(serviceDescriptionId);
      return api.patch(`/service-descriptions/${encodedId}`, update);
    },
    async deleterServiceDescription(serviceDescriptionId: string) {
      const encodedId = encodePathParameter(serviceDescriptionId);
      return api.remove(`/service-descriptions/${encodedId}`);
    },
  },
});
