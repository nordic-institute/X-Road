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
import {
  ApprovedCertificationService,
  ApprovedCertificationServiceListItem,
  CertificateAuthority,
  CertificateDetails,
  CertificationServiceFileAndSettings,
  CertificationServiceSettings,
  OcspResponder,
  TimestampingService,
} from '@/openapi-types';
import { defineStore } from 'pinia';
import axios from 'axios';

export interface CertificationServiceStoreState {
  certificationServices: ApprovedCertificationServiceListItem[];
  currentCertificationService: ApprovedCertificationService | null;
}

export const useCertificationService = defineStore('certificationService', {
  state: (): CertificationServiceStoreState => ({
    certificationServices: [],
    currentCertificationService: null,
  }),
  persist: true,
  actions: {
    fetchAll() {
      return axios
        .get<ApprovedCertificationServiceListItem[]>('/certification-services')
        .then((resp) => (this.certificationServices = resp.data));
    },
    loadById(certificationServiceId: number) {
      return axios
        .get<ApprovedCertificationService>(
          `/certification-services/${certificationServiceId}`,
        )
        .then((resp) => {
          this.currentCertificationService = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    deleteById(certificationServiceId: number) {
      return axios.delete(`/certification-services/${certificationServiceId}`);
    },
    add(newCas: CertificationServiceFileAndSettings) {
      const formData = new FormData();
      formData.append(
        'certificate_profile_info',
        newCas.certificate_profile_info || '',
      );
      formData.append('tls_auth', newCas.tls_auth || '');
      formData.append('certificate', newCas.certificate);
      return axios
        .post('/certification-services', formData)
        .finally(() => this.fetchAll());
    },
    update(
      certificationServiceId: number,
      settings: CertificationServiceSettings,
    ) {
      return axios
        .patch<ApprovedCertificationService>(
          `/certification-services/${certificationServiceId}`,
          settings,
        )
        .then((resp) => {
          this.currentCertificationService = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    getCertificate(certificationServiceId: number) {
      return axios.get<CertificateDetails>(
        `/certification-services/${certificationServiceId}/certificate`,
      );
    },
  },
});

export interface OcspResponderStoreState {
  currentCa: ApprovedCertificationService | CertificateAuthority | null;
  currentOcspResponders: OcspResponder[];
}

export const useOcspResponderService = defineStore('ocspResponderService', {
  state: (): OcspResponderStoreState => ({
    currentCa: null,
    currentOcspResponders: [],
  }),
  persist: true,
  getters: {
    getCurrentCaOcspRespondersPath(): string {
      if ((this.currentCa as ApprovedCertificationService).name) {
        return `/certification-services/${this.currentCa?.id}/ocsp-responders`;
      } else {
        return `/intermediate-cas/${this.currentCa?.id}/ocsp-responders`;
      }
    },
  },
  actions: {
    loadByCa(ca: ApprovedCertificationService | CertificateAuthority) {
      this.currentCa = ca;
      this.fetchOcspResponders();
    },
    fetchOcspResponders() {
      return axios
        .get<OcspResponder[]>(this.getCurrentCaOcspRespondersPath)
        .then((resp) => (this.currentOcspResponders = resp.data));
    },
    addOcspResponder(url: string, certificate: File | null) {
      const formData = new FormData();
      formData.append('url', url);
      if(certificate){
        formData.append('certificate', certificate);
      }

      return axios
        .post(this.getCurrentCaOcspRespondersPath, formData)
        .finally(() => this.fetchOcspResponders());
    },
    updateOcspResponder(id: number, url: string, certificate: File | null) {
      const formData = new FormData();
      formData.append('url', url);
      if (certificate) {
        formData.append('certificate', certificate);
      }
      return axios.patch(`/ocsp-responders/${id}/`, formData);
    },
    deleteOcspResponder(id: number) {
      return axios.delete(`/ocsp-responders/${id}`);
    },
    getOcspResponderCertificate(id: number) {
      return axios.get<CertificateDetails>(
        `/ocsp-responders/${id}/certificate`,
      );
    },
  },
});

export interface IntermediateCasStoreState {
  currentCs: ApprovedCertificationService | null;
  currentIntermediateCas: CertificateAuthority[];
  currentSelectedIntermediateCa: CertificateAuthority | null;
}

export const useIntermediateCasService = defineStore('intermediateCasService', {
  state: (): IntermediateCasStoreState => ({
    currentCs: null,
    currentIntermediateCas: [],
    currentSelectedIntermediateCa: null,
  }),
  persist: true,
  actions: {
    loadByCs(cs: ApprovedCertificationService) {
      this.currentCs = cs;
      this.fetchIntermediateCas();
    },
    loadById(intermediateCaId: number) {
      return this.getIntermediateCa(intermediateCaId)
        .then((resp) => {
          this.currentSelectedIntermediateCa = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    fetchIntermediateCas() {
      if (!this.currentCs) return;

      return axios
        .get<CertificateAuthority[]>(
          `/certification-services/${this.currentCs.id}/intermediate-cas`,
        )
        .then((resp) => (this.currentIntermediateCas = resp.data));
    },
    getIntermediateCa(id: number) {
      return axios.get<CertificateAuthority>(`/intermediate-cas/${id}`);
    },
    addIntermediateCa(certificate: File) {
      if (!this.currentCs) {
        throw new Error('CA not selected');
      }
      const formData = new FormData();
      formData.append('certificate', certificate);
      return axios
        .post(
          `/certification-services/${this.currentCs.id}/intermediate-cas`,
          formData,
        )
        .finally(() => this.fetchIntermediateCas());
    },
    deleteIntermediateCa(id: number) {
      return axios.delete(`/intermediate-cas/${id}`);
    },
  },
});

export interface TimestampingServiceStoreState {
  timestampingServices: TimestampingService[];
}

export const useTimestampingServicesStore = defineStore(
  'timestampingServices',
  {
    state: (): TimestampingServicesStoreState => ({
      timestampingServices: [],
    }),
    persist: true,
    actions: {
      fetchTimestampingServices() {
        return axios
          .get<TimestampingService[]>('/timestamping-services')
          .then((resp) => (this.timestampingServices = resp.data));
      },
      delete(id: number) {
        return axios
          .delete(`/timestamping-services/${id}`)
          .finally(() => this.fetchTimestampingServices());
      },
      addTimestampingService(url: string, certificate: File) {
        const formData = new FormData();
        formData.append('url', url || '');
        formData.append('certificate', certificate);
        return axios
          .post('/timestamping-services', formData)
          .finally(() => this.fetchTimestampingServices());
      },
      updateTimestampingService(
        id: number,
        url: string,
        certificate: File | null,
      ) {
        const formData = new FormData();
        formData.append('url', url || '');
        if (certificate) {
          formData.append('certificate', certificate);
        }
        return axios
          .patch(`/timestamping-services/${id}`, formData)
          .finally(() => this.fetchTimestampingServices());
      },
    },
  },
);
