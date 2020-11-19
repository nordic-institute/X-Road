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
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{ $t(title) }}</th>
          <th>{{ $t('keys.id') }}</th>
          <th>{{ $t('keys.ocsp') }}</th>
          <th>{{ $t('keys.expires') }}</th>
          <th>{{ $t('keys.status') }}</th>
          <th></th>
        </tr>
      </thead>

      <tbody v-for="key in keys" v-bind:key="key.id">
        <tr>
          <td>
            <div class="name-wrap">
              <i class="icon-xrd_key icon clickable" @click="keyClick(key)"></i>
              <div class="clickable-link" @click="keyClick(key)">
                {{ key.name }}
              </div>
            </div>
          </td>
          <td>
            <div class="id-wrap">
              <div class="clickable-link" @click="keyClick(key)">
                {{ key.id }}
              </div>
            </div>
          </td>
          <td></td>
          <td></td>
          <td></td>
          <td class="align-right">
            <SmallButton
              v-if="canCreateCsr"
              class="table-button-fix"
              :disabled="disableGenerateCsr(key)"
              @click="generateCsr(key)"
              >{{ $t('keys.generateCsr') }}</SmallButton
            >
          </td>
        </tr>

        <CertificateRow
          v-for="cert in key.certificates"
          v-bind:key="cert.id"
          :cert="cert"
          @certificate-click="certificateClick(cert, key)"
        >
          <div slot="certificateAction">
            <template v-if="canImportFromToken">
              <SmallButton
                v-if="cert.possible_actions.includes('IMPORT_FROM_TOKEN')"
                class="table-button-fix"
                @click="importCert(cert.certificate_details.hash)"
                >{{ $t('keys.importCert') }}</SmallButton
              >

              <!-- Special case where HW cert has auth usage -->
              <div v-else-if="key.usage === 'AUTHENTICATION'">
                {{ $t('keys.authNotSupported') }}
              </div>
            </template>
          </div>
        </CertificateRow>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import SmallButton from '@/components/ui/SmallButton.vue';
import { Key, PossibleAction, TokenCertificate } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';
import CertificateRow from '@/views/KeysAndCertificates/SignAndAuthKeys/CertificateRow.vue';

export default Vue.extend({
  components: {
    SmallButton,
    CertificateRow,
  },
  props: {
    keys: {
      type: Array,
      required: true,
    },
    title: {
      type: String,
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
  computed: {
    canCreateCsr(): boolean {
      return (
        this.$store.getters.hasPermission(Permissions.GENERATE_AUTH_CERT_REQ) ||
        this.$store.getters.hasPermission(Permissions.GENERATE_SIGN_CERT_REQ)
      );
    },
    canImportFromToken(): boolean {
      // Can the user import certificate from hardware token
      return this.$store.getters.hasPermission(Permissions.IMPORT_SIGN_CERT);
    },
  },
  methods: {
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
    importCert(hash: string): void {
      this.$emit('import-cert-by-hash', hash);
    },
    certificateClick(cert: TokenCertificate, key: Key): void {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          hash: cert.certificate_details.hash,
          usage: key.usage,
        },
      });
    },
    keyClick(key: Key): void {
      this.$emit('key-click', key);
    },
    generateCsr(key: Key): void {
      this.$emit('generate-csr', key);
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.$emit('refresh-list');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable {
  cursor: pointer;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
  height: 100%;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}

.id-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
  width: 100%;
}

.align-right {
  text-align: right;
}
</style>
