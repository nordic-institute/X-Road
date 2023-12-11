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

import * as api from '@/util/api';
import {
  CertificateDetails,
  ManagementServicesConfiguration,
  RegisterServiceProviderRequest,
  ServiceProviderId,
} from '@/openapi-types';
import { defineStore } from 'pinia';
import axios from "axios";
import { saveResponseAsFile } from "@/util/helpers";

interface ManagementServicesState {
  managementServicesConfiguration: ManagementServicesConfiguration;
}

export const useManagementServices = defineStore('managementServices', {
  state: (): ManagementServicesState => {
    return {
      managementServicesConfiguration: {
        wsdl_address: '',
        services_address: '',
        security_server_owners_global_group_code: '',
        security_server_id: '',
        service_provider_name: '',
        service_provider_id: '',
      },
    };
  },
  persist: true,
  actions: {
    async fetchManagementServicesConfiguration() {
      return api
        .get<ManagementServicesConfiguration>(
          '/management-services-configuration',
        )
        .then((resp) => {
          this.managementServicesConfiguration = resp.data;
        });
    },
    updateManagementServicesConfiguration(
      serviceProviderId: ServiceProviderId,
    ) {
      return api
        .patch<ManagementServicesConfiguration>(
          '/management-services-configuration',
          serviceProviderId,
        )
        .then((resp) => {
          this.managementServicesConfiguration = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    registerServiceProvider(securityServerId: RegisterServiceProviderRequest) {
      return api
        .post<ManagementServicesConfiguration>(
          '/management-services-configuration/register-provider',
          securityServerId,
        )
        .then((resp) => {
          this.managementServicesConfiguration = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    downloadCertificate() {
      return axios
        .get(`/management-services-configuration/download-certificate`, {
          responseType: 'blob',
        })
        .then((resp) => {
          saveResponseAsFile(resp, 'management-service.tar.gz');
        })
        .catch((error) => {
          throw error;
        });
    },
    uploadCertificate(certificate: File) {
      const formData = new FormData();
      formData.append('certificate', certificate);
      return axios
        .post(`/management-services-configuration/upload-certificate`, formData)
        .catch((error) => {
          throw error;
        });
    },
    generateKey() {
      return axios
        .post(`/management-services-configuration/certificate`, {})
        .catch((error) => {
          throw error;
        });
    },
    async generateCsr(distinguishedName: String) {
      return axios
        .post(`/management-services-configuration/generate-csr`, {name: distinguishedName}, {responseType: 'json'})
        .then((res) => {
          saveResponseAsFile(res, 'request.csr');
        })
        .catch((error) => {
          throw error;
        });
    },
    getCertificate() {
      return axios.get<CertificateDetails>(
        `/management-services-configuration/certificate`,
      );
    },
  },
});
