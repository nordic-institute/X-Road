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
    title="keys.dialog.add.title"
    save-button-text="keys.dialog.add.confirmButton"
    :loading="loading"
    :disable-save="!isValid"
    @save="addKey"
    @cancel="cancel"
  >
    <div slot="content">
      <div class="pt-5 dlg-input-width">
        <ValidationProvider
          ref="keyLabel"
          v-slot="{ errors }"
          rules="required|min:1|max:255"
          name="keyLabel"
          class="validation-provider"
        >
          <v-text-field
            v-model="label"
            :label="$t('keys.dialog.add.labelField')"
            :error-messages="errors"
            name="keyLabel"
            data-test="signing-key-label-input"
            outlined
            autofocus
          />
        </ValidationProvider>
      </div>
    </div>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapActions, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { useSigningKeyStore } from '@/store/modules/signing-keys';
import { ConfigurationSigningKey, ConfigurationType } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import { ValidationProvider } from 'vee-validate';

export default Vue.extend({
  components: { ValidationProvider },
  props: {
    configurationType: {
      type: String as Prop<ConfigurationType>,
      required: true,
    },
    tokenId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      label: '',
    };
  },
  computed: {
    ...mapStores(useSigningKeyStore),
    isValid(): boolean {
      return this.label?.length > 0 && this.label?.length < 256;
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancel(): void {
      this.label = '';
      this.$emit('cancel');
    },
    addKey(): void {
      this.loading = true;
      this.signingKeyStore
        .addSigningKey(this.configurationType, {
          key_label: this.label,
          token_id: this.tokenId,
        })
        .then((res) => {
          const key: ConfigurationSigningKey = res.data;
          this.showSuccess(
            this.$t('keys.dialog.add.success', {
              label: key?.label?.label || key.id,
            }),
          );
          this.$emit('key-add');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
