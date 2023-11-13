<!--
   The MIT License
   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div>
    <table class="xrd-table keys-table">
      <KeysTableThead
        :sort-direction="sortDirection"
        :selected-sort="selectedSort"
        @set-sort="setSort"
      />

      <tbody v-for="key in sortedKeys" :key="key.id">
        <!-- Key -->
        <KeyRow
          :token-logged-in="tokenLoggedIn"
          :token-key="key"
          @generate-csr="generateCsr(key)"
          @key-click="keyClick(key)"
        />

        <!-- Certificate -->
        <CertificateRow
          v-for="cert in key.certificates"
          :key="cert.certificate_details.issuer_distinguished_name"
          :cert="cert"
          @certificate-click="certificateClick(cert, key)"
        >
          <template #certificateAction>
            <template v-if="canImportFromToken">
              <xrd-button
                v-if="
                  cert.possible_actions?.includes(
                    PossibleAction.IMPORT_FROM_TOKEN,
                  )
                "
                class="table-button-fix"
                :outlined="false"
                text
                data-test="import-from-token-button"
                @click="importCert(cert.certificate_details.hash)"
                >{{ $t('keys.importCert') }}</xrd-button
              >

              <!-- Special case where HW cert has auth usage -->
              <div v-else-if="key.usage === 'AUTHENTICATION'">
                {{ $t('keys.authNotSupported') }}
              </div>
            </template>
          </template>
        </CertificateRow>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import { defineComponent, PropType } from 'vue';
import KeyRow from './KeyRow.vue';
import CertificateRow from './CertificateRow.vue';
import KeysTableThead from './KeysTableThead.vue';
import {
  Key,
  PossibleAction,
  TokenCertificate,
  TokenCertificateSigningRequest,
} from '@/openapi-types';
import { Permissions } from '@/global';
import { KeysSortColumn } from './keyColumnSorting';
import * as Sorting from './keyColumnSorting';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  components: {
    KeyRow,
    CertificateRow,
    KeysTableThead,
  },
  props: {
    keys: {
      type: Array as PropType<Key[]>,
      required: true,
    },
    tokenLoggedIn: {
      type: Boolean,
    },
    tokenType: {
      type: String,
      required: true,
    },
  },
  emits: [
    'key-click',
    'certificate-click',
    'generate-csr',
    'import-cert-by-hash',
  ],
  data() {
    return {
      registerDialog: false,
      confirmDeleteCsr: false,
      selectedCert: undefined as TokenCertificate | undefined,
      selectedCsr: undefined as TokenCertificateSigningRequest | undefined,
      selectedKey: undefined as Key | undefined,
      sortDirection: false,
      selectedSort: KeysSortColumn.NAME,
    };
  },

  computed: {
    ...mapState(useUser, ['hasPermission']),
    PossibleAction() {
      return PossibleAction;
    },
    sortedKeys(): Key[] {
      return Sorting.keyArraySort(
        this.keys,
        this.selectedSort,
        this.sortDirection,
      );
    },
    canCreateCsr(): boolean {
      return (
        this.hasPermission(Permissions.GENERATE_AUTH_CERT_REQ) ||
        this.hasPermission(Permissions.GENERATE_SIGN_CERT_REQ)
      );
    },
    canImportFromToken(): boolean {
      // Can the user import certificate from hardware token
      return this.hasPermission(Permissions.IMPORT_UNKNOWN_CERT);
    },
  },

  methods: {
    setSort(sort: KeysSortColumn): void {
      if (sort === this.selectedSort) {
        this.sortDirection = !this.sortDirection;
      }

      this.selectedSort = sort;
    },
    disableGenerateCsr(key: Key): boolean {
      if (!this.tokenLoggedIn) {
        return true;
      }

      if (
        key.possible_actions?.includes(PossibleAction.GENERATE_AUTH_CSR) ||
        key.possible_actions?.includes(PossibleAction.GENERATE_SIGN_CSR)
      ) {
        return false;
      }

      return true;
    },

    keyClick(key: Key): void {
      this.$emit('key-click', key);
    },
    certificateClick(cert: TokenCertificate, key: Key): void {
      this.$emit('certificate-click', { cert, key });
    },
    generateCsr(key: Key): void {
      this.$emit('generate-csr', key);
    },
    importCert(hash: string): void {
      this.$emit('import-cert-by-hash', hash);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.keys-table {
  margin-top: 20px;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}
</style>
