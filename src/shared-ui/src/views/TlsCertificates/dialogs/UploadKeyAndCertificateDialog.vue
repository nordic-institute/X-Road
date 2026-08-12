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
  <XrdSimpleDialog
    ref="dialog"
    title="tlsCertificates.uploadKeyAndCertificate.title"
    save-button-text="action.upload"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="!keyFile || !certificateFile"
    @save="upload"
    @cancel="emit('cancel')"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <XrdFileUploadField
            v-model:file="keyFile"
            autofocus
            accept=".pem, .key"
            label="tlsCertificates.uploadKeyAndCertificate.keyLabel"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow full-length>
          <XrdCertificateFileUpload v-model:file="certificateFile" label="tlsCertificates.uploadKeyAndCertificate.certificateLabel" />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { XrdSimpleDialog, XrdFormBlock, XrdFormBlockRow, XrdCertificateFileUpload, XrdFileUploadField } from '../../../components';
import { useBasicForm, useFileRef } from '../../../composables';
import { TlsCertificatesHandler, DialogSaveHandler } from '../../../types';

import { PropType } from 'vue';

const props = defineProps({
  handler: {
    type: Object as PropType<TlsCertificatesHandler>,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'upload']);

const { loading, addSuccessMessage } = useBasicForm();
const keyFile = useFileRef();
const certificateFile = useFileRef();

function upload(evt: Event, handler: DialogSaveHandler): void {
  if (!keyFile.value || !certificateFile.value || !props.handler.uploadKeyAndCertificate) {
    return;
  }
  loading.value = true;
  props.handler
    .uploadKeyAndCertificate(keyFile.value, certificateFile.value)
    .then(() => {
      addSuccessMessage('tlsCertificates.uploadKeyAndCertificate.success');
      emit('upload');
    })
    .catch((error) => {
      handler.addError(error);
    })
    .finally(() => (loading.value = false));
}
</script>
