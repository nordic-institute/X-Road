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
    v-if="dialog"
    title="login.logIn"
    save-button-text="login.logIn"
    :disable-save="!meta.valid"
    :loading="loading"
    width="620"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="pt-5 dlg-input-width">
        <v-text-field
          v-model="tokenPin"
          type="password"
          variant="outlined"
          :label="$t('fields.tokenPin')"
          autofocus
          name="tokenPin"
          :error-messages="errors"
          @keyup.enter="save"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState } from 'pinia';
import { Token } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { useAlerts } from '@/store/modules/alerts';
import { useNotifications } from '@/store/modules/notifications';
import { useTokens } from '@/store/modules/tokens';
import { useField } from 'vee-validate';

export default defineComponent({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['cancel', 'save'],
  setup() {
    const { value, meta, errors, setErrors, resetField } = useField(
      'tokenPin',
      'required',
      { initialValue: '' },
    );
    return { tokenPin: value, meta, errors, setErrors, resetField };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapState(useTokens, ['selectedToken']),
  },
  methods: {
    ...mapActions(useAlerts, ['checkAlertStatus']),
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
      this.resetField();
    },
    save(): void {
      if (!this.selectedToken) {
        // eslint-disable-next-line no-console
        console.error('Selected token missing');
        return;
      }
      const token: Token = this.selectedToken;

      this.loading = true;
      api
        .put(`/tokens/${encodePathParameter(token.id)}/login`, {
          password: this.tokenPin,
        })
        .then(() => {
          this.loading = false;
          this.showSuccess(this.$t('keys.loggedIn'));
          this.$emit('save');
        })
        .catch((error) => {
          this.loading = false;
          if (
            error.response.status === 400 &&
            error.response.data.error.code === 'pin_incorrect'
          ) {
            this.setErrors(this.$t('keys.incorrectPin'));
          }
          this.showError(error);
        })
        .finally(() => this.checkAlertStatus());

      this.resetField();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/dialogs';
</style>
