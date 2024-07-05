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
    title="systemSettings.editCentralServerAddressTitle"
    data-test="system-settings-central-server-address-edit-dialog"
    save-button-text="action.save"
    submittable
    :scrollable="false"
    :show-close="true"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="onServerAddressSave"
    @cancel="onCancelAddressEdit"
  >
    <template #content>
      <v-text-field
        v-model="address"
        v-bind="addressAttrs"
        data-test="system-settings-central-server-address-edit-field"
        variant="outlined"
        class="dlg-row-input"
        name="serviceAddress"
        autofocus
        :label="$t('systemSettings.centralServerAddress')"
      />
    </template>
  </xrd-simple-dialog>
</template>
<script lang="ts" setup>
import { useForm } from 'vee-validate';
import { useSystem } from '@/store/modules/system';
import { useBasicForm } from '@/util/composables';

const props = defineProps({
  serviceAddress: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['save', 'cancel']);

const { updateCentralServerAddress } = useSystem();

const { meta, setFieldError, defineField, handleSubmit } = useForm({
  validationSchema: { serviceAddress: 'required' },
  initialValues: { serviceAddress: props.serviceAddress },
});
const [address, addressAttrs] = defineField('serviceAddress', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const { showOrTranslateErrors, showSuccess, t, loading } = useBasicForm(
  setFieldError,
  { serviceAddress: 'centralServerAddressDto.centralServerAddress' },
);

const onServerAddressSave = handleSubmit((values) => {
  loading.value = true;
  updateCentralServerAddress({
    central_server_address: values.serviceAddress,
  })
    .then(() => {
      showSuccess(t('systemSettings.editCentralServerAddressSuccess'));
      emit('save');
    })
    .catch((error) => showOrTranslateErrors(error))
    .finally(() => (loading.value = false));
});

function onCancelAddressEdit() {
  emit('cancel');
}
</script>
