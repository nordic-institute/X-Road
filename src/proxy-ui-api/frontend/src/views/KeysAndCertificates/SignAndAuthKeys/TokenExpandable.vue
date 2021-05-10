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
  <xrd-expandable
    class="expandable"
    @open="descOpen(token.id)"
    @close="descClose(token.id)"
    :isOpen="isExpanded(token.id)"
    :color="tokenStatusColor"
  >
    <template v-slot:link>
      <div
        class="clickable-link identifier-wrap"
        @click="tokenNameClick()"
        data-test="token-name"
      >
        <span
          class="token-status-indicator token-name"
          v-bind:class="tokenStatusClass"
        >
          {{ $t('keys.token') }} {{ token.name }}
        </span>

        <v-btn icon @click="tokenClick(token)" color="primary">
          <v-icon class="button-icon">icon-Edit</v-icon>
        </v-btn>
      </div>
    </template>

    <template v-slot:action>
      <div class="action-slot-wrapper">
        <template v-if="canActivateToken">
          <div
            v-if="tokenLabelKey && tokenLabelKey.length > 1"
            class="token-status token-status-indicator label"
            v-bind:class="tokenStatusClass"
          >
            <v-icon
              class="token-status-indicator"
              v-bind:class="tokenStatusClass"
              >{{ tokenIcon }}</v-icon
            >
            {{ $t(tokenLabelKey) }}
          </div>
          <TokenLoggingButton
            class="token-logging-button"
            :token="token"
            @token-logout="logout()"
            @token-login="login()"
          />
        </template>
      </div>
    </template>

    <template v-slot:content>
      <div>
        <div class="button-wrap mb-6">
          <xrd-button
            v-if="canAddKey"
            outlined
            @click="addKey()"
            :disabled="!token.logged_in"
            data-test="token-add-key-button"
            ><v-icon class="xrd-large-button-icon">icon-Add</v-icon
            >{{ $t('keys.addKey') }}</xrd-button
          >
          <xrd-file-upload
            v-if="canImportCertificate"
            accepts=".pem, .cer, .der"
            @file-changed="importCert"
            v-slot="{ upload }"
          >
            <xrd-button
              outlined
              class="button-spacing"
              :disabled="!token.logged_in"
              @click="upload"
              data-test="token-import-cert-button"
            >
              <v-icon class="xrd-large-button-icon">icon-Import</v-icon>
              {{ $t('keys.importCert') }}</xrd-button
            >
          </xrd-file-upload>
        </div>

        <!-- AUTH keys table -->

        <div v-if="getAuthKeys(token.keys).length > 0">
          <KeysTableTitle
            title="keys.authKeyCert"
            :keys="getAuthKeys(token.keys)"
            @click="authKeysOpen = !authKeysOpen"
            :arrowState="authKeysOpen"
          />
          <keys-table
            v-if="authKeysOpen"
            :keys="getAuthKeys(token.keys)"
            :tokenLoggedIn="token.logged_in"
            :tokenType="token.type"
            @key-click="keyClick"
            @generate-csr="generateCsr"
            @certificate-click="certificateClick"
            @import-cert-by-hash="importCertByHash"
            @refresh-list="fetchData"
          />
        </div>
        <!-- SIGN keys table -->

        <div v-if="getSignKeys(token.keys).length > 0">
          <KeysTableTitle
            title="keys.signKeyCert"
            :keys="getSignKeys(token.keys)"
            :arrowState="signKeysOpen"
            @click="signKeysOpen = !signKeysOpen"
          />

          <keys-table
            class="keys-table"
            v-if="signKeysOpen"
            :keys="getSignKeys(token.keys)"
            :tokenLoggedIn="token.logged_in"
            :tokenType="token.type"
            @key-click="keyClick"
            @generate-csr="generateCsr"
            @certificate-click="certificateClick"
            @import-cert-by-hash="importCertByHash"
            @refresh-list="fetchData"
          />
        </div>

        <!-- Keys with unknown type -->
        <div v-if="getOtherKeys(token.keys).length > 0">
          <KeysTableTitle
            title="keys.unknown"
            :keys="getOtherKeys(token.keys)"
            :arrowState="unknownKeysOpen"
            @click="unknownKeysOpen = !unknownKeysOpen"
          />
          <unknown-keys-table
            v-if="unknownKeysOpen"
            :keys="getOtherKeys(token.keys)"
            :tokenLoggedIn="token.logged_in"
            :tokenType="token.type"
            @key-click="keyClick"
            @generate-csr="generateCsr"
            @certificate-click="certificateClick"
            @import-cert-by-hash="importCertByHash"
          />
        </div>
      </div>
    </template>
  </xrd-expandable>
</template>

<script lang="ts">
// View for a token
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import KeysTable from './KeysTable.vue';
import KeysTableTitle from './KeysTableTitle.vue';
import UnknownKeysTable from './UnknownKeysTable.vue';
import { Key, KeyUsageType, Token, TokenCertificate } from '@/openapi-types';
import * as api from '@/util/api';
import { FileUploadResult } from '@niis/shared-ui';
import { encodePathParameter } from '@/util/api';
import TokenLoggingButton from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenLoggingButton.vue';
import { Prop } from 'vue/types/options';
import { Colors } from '@/global';
import {
  getTokenUIStatus,
  TokenUIStatus,
} from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenStatusHelper';

