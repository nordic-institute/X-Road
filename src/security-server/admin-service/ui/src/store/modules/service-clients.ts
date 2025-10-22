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
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { ServiceClient, AccessRights, AccessRight } from '@/openapi-types';
import { sortAccessRightsByServiceCode } from '@/util/sorting';
import { AxiosRequestConfig } from 'axios';

export const useServiceClients = defineStore('service-clients', {
  state: () => {
    return {};
  },
  getters: {},

  actions: {
    async fetchServiceClients(clientId: string) {
      const encodedId = encodePathParameter(clientId);
      return api
        .get<ServiceClient[]>(`/clients/${encodedId}/service-clients`)
        .then((response) => response.data);
    },

    async fetchServiceClient(clientId: string, serviceClientId: string) {
      const encodedId = encodePathParameter(clientId);
      const encodedServiceClientId = encodePathParameter(serviceClientId);
      return api
        .get<ServiceClient>(
          `/clients/${encodedId}/service-clients/${encodedServiceClientId}`,
        )
        .then((response) => response.data);
    },

    async fetchCandidates(
      clientId: string,
      params: Record<string, string> | undefined = undefined,
    ): Promise<ServiceClient[]> {
      const encodedId = encodePathParameter(clientId);
      const config = {} as AxiosRequestConfig;
      if (params) {
        config.params = params;
      }
      return api
        .get<
          ServiceClient[]
        >(`/clients/${encodedId}/service-client-candidates`, config)
        .then((response) => response.data);
    },

    async saveAccessRights(
      clientId: string,
      selectedCandidateId: string,
      accessRights: AccessRights,
    ) {
      const encodedId = encodePathParameter(clientId);
      const candidateId = encodePathParameter(selectedCandidateId);
      return api.post(
        `/clients/${encodedId}/service-clients/${candidateId}/access-rights`,
        accessRights,
      );
    },

    async fetchAccessRights(
      clientId: string,
      serviceClientId: string,
      sort = true,
    ) {
      const encodedId = encodePathParameter(clientId);
      const encodedServiceClientId = encodePathParameter(serviceClientId);
      return api
        .get<
          AccessRight[]
        >(`/clients/${encodedId}/service-clients/${encodedServiceClientId}/access-rights`)
        .then((response) =>
          sort ? sortAccessRightsByServiceCode(response.data) : response.data,
        );
    },

    async removeAccessRights(
      clientId: string,
      serviceClientId: string,
      serviceCodes: string[],
    ) {
      const encodedId = encodePathParameter(clientId);
      const encodedServiceClientId = encodePathParameter(serviceClientId);
      return api.post(
        `/clients/${encodedId}/service-clients/${encodedServiceClientId}/access-rights/delete`,
        { items: serviceCodes.map((code) => ({ service_code: code })) },
      );
    },
  },
});
