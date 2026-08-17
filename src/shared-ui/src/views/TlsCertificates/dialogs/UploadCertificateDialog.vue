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
    title="tlsCertificates.uploadCertificate.title"
    save-button-text="action.upload"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="!certFile || (requireKeyFile && !keyFile)"
    @save="upload"
    @cancel="emit('cancel')"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow v-if="requireKeyFile" full-length>
          <XrdCertificateFileUpload v-model:file="keyFile" autofocus label="tlsCertificates.uploadCertificate.keyLabel" />
        </XrdFormBlockRow>
        <XrdFormBlockRow full-length>
          <XrdCertificateFileUpload v-model:file="certFile" :autofocus="!requireKeyFile" label="tlsCertificates.uploadCertificate.label" />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { XrdSimpleDialog, XrdFormBlock, XrdFormBlockRow, XrdCertificateFileUpload } from '../../../components';
import { useBasicForm, useFileRef } from '../../../composables';
import { TlsCertificatesHandler, DialogSaveHandler } from '../../../types';

import { PropType } from 'vue';

const props = defineProps({
  handler: {
    type: Object as PropType<TlsCertificatesHandler>,
    required: true,
  },
  // When true, also collects a private key file and passes it to the handler alongside
  // the certificate - used for views where the operator brings their own key.
  requireKeyFile: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['cancel', 'upload']);

const { loading, addSuccessMessage } = useBasicForm();
const certFile = useFileRef();
const keyFile = useFileRef();

function upload(evt: Event, handler: DialogSaveHandler): void {
  if (!certFile.value || (props.requireKeyFile && !keyFile.value)) {
    return;
  }
  loading.value = true;
  props.handler
    .uploadCertificate(certFile.value, props.requireKeyFile ? keyFile.value : undefined)
    .then(() => {
      addSuccessMessage('tlsCertificates.uploadCertificate.success');
      emit('upload');
    })
    .catch((error) => {
      handler.addError(error);
    })
    .finally(() => (loading.value = false));
}
</script>
