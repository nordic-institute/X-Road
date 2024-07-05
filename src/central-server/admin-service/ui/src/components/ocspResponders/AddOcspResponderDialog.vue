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
    title="trustServices.trustService.ocspResponders.add.dialog.title"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    submittable
    :disable-save="!meta.valid"
    :loading="loading"
    @cancel="cancel"
    @save="add"
  >
    <template #content>
      <div class="space-out-bottom dlg-input-width">
        <v-text-field
          v-model="ocspUrl"
          v-bind="ocspUrlAttrs"
          variant="outlined"
          data-test="ocsp-responder-url-input"
          persistent-hint
          autofocus
          :label="$t('trustServices.trustService.ocspResponders.url')"
        />
      </div>

      <div class="dlg-input-width">
        <CertificateFileUpload
          v-model:file="certFile"
          data-test="ocsp-responder-file-input"
          label-key="trustServices.uploadCertificate"
        />
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useOcspResponderService } from '@/store/modules/trust-services';
import { useForm } from 'vee-validate';
import CertificateFileUpload from '@/components/ui/CertificateFileUpload.vue';
import { useBasicForm, useFileRef } from '@/util/composables';

const emits = defineEmits(['save', 'cancel']);

const { defineField, handleSubmit, meta } = useForm({
  validationSchema: { url: 'required|url' },
  initialValues: { url: '' },
});

const [ocspUrl, ocspUrlAttrs] = defineField('url', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { showSuccess, showError, t, loading } = useBasicForm();
const { addOcspResponder } = useOcspResponderService();

const certFile = useFileRef();

const add = handleSubmit((values) => {
  loading.value = true;
  addOcspResponder(values.url, certFile.value)
    .then(() => {
      showSuccess(t('trustServices.trustService.ocspResponders.add.success'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});

function cancel() {
  emits('cancel');
}
</script>
