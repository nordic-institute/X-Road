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
    title="securityServers.dialogs.editDsConfig.title"
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
      <v-checkbox
        v-model="securityServerDsEnabled"
        v-bind="securityServerDsEnabledAttrs"
        data-test="security-server-ds-enable-checkbox"
        autofocus
        variant="outlined"
        class="dlg-row-input"
        name="securityServerDsProtocol"
        :label="$t('securityServers.dialogs.editDs.enabled')"
      />
      <v-text-field
        v-model="securityServerDsProtocol"
        v-bind="securityServerDsProtocolAttrs"
        data-test="security-server-ds-protocol-edit-field"
        autofocus
        variant="outlined"
        class="dlg-row-input"
        name="securityServerDsProtocol"
        :label="$t('securityServers.dialogs.editDs.protocolUrl')"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useSecurityServer } from '@/store/modules/security-servers';
import { useForm } from 'vee-validate';
import { useBasicForm } from '@/util/composables';
import { SecurityServerDataSpaceConfig } from '@/openapi-types';
import { PropType } from 'vue';

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
  dsConfig: {
    type: Object as PropType<SecurityServerDataSpaceConfig>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const { meta, resetForm, setFieldError, defineField, handleSubmit } = useForm({
  validationSchema: {
    securityServerDsEnabled: '',
    securityServerDsProtocol: 'required',
  },
  initialValues: {
    securityServerDsEnabled: props.dsConfig.ds_enabled,
    securityServerDsProtocol: props.dsConfig.protocol_url,
  },
});
const [securityServerDsProtocol, securityServerDsProtocolAttrs] = defineField(
  'securityServerDsProtocol',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);
const [securityServerDsEnabled, securityServerDsEnabledAttrs] = defineField(
  'securityServerDsEnabled',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);
const { updateAddress } = useSecurityServer();
const { showOrTranslateErrors, showSuccess, loading, t } = useBasicForm(
  setFieldError,
  { securityServerDsProtocol: 'securityServerDsProtocolDto.serverAddress' },
);

function close() {
  resetForm();
  emits('cancel');
}

const saveAddress = handleSubmit((values) => {
  loading.value = true;
  updateAddress(
    props.securityServerId,
    props.address,
    values.securityServerDsEnabled,
    values.securityServerDsProtocol!,
  )
    .then(() => {
      showSuccess(t('securityServers.dialogs.editAddress.success'));
      emits('save');
    })
    .catch((error) => showOrTranslateErrors(error))
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
