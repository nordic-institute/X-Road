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
    <xrd-button
      v-if="showLogin"
      min-width="120px"
      :outlined="false"
      text
      :disabled="!token.available"
      data-test="token-login-button"
      @click="showLoginDialog = true"
      >{{ $t('keys.logIn') }}
    </xrd-button>

    <xrd-button
      v-if="showLogout"
      min-width="120px"
      :outlined="false"
      text
      data-test="token-logout-button"
      @click="showLogoutDialog = true"
      >{{ $t('keys.logOut') }}
    </xrd-button>

    <TokenLoginDialog
      v-if="showLoginDialog"
      :token="token"
      @cancel="showLoginDialog = false"
      @token-login="tokenLoggedIn"
    />

    <TokenLogoutDialog
      v-if="showLogoutDialog"
      :token="token"
      @cancel="showLogoutDialog = false"
      @token-logout="tokenLoggedOut"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import TokenLogoutDialog from '@/components/tokens/TokenLogoutDialog.vue';
import TokenLoginDialog from '@/components/tokens/TokenLoginDialog.vue';
import { PossibleTokenAction, Token } from '@/openapi-types';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { Permissions } from '@/global';

export default defineComponent({
  components: { TokenLogoutDialog, TokenLoginDialog },
  props: {
    token: {
      type: Object as PropType<Token>,
      required: true,
    },
  },
  emits: ['token-login', 'token-logout'],
  data() {
    return {
      showLoginDialog: false,
      showLogoutDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showLogin(): boolean {
      if (!this.token.possible_actions) {
        return false;
      }
      return (
        this.hasPermission(Permissions.ACTIVATE_TOKEN) &&
        this.token.possible_actions.includes(PossibleTokenAction.LOGIN)
      );
    },
    showLogout(): boolean {
      if (!this.token.possible_actions) {
        return false;
      }
      return (
        this.hasPermission(Permissions.DEACTIVATE_TOKEN) &&
        this.token.possible_actions.includes(PossibleTokenAction.LOGOUT)
      );
    },
  },
  methods: {
    tokenLoggedIn(): void {
      this.showLoginDialog = false;
      this.$emit('token-login');
    },
    tokenLoggedOut(): void {
      this.showLogoutDialog = false;
      this.$emit('token-logout');
    },
  },
});
</script>