export default Vue.extend({
  components: {
    KeysTable,
    KeysTableTitle,
    UnknownKeysTable,
    TokenLoggingButton,
  },
  props: {
    token: {
      type: Object as Prop<Token>,
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

    tokenInactive(): boolean {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);
      return status === TokenUIStatus.Inactive;
    },

    tokenUnavailable(): boolean {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);
      return status === TokenUIStatus.Unavailable;
    },

    tokenUnsaved(): boolean {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);
      return status === TokenUIStatus.Unsaved;
    },

    tokenLabelKey(): string {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);

      if (this.tokenInactive) {
        return 'keys.tokenStatus.inactive';
      } else if (status === TokenUIStatus.Unavailable) {
        return 'keys.tokenStatus.unavailable';
      } else if (status === TokenUIStatus.Unsaved) {
        return 'keys.tokenStatus.unsaved';
      }

      return ''; // if TokenUIStatus is Active or Available or unknown return empty string
    },

    tokenIcon(): string {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);

      if (status === TokenUIStatus.Inactive) {
        return 'icon-Cancel';
      } else if (status === TokenUIStatus.Unavailable) {
        return 'icon-Error';
      } else if (status === TokenUIStatus.Unsaved) {
        return 'icon-Error';
      }

      return '';
    },

    tokenStatusClass(): string {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);

      if (status === TokenUIStatus.Inactive) {
        return 'inactive';
      } else if (status === TokenUIStatus.Unavailable) {
        return 'unavailable';
      } else if (status === TokenUIStatus.Unsaved) {
        return 'unsaved';
      }

      return '';
    },
    tokenStatusColor(): string {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);

      if (status === TokenUIStatus.Inactive) {
        return this.colors.Black50;
      } else if (
        status === TokenUIStatus.Unavailable ||
        status === TokenUIStatus.Unsaved
      ) {
        return this.colors.Error; // Red
      } else {
        return this.colors.Black100;
      }
    },
  },
  data() {
    return {
      colors: Colors,
      authKeysOpen: true,
      signKeysOpen: true,
      unknownKeysOpen: true,
    };
  },
  methods: {
    addKey(): void {
      this.$store.dispatch('setSelectedToken', this.token);
      this.$emit('add-key');
    },

    login(): void {
      this.$store.dispatch('setSelectedToken', this.token);
      this.$emit('token-login');
    },

    logout(): void {
      this.$store.dispatch('setSelectedToken', this.token);
      this.$emit('token-logout');
    },

    tokenNameClick(): void {
      if (this.isExpanded(this.token.id)) {
        this.descClose(this.token.id);
        return;
      }

      this.descOpen(this.token.id);
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
          usage: payload.key.usage ?? 'undefined',
        },
      });
    },

    getAuthKeys(keys: Key[]): Key[] {
      const filtered = keys.filter((key: Key) => {
        return key.usage === KeyUsageType.AUTHENTICATION;
      });

      return filtered;
    },

    getSignKeys(keys: Key[]): Key[] {
      return keys.filter((key: Key) => key.usage === KeyUsageType.SIGNING);
    },

    getOtherKeys(keys: Key[]): Key[] {
      // Keys that don't have assigned usage type
      return keys.filter(
        (key: Key) =>
          key.usage !== KeyUsageType.SIGNING &&
          key.usage !== KeyUsageType.AUTHENTICATION,
      );
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
        params: {
          keyId: key.id,
          tokenType: this.token.type,
        },
      });
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.$emit('refresh-list');
    },
  },
  created() {
    if (this.getAuthKeys(this.token.keys).length > 10) {
      this.authKeysOpen = false;
    }
    if (this.getSignKeys(this.token.keys).length > 10) {
      this.signKeysOpen = false;
    }
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';
@import '~styles/colors';

.token-logging-button {
  display: inline-flex;
}

.token-status-indicator {
  font-weight: bold;
  text-transform: uppercase;
  text-align: center;

  &.label {
    margin-right: 24px;
    text-decoration: none;
  }

  &.inactive {
    color: $XRoad-Black50;
    text-decoration-color: $XRoad-Black50;
  }

  &.unavailable {
    color: $XRoad-Error;
    text-decoration-color: $XRoad-Error;
  }

  &.unsaved {
    color: $XRoad-Error;
    text-decoration-color: $XRoad-Error;
  }
}

.clickable-link {
  color: $XRoad-Purple100;
  cursor: pointer;
}

.expandable {
  margin-bottom: 10px;
}

.action-slot-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.token-status {
  display: flex;
  flex-direction: row;
  height: 100%;
  align-items: center;
  font-weight: 700;
}

.button-wrap {
  margin-top: 10px;
  padding-right: 16px;
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.button-spacing {
  margin-left: 20px;
}

.keys-table {
  transform-origin: top;
  transition: transform 0.4s ease-in-out;
}

.button-icon {
  margin-top: -14px; // fix for icon position
}
</style>
