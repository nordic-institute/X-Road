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
  <xrd-confirm-dialog
    :dialog="true"
    :loading="loading"
    :data="label"
    accept-button-text="keys.dialog.delete.confirmButton"
    title="keys.dialog.delete.title"
    text="keys.dialog.delete.text"
    @cancel="cancel"
    @accept="confirmDelete"
  />
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useSigningKey } from '@/store/modules/signing-keys';
import { ConfigurationSigningKey } from '@/openapi-types';

export default defineComponent({
  props: {
    signingKey: {
      type: Object as PropType<ConfigurationSigningKey>,
      required: true,
    },
  },
  emits: ['cancel', 'key-delete'],
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useSigningKey),
    label() {
      const key: ConfigurationSigningKey = this.signingKey;
      return { label: key?.label?.label || key.id };
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
    },
    confirmDelete(): void {
      this.loading = true;
      this.signingKeyStore
        .deleteSigningKey(this.signingKey.id)
        .then(() => {
          this.showSuccess(this.$t('keys.dialog.delete.success'));
          this.$emit('key-delete');
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
