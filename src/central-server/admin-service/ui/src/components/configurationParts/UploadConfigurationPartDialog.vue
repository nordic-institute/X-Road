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
    :dialog="true"
    title="globalConf.cfgParts.dialog.upload.title"
    save-button-text="action.upload"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="!partFile"
    @save="save"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <div class="dlg-input-width">
        <XrdFileUploadField
          v-model:file="partFile"
          accept=".xml"
          label-key="globalConf.cfgParts.dialog.upload.uploadConfigurationPart"
          data-test="timestamping-service-file-input"
          autofocus
        />
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { PropType } from 'vue';
import { useConfigurationSource } from '@/store/modules/configuration-sources';
import {
  ConfigurationPartContentIdentifier,
  ConfigurationType,
} from '@/openapi-types';
import { XrdFileUploadField } from '@niis/shared-ui';
import { useBasicForm, useFileRef } from '@/util/composables';

const props = defineProps({
  configurationType: {
    type: String as PropType<ConfigurationType>,
    required: true,
  },
  contentIdentifier: {
    type: String as PropType<ConfigurationPartContentIdentifier>,
    required: true,
  },
});

const { uploadConfigurationFile } = useConfigurationSource();
const { loading, showSuccess, t, showError } = useBasicForm();

const emit = defineEmits(['save', 'cancel']);

const partFile = useFileRef();

function save() {
  if (!partFile.value) {
    return;
  }

  loading.value = true;
  uploadConfigurationFile(
    props.configurationType,
    props.contentIdentifier,
    partFile.value,
  )
    .then(() => {
      showSuccess(t('globalConf.cfgParts.dialog.upload.success'));
      emit('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
}
</script>
