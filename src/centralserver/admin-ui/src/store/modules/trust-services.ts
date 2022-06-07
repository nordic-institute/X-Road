import { ApprovedCertificationService } from '@/openapi-types';
import { defineStore } from 'pinia';
import axios from 'axios';

export interface State {
  certificationSevices: ApprovedCertificationService[];
}

export const useCertificationServiceStore = defineStore(
  'certificationService',
  {
    state: (): State => ({
      certificationSevices: [],
    }),
    persist: true,
    actions: {
      fetchAll() {
        return axios
          .get<ApprovedCertificationService[]>('/certification-services')
          .then((resp) => (this.certificationSevices = resp.data));
      },
    },
  },
);
