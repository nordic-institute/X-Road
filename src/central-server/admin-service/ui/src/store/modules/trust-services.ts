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
  ApprovedDsTlsCertificationAuthority,
  ApprovedDsTlsCertificationAuthorityListItem,
  CertificateAuthority,
  CertificateDetails,
  CertificationServiceFileAndSettings,
  CertificationServiceSettings,
  CostType,
  DsTlsCertificationAuthorityFileAndSettings,
  DsTlsCertificationAuthoritySettings,
  DsTlsIntermediateCertificateAuthority,
  OcspResponder,
  TimestampingService,
  OcspResponderCertificateDetails,
} from '@/openapi-types';
import { defineStore } from 'pinia';
import axios from 'axios';
import { WithCurrentItem } from '@niis/shared-ui';

export interface CertificationServiceStoreState extends WithCurrentItem<ApprovedCertificationService> {
  certificationServices: ApprovedCertificationServiceListItem[];
}

export const useCertificationService = defineStore('certificationService', {
  state: (): CertificationServiceStoreState => ({
    current: undefined,
    certificationServices: [],
  }),
  persist: true,
  actions: {
    async fetchAll() {
      return axios
        .get<ApprovedCertificationServiceListItem[]>('/certification-services')
        .then((resp) => (this.certificationServices = resp.data));
    },
    async loadById(certificationServiceId: number) {
      this.loadingCurrent = true;
      this.current = undefined;
      return axios
        .get<ApprovedCertificationService>(`/certification-services/${certificationServiceId}`)
        .then((resp) => {
          this.current = resp.data;
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => (this.loadingCurrent = false));
    },
    async deleteById(certificationServiceId: number) {
      return axios.delete(`/certification-services/${certificationServiceId}`);
    },
    async add(newCas: CertificationServiceFileAndSettings) {
      const formData = new FormData();
      formData.append('certificate_profile_info', newCas.certificate_profile_info || '');
      formData.append('tls_auth', newCas.tls_auth || '');
      formData.append('certificate', newCas.certificate);
      formData.append('default_csr_format', newCas.default_csr_format as string);
      formData.append('acme_server_directory_url', newCas.acme_server_directory_url || '');
      formData.append('acme_server_ip_address', newCas.acme_server_ip_address || '');
      formData.append('authentication_certificate_profile_id', newCas.authentication_certificate_profile_id || '');
      formData.append('signing_certificate_profile_id', newCas.signing_certificate_profile_id || '');
      return axios.post('/certification-services', formData).finally(() => this.fetchAll());
    },
    async update(certificationServiceId: number, settings: CertificationServiceSettings) {
      return axios
        .patch<ApprovedCertificationService>(`/certification-services/${certificationServiceId}`, settings)
        .then((resp) => {
          this.current = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    async getCertificate(certificationServiceId: number) {
      return axios.get<CertificateDetails>(`/certification-services/${certificationServiceId}/certificate`);
    },
  },
});

export interface OcspResponderStoreState {
  currentCa: ApprovedCertificationService | CertificateAuthority | null;
  currentOcspResponders: OcspResponder[];
  loadingOcspResponders?: boolean;
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
    async loadByCa(ca: ApprovedCertificationService | CertificateAuthority) {
      this.currentCa = ca;
      return this.fetchOcspResponders();
    },
    async fetchOcspResponders() {
      this.loadingOcspResponders = true;
      return axios
        .get<OcspResponder[]>(this.getCurrentCaOcspRespondersPath)
        .then((resp) => (this.currentOcspResponders = resp.data))
        .finally(() => (this.loadingOcspResponders = false));
    },
    async addOcspResponder(url: string, costType: string, certificate: File | undefined) {
      const formData = new FormData();
      formData.append('url', url);
      formData.append('cost_type', costType);
      if (certificate) {
        formData.append('certificate', certificate);
      }

      return axios.post(this.getCurrentCaOcspRespondersPath, formData).finally(() => this.fetchOcspResponders());
    },
    async updateOcspResponder(id: number, url: string, costType: string, certificate: File | undefined) {
      const formData = new FormData();
      formData.append('url', url);
      formData.append('cost_type', costType);
      if (certificate) {
        formData.append('certificate', certificate);
      }
      return axios.patch(`/ocsp-responders/${id}/`, formData);
    },
    async deleteOcspResponder(id: number) {
      return axios.delete(`/ocsp-responders/${id}`);
    },
    async getOcspResponderCertificate(id: number) {
      return axios.get<OcspResponderCertificateDetails>(`/ocsp-responders/${id}/certificate`);
    },
  },
});

export interface IntermediateCasStoreState extends WithCurrentItem<CertificateAuthority> {
  currentCs: ApprovedCertificationService | null;
  currentIntermediateCas: CertificateAuthority[];
}

export const useIntermediateCasService = defineStore('intermediateCasService', {
  state: (): IntermediateCasStoreState => ({
    current: undefined,
    currentCs: null,
    currentIntermediateCas: [],
  }),
  persist: true,
  actions: {
    async loadByCs(cs: ApprovedCertificationService) {
      this.currentCs = cs;
      return this.fetchIntermediateCas();
    },
    async loadById(intermediateCaId: number) {
      this.loadingCurrent = true;
      this.current = undefined;
      return this.getIntermediateCa(intermediateCaId)
        .then((resp) => {
          this.current = resp.data;
          return this.current;
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => (this.loadingCurrent = false));
    },
    async fetchIntermediateCas() {
      if (!this.currentCs) return;

      return axios
        .get<CertificateAuthority[]>(`/certification-services/${this.currentCs.id}/intermediate-cas`)
        .then((resp) => (this.currentIntermediateCas = resp.data));
    },
    async getIntermediateCa(id: number) {
      return axios.get<CertificateAuthority>(`/intermediate-cas/${id}`);
    },
    async addIntermediateCa(certificate: File) {
      if (!this.currentCs) {
        throw new Error('CA not selected');
      }
      const formData = new FormData();
      formData.append('certificate', certificate);
      return axios
        .post(`/certification-services/${this.currentCs.id}/intermediate-cas`, formData)
        .finally(() => this.fetchIntermediateCas());
    },
    deleteIntermediateCa(id: number) {
      return axios.delete(`/intermediate-cas/${id}`);
    },
  },
});

export interface DsTlsCertificationAuthorityStoreState extends WithCurrentItem<ApprovedDsTlsCertificationAuthority> {
  dsTlsCertificationAuthorities: ApprovedDsTlsCertificationAuthorityListItem[];
}

export const useDsTlsCertificationAuthorityService = defineStore('dsTlsCertificationAuthorityService', {
  state: (): DsTlsCertificationAuthorityStoreState => ({
    current: undefined,
    dsTlsCertificationAuthorities: [],
  }),
  persist: true,
  actions: {
    async fetchAll() {
      return axios
        .get<ApprovedDsTlsCertificationAuthorityListItem[]>('/ds-tls-certification-authorities')
        .then((resp) => (this.dsTlsCertificationAuthorities = resp.data));
    },
    async loadById(dsTlsCertificationAuthorityId: number) {
      this.loadingCurrent = true;
      this.current = undefined;
      return axios
        .get<ApprovedDsTlsCertificationAuthority>(`/ds-tls-certification-authorities/${dsTlsCertificationAuthorityId}`)
        .then((resp) => {
          this.current = resp.data;
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => (this.loadingCurrent = false));
    },
    async deleteById(dsTlsCertificationAuthorityId: number) {
      return axios.delete(`/ds-tls-certification-authorities/${dsTlsCertificationAuthorityId}`);
    },
    async add(newCa: DsTlsCertificationAuthorityFileAndSettings) {
      const formData = new FormData();
      formData.append('name', newCa.name || '');
      formData.append('certificate', newCa.certificate);
      formData.append('acme_server_directory_url', newCa.acme_server_directory_url || '');
      formData.append('ds_tls_certificate_profile_id', newCa.ds_tls_certificate_profile_id || '');
      return axios.post('/ds-tls-certification-authorities', formData).finally(() => this.fetchAll());
    },
    async update(dsTlsCertificationAuthorityId: number, settings: DsTlsCertificationAuthoritySettings) {
      return axios
        .patch<ApprovedDsTlsCertificationAuthority>(`/ds-tls-certification-authorities/${dsTlsCertificationAuthorityId}`, settings)
        .then((resp) => {
          this.current = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    async getCertificate(dsTlsCertificationAuthorityId: number) {
      return axios.get<CertificateDetails>(`/ds-tls-certification-authorities/${dsTlsCertificationAuthorityId}/certificate`);
    },
  },
});

export interface DsTlsIntermediateCasStoreState extends WithCurrentItem<DsTlsIntermediateCertificateAuthority> {
  currentCa: ApprovedDsTlsCertificationAuthority | null;
  currentIntermediateCas: DsTlsIntermediateCertificateAuthority[];
}

export const useDsTlsIntermediateCasService = defineStore('dsTlsIntermediateCasService', {
  state: (): DsTlsIntermediateCasStoreState => ({
    current: undefined,
    currentCa: null,
    currentIntermediateCas: [],
  }),
  persist: true,
  actions: {
    async loadByCa(ca: ApprovedDsTlsCertificationAuthority) {
      this.currentCa = ca;
      return this.fetchIntermediateCas();
    },
    async loadById(dsTlsIntermediateCaId: number) {
      this.loadingCurrent = true;
      this.current = undefined;
      return this.getIntermediateCa(dsTlsIntermediateCaId)
        .then((resp) => {
          this.current = resp.data;
          return this.current;
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => (this.loadingCurrent = false));
    },
    async fetchIntermediateCas() {
      if (!this.currentCa) return;

      return axios
        .get<DsTlsIntermediateCertificateAuthority[]>(`/ds-tls-certification-authorities/${this.currentCa.id}/intermediate-cas`)
        .then((resp) => (this.currentIntermediateCas = resp.data));
    },
    async getIntermediateCa(dsTlsIntermediateCaId: number) {
      return axios.get<DsTlsIntermediateCertificateAuthority>(`/ds-tls-intermediate-cas/${dsTlsIntermediateCaId}`);
    },
    async addIntermediateCa(certificate: File) {
      if (!this.currentCa) {
        throw new Error('DS TLS certification authority not selected');
      }
      const formData = new FormData();
      formData.append('certificate', certificate);
      return axios
        .post(`/ds-tls-certification-authorities/${this.currentCa.id}/intermediate-cas`, formData)
        .finally(() => this.fetchIntermediateCas());
    },
    deleteIntermediateCa(dsTlsIntermediateCaId: number) {
      return axios.delete(`/ds-tls-intermediate-cas/${dsTlsIntermediateCaId}`);
    },
  },
});

export interface TimestampingServicesStoreState {
  timestampingServices: TimestampingService[];
}

export const useTimestampingServices = defineStore('timestampingServices', {
  state: (): TimestampingServicesStoreState => ({
    timestampingServices: [],
  }),
  persist: true,
  actions: {
    async fetchTimestampingServices() {
      return axios.get<TimestampingService[]>('/timestamping-services').then((resp) => (this.timestampingServices = resp.data));
    },
    async delete(id: number) {
      return axios.delete(`/timestamping-services/${id}`).finally(() => this.fetchTimestampingServices());
    },
    async addTimestampingService(url: string, costType: string, certificate: File) {
      const formData = new FormData();
      formData.append('url', url);
      formData.append('cost_type', costType);
      formData.append('certificate', certificate);
      return axios.post('/timestamping-services', formData).finally(() => this.fetchTimestampingServices());
    },
    async updateTimestampingService(id: number, url: string, costType: string, certificate: File | undefined) {
      const formData = new FormData();
      formData.append('url', url);
      formData.append('cost_type', costType);
      if (certificate) {
        formData.append('certificate', certificate);
      }
      return axios.patch(`/timestamping-services/${id}`, formData).finally(() => this.fetchTimestampingServices());
    },
  },
});

export const definedCostTypes: CostType[] = Object.values(CostType).filter((v) => v !== CostType.UNDEFINED);
