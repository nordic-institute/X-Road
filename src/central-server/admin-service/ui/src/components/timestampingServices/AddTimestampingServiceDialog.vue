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
    title="trustServices.timestampingService.dialog.add.title"
    save-button-text="action.add"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="!canAdd"
    @save="save"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <div class="dlg-input-width space-out-bottom">
        <v-text-field
          v-model="tasUrl"
          v-bind="tasUrlAttrs"
          variant="outlined"
          data-test="timestamping-service-url-input"
          autofocus
          persistent-hint
          :label="$t('trustServices.timestampingService.url')"
        ></v-text-field>
      </div>

      <div class="dlg-input-width">
        <CertificateFileUpload
          v-model:file="certFile"
          data-test="timestamping-service-file-input"
          label-key="trustServices.uploadCertificate"
        />
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useTimestampingServicesStore } from '@/store/modules/trust-services';
import { useForm } from 'vee-validate';
import CertificateFileUpload from '@/components/ui/CertificateFileUpload.vue';
import { useBasicForm, useFileRef } from '@/util/composables';

const emits = defineEmits(['save', 'cancel']);

const { meta, defineField, handleSubmit } = useForm({
  validationSchema: { url: 'required|url' },
});
const [tasUrl, tasUrlAttrs] = defineField('url', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { showSuccess, showError, t, loading } = useBasicForm();
const { addTimestampingService } = useTimestampingServicesStore();

const certFile = useFileRef();
const canAdd = computed(() => meta.value.valid && certFile.value);

const save = handleSubmit((values) => {
  if (!certFile.value) {
    return;
  }

  loading.value = true;
  addTimestampingService(values.url, certFile.value)
    .then(() => {
      showSuccess(t('trustServices.timestampingService.dialog.add.success'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>
