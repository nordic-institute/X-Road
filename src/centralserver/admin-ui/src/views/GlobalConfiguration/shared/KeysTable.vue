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
    <table class="xrd-table mt-0">
      <thead>
        <tr>
          <th class="title-col">
            {{ $t('keys.signKey') }}
          </th>

          <th class="expiration-col">
            {{ $t('keys.created') }}
          </th>
        </tr>
      </thead>

      <tbody v-for="key in sortedKeys" :key="key.id">
        <!-- SOFTWARE token table body -->
        <KeyRow
          :token-logged-in="tokenLoggedIn"
          :token-key="key"
          @key-click="keyClick(key)"
        />
      </tbody>
    </table>

    <!--
    <RegisterCertificateDialog
      :dialog="registerDialog"
      @save="registerCert"
      @cancel="registerDialog = false"
    /> -->

    <xrd-confirm-dialog
      :dialog="confirmDeleteCsr"
      title="keys.deleteCsrTitle"
      text="keys.deleteCsrText"
      @cancel="confirmDeleteCsr = false"
      @accept="deleteCsr()"
    />
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
//import RegisterCertificateDialog from './RegisterCertificateDialog.vue';
import KeyRow from './KeyRow.vue';

import {
  Key,
  TokenCertificateSigningRequest,
  TokenType,
} from '@/mock-openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { Prop } from 'vue/types/options';

export default Vue.extend({
  components: {
    // RegisterCertificateDialog,
    KeyRow,
  },
  props: {
    keys: {
      type: Array as Prop<Key[]>,
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
  data() {
    return {
      registerDialog: false,
      confirmDeleteCsr: false,
      selectedCsr: undefined as TokenCertificateSigningRequest | undefined,
      selectedKey: undefined as Key | undefined,
      sortDirection: false,
      tokenTypes: TokenType,
    };
  },
  computed: {
    sortedKeys(): Key[] {
      return this.keys;
    },
    canImportFromToken(): boolean {
      // Can the user import certificate from hardware token
      // TODO
      return true;
    },

    showRegisterCertButton(): boolean {
      // Decide if the user can register a certificate
      // TODO
      return true;
    },
  },

  methods: {
    canDeleteCsr(key: Key): boolean {
      // Decide if the user can delete CSR based on the key usage type and permissions
      // TODO
      return true;
    },
    keyClick(key: Key): void {
      this.$emit('key-click', key);
    },

    showDeleteCsrDialog(req: TokenCertificateSigningRequest, key: Key): void {
      this.confirmDeleteCsr = true;
      this.selectedCsr = req;
      this.selectedKey = key;
    },
    deleteCsr(): void {
      this.confirmDeleteCsr = false;

      if (!this.selectedKey || !this.selectedCsr) {
        return;
      }

      api
        .remove(
          `/keys/${encodePathParameter(
            this.selectedKey.id,
          )}/csrs/${encodePathParameter(this.selectedCsr.id)}`,
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.csrDeleted');
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.cert-icon {
  color: $XRoad-WarmGrey100;
  margin-right: 20px;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.td-align-right {
  text-align: right;
}

td.td-name {
  padding-left: 30px;
  text-align: center;
  vertical-align: middle;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-left: 2.7rem;
}
</style>
