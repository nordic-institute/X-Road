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
<!--
  Member details view
-->
<template>
  <xrd-simple-dialog
    title="securityServers.dialogs.editAddress.title"
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
        :label="$t('securityServers.dialogs.editAddress.addressField')"
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
import { useSecurityServer } from '@/store/modules/security-servers';
import { useNotifications } from '@/store/modules/notifications';
import { ErrorInfo } from '@/openapi-types';
import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import { AxiosError } from 'axios';
import { useForm } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';

/**
 * Component for a Security server details view
 */
export default defineComponent({
  props: {
    securityServerId: {
      type: String,
      required: true,
    },
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
        securityServerAddress: 'required',
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
      showDialog: false,
    };
  },
  computed: {
    ...mapStores(useSecurityServer),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.resetForm();
      this.$emit('cancel');
    },
    saveAddress: async function () {
      try {
        this.loading = true;
        await this.securityServerStore.updateAddress(
          this.securityServerId,
          this.values.securityServerAddress,
        );
        this.showSuccess(
          this.$t('securityServers.dialogs.editAddress.success'),
        );
        this.$emit('addressUpdated');
      } catch (updateError: unknown) {
        const errorInfo: ErrorInfo = getErrorInfo(updateError as AxiosError);
        if (isFieldError(errorInfo)) {
          // backend validation error
          let fieldErrors = errorInfo.error?.validation_errors;
          if (fieldErrors) {
            this.setFieldError(
              'securityServerAddress',
              getTranslatedFieldErrors(
                'securityServerAddressDto.serverAddress',
                fieldErrors,
              ),
            );
          }
        } else {
          this.showError(updateError);
          this.close();
        }
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped></style>
