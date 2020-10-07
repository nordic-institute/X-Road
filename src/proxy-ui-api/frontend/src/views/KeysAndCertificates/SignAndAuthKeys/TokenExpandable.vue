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
  <expandable
    class="expandable"
    @open="descOpen(token.id)"
    @close="descClose(token.id)"
    :isOpen="isExpanded(token.id)"
  >
    <template v-slot:action>
      <template v-if="canActivateToken">
        <large-button
          @click="confirmLogin()"
          v-if="!token.logged_in"
          :disabled="!token.available"
          data-test="token-login-button"
          >{{ $t('keys.logIn') }}</large-button
        >
        <large-button
          @click="confirmLogout()"
          v-if="token.logged_in"
          outlined
          data-test="token-logout-button"
          >{{ $t('keys.logOut') }}</large-button
        >
      </template>
    </template>

    <template v-slot:link>
      <div
        class="clickable-link identifier-wrap"
        @click="tokenClick(token)"
        data-test="token-name"
      >
        {{ $t('keys.token') }} {{ token.name }}
      </div>
    </template>

    <template v-slot:content>
      <div>
        <div class="button-wrap">
          <large-button
            v-if="canAddKey"
            outlined
            @click="addKey()"
            :disabled="!token.logged_in"
            data-test="token-add-key-button"
            >{{ $t('keys.addKey') }}</large-button
          >
          <file-upload
            v-if="canImportCertificate"
            accepts=".pem, .cer, .der"
            @fileChanged="importCert"
            v-slot="{ upload }"
          >
            <large-button
              outlined
              class="button-spacing"
              :disabled="!token.logged_in"
              @click="upload"
              data-test="token-import-cert-button"
              >{{ $t('keys.importCert') }}</large-button
            >
          </file-upload>
        </div>

        <!-- AUTH keys table -->
        <keys-table
          v-if="getAuthKeys(token.keys).length > 0"
          :keys="getAuthKeys(token.keys)"
          title="keys.authKeyCert"
          :tokenLoggedIn="token.logged_in"
          :tokenType="token.type"
          @keyClick="keyClick"
          @generateCsr="generateCsr"
          @certificateClick="certificateClick"
          @importCertByHash="importCertByHash"
          @refreshList="fetchData"
        />

        <!-- SIGN keys table -->
        <keys-table
          v-if="getSignKeys(token.keys).length > 0"
          :keys="getSignKeys(token.keys)"
          title="keys.signKeyCert"
          :tokenLoggedIn="token.logged_in"
          :tokenType="token.type"
          @keyClick="keyClick"
          @generateCsr="generateCsr"
          @certificateClick="certificateClick"
          @importCertByHash="importCertByHash"
          @refreshList="fetchData"
        />

        <!-- Keys with unknown type -->
        <unknown-keys-table
          v-if="getOtherKeys(token.keys).length > 0"
          :keys="getOtherKeys(token.keys)"
          title="keys.unknown"
          :tokenLoggedIn="token.logged_in"
          :tokenType="token.type"
          @keyClick="keyClick"
          @generateCsr="generateCsr"
          @importCertByHash="importCertByHash"
        />
      </div>
    </template>
  </expandable>
</template>

<script lang="ts">
// View for a token
import Vue from 'vue';
import { Permissions, RouteName, UsageTypes } from '@/global';
import Expandable from '@/components/ui/Expandable.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import KeysTable from './KeysTable.vue';
import UnknownKeysTable from './UnknownKeysTable.vue';
import { Key, Token, TokenCertificate } from '@/openapi-types';
import * as api from '@/util/api';
import FileUpload from '@/components/ui/FileUpload.vue';
import { FileUploadResult } from '@/ui-types';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    Expandable,
    LargeButton,
    KeysTable,
    UnknownKeysTable,
    FileUpload,
  },
  props: {
    token: {
      type: Object,
      required: true,
    },
  },
  computed: {
    canActivateToken(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.ACTIVATE_DEACTIVATE_TOKEN,
      );
    },
    canImportCertificate(): boolean {
      return (
        this.$store.getters.hasPermission(Permissions.IMPORT_AUTH_CERT) ||
        this.$store.getters.hasPermission(Permissions.IMPORT_SIGN_CERT)
      );
    },
    canAddKey(): boolean {
      return this.$store.getters.hasPermission(Permissions.GENERATE_KEY);
    },
  },
  methods: {
    confirmLogout(): void {
      this.$store.dispatch('setSelectedToken', this.token);
      this.$emit('tokenLogout');
    },
    confirmLogin(): void {
      this.$store.dispatch('setSelectedToken', this.token);
      this.$emit('tokenLogin');
    },

    addKey(): void {
      this.$store.dispatch('setSelectedToken', this.token);
      this.$emit('addKey');
    },

    tokenClick(token: Token): void {
      this.$router.push({
        name: RouteName.Token,
        params: { id: token.id },
      });
    },

    keyClick(key: Key): void {
      this.$router.push({
        name: RouteName.Key,
        params: { id: key.id },
      });
    },

    certificateClick(payload: { cert: TokenCertificate; key: Key }): void {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          hash: payload.cert.certificate_details.hash,
          usage: payload.key.usage,
        },
      });
    },

    getAuthKeys(keys: Key[]): Key[] {
      const filtered = keys.filter((key: Key) => {
        return key.usage === UsageTypes.AUTHENTICATION;
      });

      return filtered;
    },

    getSignKeys(keys: Key[]): Key[] {
      const filtered = keys.filter((key: Key) => {
        if (
          this.token.type === 'HARDWARE' &&
          key.usage !== UsageTypes.SIGNING &&
          key.usage !== UsageTypes.AUTHENTICATION
        ) {
          // Hardware keys are SIGNING type by definition
          // If a hardware token's key doesn't have a usage type make it a SIGNING key
          return true;
        }
        return key.usage === UsageTypes.SIGNING;
      });

      return filtered;
    },

    getOtherKeys(keys: Key[]): Key[] {
      // Keys that don't have assigned usage type
      const filtered = keys.filter((key: Key) => {
        return (
          this.token.type !== 'HARDWARE' &&
          key.usage !== UsageTypes.SIGNING &&
          key.usage !== UsageTypes.AUTHENTICATION
        );
      });

      return filtered;
    },

    descClose(tokenId: string) {
      this.$store.dispatch('hideToken', tokenId);
    },
    descOpen(tokenId: string) {
      this.$store.dispatch('expandToken', tokenId);
    },
    isExpanded(tokenId: string) {
      return this.$store.getters.tokenExpanded(tokenId);
    },

    importCert(event: FileUploadResult) {
      api
        .post('/token-certificates', event.buffer, {
          headers: {
            'Content-Type': 'application/octet-stream',
          },
        })
        .then(
          () => {
            this.$store.dispatch('showSuccess', 'keys.importCertSuccess');
            this.fetchData();
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
    },
    importCertByHash(hash: string) {
      api
        .post(`/token-certificates/${encodePathParameter(hash)}/import`, {})
        .then(
          () => {
            this.$store.dispatch('showSuccess', 'keys.importCertSuccess');
            this.fetchData();
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
    },
    generateCsr(key: Key) {
      this.$router.push({
        name: RouteName.GenerateCertificateSignRequest,
        params: { keyId: key.id },
      });
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.$emit('refreshList');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.expandable {
  margin-bottom: 10px;
}

.button-wrap {
  margin-top: 10px;
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.button-spacing {
  margin-left: 20px;
}
</style>
