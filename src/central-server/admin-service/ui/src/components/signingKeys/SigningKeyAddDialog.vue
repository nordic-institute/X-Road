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
    title="keys.dialog.add.title"
    save-button-text="keys.dialog.add.confirmButton"
    :loading="loading"
    :disable-save="!meta.valid"
    @save="addKey"
    @cancel="cancel"
  >
    <template #content>
      <v-text-field
        v-bind="keyLabel"
        :label="$t('keys.dialog.add.labelField')"
        :error-messages="errors.keyLabel"
        name="keyLabel"
        data-test="signing-key-label-input"
        variant="outlined"
        autofocus
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useSigningKey } from '@/store/modules/signing-keys';
import { ConfigurationSigningKey, ConfigurationType } from '@/openapi-types';
import { useForm } from 'vee-validate';

export default defineComponent({
  props: {
    configurationType: {
      type: String as PropType<ConfigurationType>,
      required: true,
    },
    tokenId: {
      type: String,
      required: true,
    },
  },
  emits: ['cancel', 'key-add'],
  setup() {
    const { defineComponentBinds, errors, values, meta } = useForm({
      validationSchema: { keyLabel: 'required|min:1|max:255' },
    });
    const keyLabel = defineComponentBinds('keyLabel');
    return { errors, values, meta, keyLabel };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useSigningKey),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
    },
    addKey(): void {
      this.loading = true;
      this.signingKeyStore
        .addSigningKey(this.configurationType, {
          key_label: this.values.keyLabel,
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
