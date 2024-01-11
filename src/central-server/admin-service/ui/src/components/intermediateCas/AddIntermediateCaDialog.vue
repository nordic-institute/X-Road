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
    title="trustServices.trustService.intermediateCas.add.dialog.title"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    submittable
    :disable-save="!certFile"
    :loading="loading"
    @cancel="$emit('cancel')"
    @save="uploadCertificate"
  >
    <template #content>
      <div class="dlg-input-width">
        <CertificateFileUpload
          v-model:file="certFile"
          data-test="add-intermediate-ca-cert-input"
          label-key="trustServices.uploadCertificate"
          autofocus
        />
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useIntermediateCasService } from '@/store/modules/trust-services';
import CertificateFileUpload from '@/components/ui/CertificateFileUpload.vue';
import { useBasicForm, useFileRef } from '@/util/composables';

const emits = defineEmits(['save', 'cancel']);

const { addIntermediateCa } = useIntermediateCasService();
const { showSuccess, showError, t, loading } = useBasicForm();

const certFile = useFileRef();

function uploadCertificate() {
  loading.value = true;
  if (!certFile.value) {
    throw new Error('Certificate is null');
  }
  addIntermediateCa(certFile.value)
    .then(() => {
      showSuccess(t('trustServices.trustService.intermediateCas.add.success'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
}
</script>
