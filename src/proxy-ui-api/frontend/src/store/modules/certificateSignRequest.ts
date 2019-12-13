
import _ from 'lodash';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { saveAsFile } from '@/util/helpers';
import { Key, Token } from '@/types';
import * as api from '@/util/api';
import { UsageTypes, CsrFormatTypes } from '@/global';


export interface CsrState {
  csrKey: Key | null;
  csrClient: string | null;
  usage: string | null;
  certificationService: string;
  csrFormat: string;
  certificationServiceList: any[];
  keyId: string;
  form: any;
}

const getDefaultState = () => {
  return {
    csrKey: null,
    csrClient: null,
    usage: null,
    certificationService: '',
    csrFormat: CsrFormatTypes.DER,
    certificationServiceList: [],
    keyId: '',
    form: null,
  };
};

// Initial state. The state can be reseted with this.
const csrState = getDefaultState();


export const getters: GetterTree<CsrState, RootState> = {
  csrClient(state): string | null {
    return state.csrClient;
  },
  csrKey(state): Key | null {
    return state.csrKey;
  },
  usage(state): string | null {
    return state.usage;
  },
  certificationService(state): string {
    return state.certificationService;
  },
  csrFormat(state): string {
    return state.csrFormat;
  },
  csrForm(state): string {
    return state.form;
  },
  certificationServiceList(state): any[] {
    return state.certificationServiceList;
  },
  keyId(state): string {
    return state.keyId;
  },
  isUsageReadOnly(state): boolean {
    // Usage type can be selected only when the Key doesn't have already have it set
    if (state.csrKey && state.csrKey.usage) {
      return true;
    }
    return false;
  },
  filteredServiceList(state): any[] {
    // Return the list of available auth services based on the current usage type
    if (state.usage === UsageTypes.SIGNING) {
      const filtered = state.certificationServiceList.filter(
        (service: any) => {
          return !service.authentication_only;
        },
      );
      return filtered;
    }
    return state.certificationServiceList;
  },
};

export const mutations: MutationTree<CsrState> = {
  resetState(state) {
    Object.assign(state, getDefaultState());
  },
  storeCsrClient(state, client: string | null) {
    state.csrClient = client;
  },
  storeCsrKey(state, key: Key) {
    state.csrKey = key;
  },
  storeUsage(state, usage: string) {
    state.usage = usage;
  },
  storeCertificationService(state, cs: string) {
    state.certificationService = cs;
  },
  storeCsrFormat(state, csrFormat: string) {
    state.csrFormat = csrFormat;
  },
  storeCertificationServiceList(state, csl: any[]) {
    state.certificationServiceList = csl;
  },
  storeForm(state, form: any) {
    state.form = form;
  },
  storeKeyId(state, id: string) {
    state.keyId = id;
  },
};

export const actions: ActionTree<CsrState, RootState> = {
  resetState({ commit }) {
    commit('resetState');
  },

  fetchCertificateAuthorities({ commit, rootGetters }) {
    api
      .get(`/certificate-authorities`)
      .then((res: any) => {
        commit('storeCertificationServiceList', res.data);
      })
      .catch((error: any) => {
        throw error;
      });
  },

  fetchCsrForm({ commit, rootGetters, state }) {
    let query = '';

    if (state.usage === UsageTypes.SIGNING) {
      query =
        `/certificate-authorities/${state.certificationService}/csr-subject-fields?key_usage_type=${state.usage}` +
        `&key_id=${state.keyId}&member_id=${state.csrClient}`;
    } else {
      query =
        `/certificate-authorities/${state.certificationService}/csr-subject-fields?key_usage_type=${state.usage}` +
        `&key_id=${state.keyId}`;
    }

    return api
      .get(query)
      .then((res: any) => {
        commit('storeForm', res.data);
      })
      .catch((error: any) => {
        throw error;
      });
  },

  fetchKeyData({ commit, rootGetters, state }) {
    return api
      .get(`/keys/${state.keyId}`)
      .then((res: any) => {
        commit('storeCsrKey', res.data);

        if (res.data.usage) {
          commit('storeUsage', res.data.usage);
        }
      })
      .catch((error: any) => {
        throw error;
      });
  },

  setCsrFormat({ commit, rootGetters }, csrFormat: string) {
    commit('storeCsrFormat', csrFormat);
  },

  generateCsr({ commit, rootGetters, state }) {

    const subjectFieldValues: any = {};

    state.form.forEach((item: any) => {
      subjectFieldValues[item.id] = item.default_value;
    });

    return api
      .put(`/keys/${state.keyId}/generate-csr`, {
        key_usage_type: state.usage,
        ca_name: state.certificationService,
        csr_format: state.csrFormat,
        subject_field_values: subjectFieldValues,
        member_id: state.csrClient,
      })
      .then((response) => {
        saveAsFile(response);
      }).catch((error: any) => {
        throw error;
      });
  },
};

export const csrModule: Module<CsrState, RootState> = {
  namespaced: false,
  state: csrState,
  getters,
  actions,
  mutations,
};
