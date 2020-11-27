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
    <v-btn
      min-width="120px"
      v-if="!token.logged_in"
      :color="styles.color"
      :disabled="!token.available"
      @click="confirmLogin()"
      data-test="token-login-button"
      >{{ $t('keys.logIn') }}
    </v-btn>

    <v-btn
      active-class="'test'"
      outlined="outlined"
      min-width="120px"
      v-if="token.logged_in"
      :color="styles.color"
      @click="confirmLogout()"
      data-test="token-logout-button"
      >{{ $t('keys.logOut') }}
    </v-btn>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Token } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import {
  getTokenUIStatus,
  TokenUIStatus,
} from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenStatusHelper';

interface ButtonStyles {
  class: string;
  color: string;
}

export default Vue.extend({
  props: {
    token: {
      type: Object as Prop<Token>,
      required: true,
    },
  },
  data: function () {
    return {
      styles: {
        color: 'primary',
        class: '',
      } as ButtonStyles,
    };
  },
  methods: {
    confirmLogout(): void {
      this.$emit('token-logout');
    },
    confirmLogin(): void {
      this.$emit('token-login');
    },
    getButtonStyles(token: Token): void {
      const status: TokenUIStatus = getTokenUIStatus(token.status);

      if (status === TokenUIStatus.Available) {
        this.styles.color = 'primary';
      } else if (status === TokenUIStatus.Active) {
        this.styles.color = 'primary';
      } else if (status === TokenUIStatus.Unavailable) {
        this.styles.color = 'error';
      } else if (status === TokenUIStatus.Unsaved) {
        this.styles.color = 'error';
      } else if (status === TokenUIStatus.Inactive) {
        this.styles.color = 'grey';
      }
    },
  },
  created() {
    this.getButtonStyles(this.token);
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
.grey-background {
  background-color: $XRoad-Grey10;
}

.large-button {
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
