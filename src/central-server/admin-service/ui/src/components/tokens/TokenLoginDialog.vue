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
  <xrd-simple-dialog
    :dialog="true"
    title="tokens.loginDialog.title"
    save-button-text="tokens.logIn"
    :disable-save="!isValid"
    :loading="loading"
    @save="login"
    @cancel="cancel"
  >
    <div slot="content">
      <div class="pt-5 dlg-input-width">
        <ValidationProvider
          ref="tokenPin"
          v-slot="{ errors }"
          rules="required"
          name="tokenPin"
          class="validation-provider"
        >
          <v-text-field
            v-model="pin"
            :label="$t('tokens.pin')"
            :error-messages="errors"
            type="password"
            name="tokenPin"
            data-test="token-pin-input"
            outlined
            autofocus
          ></v-text-field>
        </ValidationProvider>
      </div>
    </div>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider } from 'vee-validate';
import { mapActions } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { tokenStore } from '@/store/modules/tokens';
import { Prop } from 'vue/types/options';
import { Token } from '@/openapi-types';

export default Vue.extend({
  name: 'TokenLoginDialog',
  components: { ValidationProvider },
  props: {
    token: {
      type: Object as Prop<Token>,
      required: true,
    },
  },
  data() {
    return {
      pin: '',
      loading: false,
    };
  },
  computed: {
    isValid(): boolean {
      return this.pin?.length > 0;
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    ...mapActions(tokenStore, ['loginToken']),
    login(): void {
      this.loading = true;
      this.loginToken(this.token.id, { password: this.pin })
        .then(() => {
          this.showSuccess(this.$t('tokens.loginDialog.success'));
          this.$emit('token-login');
        })
        .catch((error) => {
          const metadata: string[] = error.response?.data?.error?.metadata;
          if (metadata && metadata.length > 0) {
            (
              this.$refs.tokenPin as InstanceType<typeof ValidationProvider>
            ).setErrors(
              metadata.map(
                (code) => this.$t('tokens.errors.' + code) as string,
              ),
            );
          }
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    cancel(): void {
      this.loading = false;
      this.pin = '';
      this.$emit('cancel');
    },
  },
});
</script>

<style lang="scss" scoped></style>
