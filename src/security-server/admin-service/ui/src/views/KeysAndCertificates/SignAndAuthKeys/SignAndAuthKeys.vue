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
  <div class="title-and-search">
    <div class="xrd-view-title">{{ $t('tab.keys.signAndAuthKeys') }}</div>
    <help-button
      :help-image="helpImage"
      help-title="keys.helpTitleKeys"
      help-text="keys.helpTextKeys"
    ></help-button>
    <div class="search-row">
      <xrd-search v-model="search" />
    </div>
  </div>

  <XrdEmptyPlaceholder
    :data="filtered"
    :loading="loading"
    :filtered="!!search && search.length > 0"
    :no-items-text="$t('noData.noTokens')"
    skeleton-type="table-heading"
  />

  <template v-if="filtered && !loading">
    <TokenExpandable
      v-for="token in filtered"
      :key="token.id"
      :token="token"
      @refresh-list="fetchData"
      @token-logout="logoutDialog = true"
      @token-login="loginDialog = true"
      @add-key="addKey"
    />
  </template>

  <xrd-confirm-dialog
    v-if="logoutDialog"
    title="keys.logOutTitle"
    text="keys.logOutText"
    @cancel="logoutDialog = false"
    @accept="acceptTokenLogout()"
  />

  <TokenLoginDialog
    :dialog="loginDialog"
    @cancel="loginDialog = false"
    @save="tokenLogin"
  />
</template>

<script lang="ts">
// View for keys tab
import { defineComponent } from 'vue';
import { RouteName } from '@/global';
import TokenExpandable from './TokenExpandable.vue';
import TokenLoginDialog from '@/components/token/TokenLoginDialog.vue';
import HelpButton from '../HelpButton.vue';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useTokens } from '@/store/modules/tokens';
import helpImage from '@/assets/keys_and_certificates.png';

import {
  Key,
  Token,
  TokenCertificate,
  TokenCertificateSigningRequest,
} from '@/openapi-types';
import { deepClone } from '@/util/helpers';

export default defineComponent({
  components: {
    HelpButton,
    TokenExpandable,
    TokenLoginDialog,
  },
  data() {
    return {
      search: '',
      loginDialog: false,
      logoutDialog: false,
      loading: false,
    };
  },
  computed: {
    ...mapState(useTokens, ['tokens', 'selectedToken']),
    helpImage(): string {
      return helpImage;
    },
    filtered(): Token[] {
      if (!this.tokens || this.tokens.length === 0) {
        return [];
      }

      // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
      let arr = deepClone<Token[]>(this.tokens).sort((a, b) => {
        if (a.id < b.id) {
          return -1;
        }
        if (a.id > b.id) {
          return 1;
        }

        // equal id:s. (should not happen)
        return 0;
      });

      // Check that there is a search input
      if (!this.search || this.search.length < 1) {
        return arr;
      }

      const mysearch = this.search.toLowerCase();

      arr.forEach((token: Token) => {
        token.keys.forEach((key: Key) => {
          // Filter the certificates
          const certs = key.certificates.filter((cert: TokenCertificate) => {
            if (cert.owner_id) {
              return cert.owner_id.toLowerCase().includes(mysearch);
            }
            return false;
          });
          key.certificates = certs;

          // Filter the CSR:s
          const csrs = key.certificate_signing_requests.filter(
            (csr: TokenCertificateSigningRequest) => {
              if (csr.id) {
                return csr.id.toLowerCase().includes(mysearch);
              }
              return false;
            },
          );
          key.certificate_signing_requests = csrs;
        });
      });

      arr.forEach((token: Token) => {
        const keys = token.keys.filter((key: Key) => {
          if (key.certificates && key.certificates.length > 0) {
            return true;
          }

          if (
            key.certificate_signing_requests &&
            key.certificate_signing_requests.length > 0
          ) {
            return true;
          }

          if (key.name) {
            return key.name.toLowerCase().includes(mysearch);
          }
          if (key.id) {
            return key.id.toLowerCase().includes(mysearch);
          }
          return false;
        });
        token.keys = keys;
      });

      arr = arr.filter((token: Token) => {
        if (token.keys && token.keys.length > 0) {
          return true;
        }

        return token.name.toLowerCase().includes(mysearch);
      });

      return arr;
    },
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useTokens, ['fetchTokens', 'tokenLogout']),
    fetchData(): void {
      // Fetch tokens from backend
      this.loading = true;
      this.fetchTokens()
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    acceptTokenLogout(): void {
      if (!this.selectedToken) {
        // eslint-disable-next-line no-console
        console.error('Token is undefined');
        return;
      }

      this.tokenLogout(this.selectedToken.id).then(
        () => {
          this.showSuccess(this.$t('keys.loggedOut'));
        },
        (error) => {
          this.showError(error);
        },
      );

      this.logoutDialog = false;
    },
    tokenLogin(): void {
      this.fetchData();
      this.loginDialog = false;
    },
    addKey() {
      if (!this.selectedToken) {
        // Should not happen
        throw new Error('Token is undefined');
      }

      this.$router.push({
        name: RouteName.AddKey,
        params: {
          tokenId: this.selectedToken.id,
          tokenType: this.selectedToken.type,
        },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
.title-and-search {
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  margin-bottom: 40px;
}

.search-row {
  margin-left: 20px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}
</style>
