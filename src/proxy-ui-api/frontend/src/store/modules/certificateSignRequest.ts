
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { saveResponseAsFile } from '@/util/helpers';
import { Key, CertificateAuthority, CsrSubjectFieldDescription } from '@/openapi-types';
import * as api from '@/util/api';
import { UsageTypes, CsrFormatTypes } from '@/global';


export interface CsrState {
  csrKey: Key | null;
  csrClient: string | null;
  usage: string | null;
  certificationService: string;
  csrFormat: string;
  certificationServiceList: CertificateAuthority[];
  keyId: string;
  form: CsrSubjectFieldDescription[];
  keyLabel: string;
  tokenId: string | undefined;
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
    form: [],
    keyLabel: '',
    tokenId: undefined,
  };
};

// Initial state. The state can be reseted with this.
const csrState = getDefaultState();


export const crsGetters: GetterTree<CsrState, RootState> = {
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
  csrForm(state): CsrSubjectFieldDescription[] {
    return state.form;
  },
  certificationServiceList(state): CertificateAuthority[] {
    return state.certificationServiceList;
  },
  keyId(state): string {
    return state.keyId;
  },
  keyLabel(state): string {
    return state.keyLabel;
  },
  isUsageReadOnly(state): boolean {
    // Usage type can be selected only when the Key doesn't have already have it set
    if (state.csrKey && state.csrKey.usage) {
      return true;
    }
    return false;
  },
  filteredServiceList(state): CertificateAuthority[] {
    // Return the list of available auth services based on the current usage type
    if (state.usage === UsageTypes.SIGNING) {
      const filtered = state.certificationServiceList.filter(
        (service: CertificateAuthority) => {
          return !service.authentication_only;
        },
      );
      return filtered;
    }
    return state.certificationServiceList;
  },
  csrRequestBody(state) {
    // Creates an object that can be used as body for generate CSR request
    const subjectFieldValues: any = {};

    state.form.forEach((item: any) => {
      subjectFieldValues[item.id] = item.default_value;
    });

    return {
      key_usage_type: state.usage,
      ca_name: state.certificationService,
      csr_format: state.csrFormat,
      subject_field_values: subjectFieldValues,
      member_id: state.csrClient,
    };
  },
  csrTokenId(state): string | undefined {
    return state.tokenId;
  },
};

export const mutations: MutationTree<CsrState> = {
  resetCsrState(state) {
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
  storeCertificationServiceList(state, csl: CertificateAuthority[]) {
    state.certificationServiceList = csl;
  },
  storeForm(state, form: CsrSubjectFieldDescription[]) {
    state.form = form;
  },
  storeKeyId(state, id: string) {
    state.keyId = id;
  },
  storeKeyLabel(state, label: string) {
    state.keyLabel = label;
  },
  storeCsrTokenId(state, tokenId: string) {
    state.tokenId = tokenId;
  },
};

export const actions: ActionTree<CsrState, RootState> = {
  resetCsrState({ commit }) {
    commit('resetCsrState');
  },
  setCsrTokenId({ commit, dispatch, rootGetters }, tokenId: string) {
    commit('storeCsrTokenId', tokenId);
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
        `&member_id=${state.csrClient}`;
    } else {
      query =
        `/certificate-authorities/${state.certificationService}/csr-subject-fields?key_usage_type=${state.usage}`;
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

  generateCsr({ commit, getters, state }) {
    const requestBody = getters.csrRequestBody;

    return api
      .post(`/keys/${state.keyId}/csrs`, requestBody)
      .then((response) => {
        saveResponseAsFile(response);
      }).catch((error: any) => {
        throw error;
      });
  },

  generateKeyAndCsr({ commit, getters, state }, tokenId: string) {
    const crtObject = getters.csrRequestBody;

    const body = {
      key_label: getters.keyLabel,
      csr_generate_request: crtObject,
    };

    return api
      .post(`/tokens/${tokenId}/keys-with-csrs`, body)
      .then((response) => {
        saveResponseAsFile(response);
      })
      .catch((error) => {
        throw error;
      });
  },

  setupSignKey({ commit, rootGetters }) {
    // Initialize the state with sign type Key. Needed for "add client" wizard.
    const templateKey: Key = {
      id: '',
      name: '',
      label: '',
      certificates: [],
      certificate_signing_requests: [],
      usage: UsageTypes.SIGNING,
    };

    commit('storeCsrKey', templateKey);
    commit('storeUsage', UsageTypes.SIGNING);
  },

};

export const csrModule: Module<CsrState, RootState> = {
  namespaced: false,
  state: csrState,
  getters: crsGetters,
  actions,
  mutations,
};
