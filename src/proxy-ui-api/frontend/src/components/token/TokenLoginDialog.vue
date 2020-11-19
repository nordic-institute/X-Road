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
  <simpleDialog
    :dialog="dialog"
    title="login.logIn"
    @save="save"
    @cancel="cancel"
    saveButtonText="login.logIn"
    :disableSave="!isValid"
    :loading="loading"
  >
    <div slot="content">
      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{ $t('fields.tokenPin') }}</div>
        <ValidationProvider
          rules="required"
          ref="tokenPin"
          name="tokenPin"
          v-slot="{ errors }"
          class="validation-provider"
        >
          <v-text-field
            type="password"
            v-model="pin"
            single-line
            autofocus
            class="dlg-row-input"
            name="tokenPin"
            :error-messages="errors"
            v-on:keyup.enter="save"
          ></v-text-field>
        </ValidationProvider>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider } from 'vee-validate';
import { Token } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: { ValidationProvider },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  computed: {
    isValid(): boolean {
      // Check that input is not empty
      if (this.pin && this.pin.length > 0) {
        return true;
      }
      return false;
    },
  },

  data() {
    return {
      pin: '',
      loading: false,
    };
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      const token: Token = this.$store.getters.selectedToken;

      if (!token) {
        return;
      }

      this.loading = true;
      api
        .put(`/tokens/${encodePathParameter(token.id)}/login`, {
          password: this.pin,
        })
        .then(() => {
          this.loading = false;
          this.$store.dispatch('showSuccess', 'keys.loggedIn');
          this.$emit('save');
        })
        .catch((error) => {
          this.loading = false;
          if (
            error.response.status === 400 &&
            error.response.data.error.code === 'pin_incorrect'
          ) {
            (this.$refs.tokenPin as InstanceType<
              typeof ValidationProvider
            >).setErrors([this.$t('keys.incorrectPin') as string]);
          }

          this.$store.dispatch('showError', error);
        })
        .finally(() => this.$store.dispatch('checkAlertStatus'));

      this.clear();
    },
    clear(): void {
      this.pin = '';
      (this.$refs.tokenPin as InstanceType<typeof ValidationProvider>).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/dialogs';
</style>
