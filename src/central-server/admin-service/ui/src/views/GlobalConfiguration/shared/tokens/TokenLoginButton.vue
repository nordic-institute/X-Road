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
    <XrdBtn
      v-if="showLogin"
      data-test="token-login-button"
      text="keys.logIn"
      variant="text"
      :disabled="!token.available"
      @click="showLoginDialog = true"
    />

    <XrdBtn v-if="showLogout" data-test="token-logout-button" text="keys.logOut" variant="text" @click="showLogoutDialog = true" />

    <TokenLoginDialog v-if="showLoginDialog" :token="token" @cancel="showLoginDialog = false" @token-login="tokenLoggedIn" />

    <TokenLogoutDialog v-if="showLogoutDialog" :token="token" @cancel="showLogoutDialog = false" @token-logout="tokenLoggedOut" />
  </div>
</template>

<script lang="ts" setup>
import { PropType, ref, computed } from 'vue';

import { XrdBtn } from '@niis/shared-ui';

import { Permissions } from '@/global';
import { PossibleTokenAction, Token } from '@/openapi-types';
import { useUser } from '@/store/modules/user';

import TokenLoginDialog from './dialogs/TokenLoginDialog.vue';
import TokenLogoutDialog from './dialogs/TokenLogoutDialog.vue';

const props = defineProps({
  token: {
    type: Object as PropType<Token>,
    required: true,
  },
});

const emit = defineEmits(['token-login', 'token-logout']);

defineExpose({
  openLogin(): boolean {
    if (showLogin.value && props.token?.available) {
      showLoginDialog.value = true;
      return true;
    }
    return false;
  },
});

const { hasPermission } = useUser();

const showLoginDialog = ref(false);
const showLogoutDialog = ref(false);

const showLogin = computed(() => {
  if (!props.token.possible_actions) {
    return false;
  }
  return hasPermission(Permissions.ACTIVATE_TOKEN) && props.token.possible_actions.includes(PossibleTokenAction.LOGIN);
});

const showLogout = computed(() => {
  if (!props.token.possible_actions) {
    return false;
  }
  return hasPermission(Permissions.DEACTIVATE_TOKEN) && props.token.possible_actions.includes(PossibleTokenAction.LOGOUT);
});

function tokenLoggedIn(): void {
  showLoginDialog.value = false;
  emit('token-login');
}

function tokenLoggedOut(): void {
  showLogoutDialog.value = false;
  emit('token-logout');
}
</script>
