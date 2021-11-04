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
    <div class="title-and-search">
      <div class="xrd-view-title">{{ $t('tab.keys.signAndAuthKeys') }}</div>
      <div>
        <help-button
          help-image="keys_and_certificates.png"
          help-title="keys.helpTitleKeys"
          help-text="keys.helpTextKeys"
        ></help-button>
      </div>
      <div class="search-row">
        <xrd-search v-model="search" />
      </div>
    </div>
    <div v-if="filtered && filtered.length < 1">
      {{ $t('services.noMatches') }}
    </div>

    <template v-if="filtered">
      <token-expandable
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
      :dialog="logoutDialog"
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
  </div>
</template>

<script lang="ts">
// View for keys tab
import Vue from 'vue';
import { RouteName } from '@/global';
import TokenExpandable from './TokenExpandable.vue';
import TokenLoginDialog from '@/components/token/TokenLoginDialog.vue';
import HelpButton from '../HelpButton.vue';
import { mapGetters } from 'vuex';
import { Key, Token, TokenCertificate } from '@/openapi-types';
import { deepClone } from '@/util/helpers';

export default Vue.extend({
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
    };
  },
  computed: {
    ...mapGetters(['tokens']),
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

      if (!this.search) {
        return arr;
      }

      const mysearch = this.search.toLowerCase();

      if (mysearch.length < 1) {
        return this.tokens;
      }

      arr.forEach((token: Token) => {
        token.keys.forEach((key: Key) => {
          const certs = key.certificates.filter((cert: TokenCertificate) => {
            if (cert.owner_id) {
              return cert.owner_id.toLowerCase().includes(mysearch);
            }
            return false;
          });
          key.certificates = certs;
        });
      });

      arr.forEach((token: Token) => {
        const keys = token.keys.filter((key: Key) => {
          if (key.certificates && key.certificates.length > 0) {
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
    fetchData(): void {
      // Fetch tokens from backend
      this.$store.dispatch('fetchTokens').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
    acceptTokenLogout(): void {
      const token: Token = this.$store.getters.selectedToken;

      if (!token) {
        return;
      }

      this.$store.dispatch('tokenLogout', token.id).then(
        () => {
          this.$store.dispatch('showSuccess', this.$t('keys.loggedOut'));
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );

      this.logoutDialog = false;
    },
    tokenLogin(): void {
      this.fetchData();
      this.loginDialog = false;
    },
    addKey() {
      this.$router.push({
        name: RouteName.AddKey,
        params: {
          tokenId: this.$store.getters.selectedToken.id,
          tokenType: this.$store.getters.selectedToken.type,
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
