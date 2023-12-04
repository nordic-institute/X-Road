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
    title="systemParameters.securityServer.editDialog.title"
    data-test="security-server-address-edit-dialog"
    save-button-text="action.save"
    :scrollable="false"
    :show-close="true"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="saveAddress"
    @cancel="close"
  >
    <template #content>
      <v-text-field
        v-bind="securityServerAddress"
        data-test="security-server-address-edit-field"
        :label="$t('fields.securityServerAddress')"
        autofocus
        variant="outlined"
        class="dlg-row-input"
        name="securityServerAddress"
        :error-messages="errors.securityServerAddress"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import { useNotifications } from '@/store/modules/notifications';
import { useForm } from 'vee-validate';
import { mapActions } from 'pinia';

export default defineComponent({
  props: {
    address: {
      type: String,
      required: true,
    },
  },
  emits: ['cancel', 'addressUpdated'],
  setup(props) {
    const {
      values,
      errors,
      meta,
      resetForm,
      setFieldError,
      defineComponentBinds,
    } = useForm({
      validationSchema: {
        securityServerAddress: 'required|max:255',
      },
      initialValues: { securityServerAddress: props.address },
    });
    const securityServerAddress = defineComponentBinds('securityServerAddress');
    return {
      values,
      meta,
      errors,
      resetForm,
      setFieldError,
      securityServerAddress,
    };
  },
  data() {
    return {
      loading: false,
    };
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.resetForm();
      this.$emit('cancel');
    },
    saveAddress: async function () {
      this.loading = true;
      api
        .put('/system/server-address', { address: this.values.securityServerAddress })
        .then(() => {
          this.showSuccess(this.$t('systemParameters.securityServer.updateSubmitted'));
          this.$emit('addressUpdated');
        })
        .catch((error) => {
          this.showError(error);
          this.close();
        })
        .finally(() => this.loading = false)
    },
  },
});
</script>

<style lang="scss" scoped></style>
