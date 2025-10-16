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
      v-if="!token.logged_in"
      data-test="token-login-button"
      variant="text"
      text="keys.logIn"
      prepend-icon="login"
      :disabled="!token.available"
      @click="confirmLogin()"
    />

    <XrdBtn
      v-if="token.logged_in"
      data-test="token-logout-button"
      variant="text"
      text="keys.logOut"
      prepend-icon="logout"
      @click="confirmLogout()"
    />
  </div>
</template>

<script lang="ts" setup>
import { PropType } from 'vue';
import { Token } from '@/openapi-types';
import { XrdBtn } from '@niis/shared-ui';

defineProps({
  token: {
    type: Object as PropType<Token>,
    required: true,
  },
});

const emit = defineEmits(['token-logout', 'token-login']);

function confirmLogout(): void {
  emit('token-logout');
}

function confirmLogin(): void {
  emit('token-login');

}
</script>
