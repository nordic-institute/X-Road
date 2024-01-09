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
    submittable
    :scrollable="false"
    :show-close="true"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="saveAddress"
    @cancel="close"
  >
    <template #content>
      <v-text-field
        v-model="securityServerAddress"
        v-bind="securityServerAddressAttrs"
        data-test="security-server-address-edit-field"
        autofocus
        variant="outlined"
        class="dlg-row-input"
        name="securityServerAddress"
        :label="$t('securityServers.dialogs.editAddress.addressField')"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
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
import i18n from '@/plugins/i18n';

/**
 * Component for a Security server details view
 */

const props = defineProps({
  securityServerId: {
    type: String,
    required: true,
  },
  address: {
    type: String,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const {
  meta,
  resetForm,
  setFieldError,
  defineField,
  handleSubmit
} = useForm({
  validationSchema: {
    securityServerAddress: 'required|address',
  },
  initialValues: { securityServerAddress: props.address },
});
const [securityServerAddress, securityServerAddressAttrs] = defineField(
  'securityServerAddress',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);

const { updateAddress } = useSecurityServer();
const { showError, showSuccess } = useNotifications();

function close() {
  resetForm();
  emits('cancel');
}

const loading = ref(false);
const { t } = i18n.global;
const saveAddress = handleSubmit((values) => {
    loading.value = true;
    updateAddress(
      props.securityServerId,
      values.securityServerAddress,
    ).then(() => {
      showSuccess(t('securityServers.dialogs.editAddress.success'));
      emits('save');
    })
      .catch((updateError) => {
        const errorInfo: ErrorInfo = getErrorInfo(updateError as AxiosError);
        if (isFieldError(errorInfo)) {
          // backend validation error
          let fieldErrors = errorInfo.error?.validation_errors;
          if (fieldErrors) {
            setFieldError(
              'securityServerAddress',
              getTranslatedFieldErrors(
                'securityServerAddressDto.serverAddress',
                fieldErrors,
              ),
            );
          }
        } else {
          showError(updateError);
          close();
        }
      })
      .finally(() => (loading.value = false));
  }
);
</script>

<style lang="scss" scoped></style>
