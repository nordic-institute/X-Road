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
    title="issuerService.editHostTitle"
    data-test="issuer-service-host-edit-dialog"
    save-button-text="action.save"
    submittable
    :scrollable="false"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="onSave"
    @cancel="onCancel"
  >
    <template #content>
      <v-alert data-test="issuer-service-host-help" type="info" variant="tonal" class="mb-4">
        <p>{{ $t('issuerService.hostHelpCluster') }}</p>
        <p>{{ $t('issuerService.hostHelpSingleServer') }}</p>
      </v-alert>
      <v-text-field
        v-model="hostInput"
        v-bind="hostInputAttrs"
        data-test="issuer-service-host-edit-field"
        variant="outlined"
        name="host"
        autofocus
        :label="$t('issuerService.host')"
      />
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { useForm } from 'vee-validate';
import { XrdSimpleDialog } from '@niis/shared-ui';
import type { DialogSaveHandler } from '@niis/shared-ui';
import { useNotifications } from '@niis/shared-ui';
import { ref } from 'vue';

import { useIssuerService } from '@/store/modules/issuer-service';

const props = defineProps({
  host: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['save', 'cancel']);

const { setHost } = useIssuerService();
const { addSuccessMessage } = useNotifications();

const { meta, defineField, handleSubmit } = useForm({
  validationSchema: { host: 'required' },
  initialValues: { host: props.host },
});
const [hostInput, hostInputAttrs] = defineField('host', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const loading = ref(false);

function onSave(evt: Event, handler: DialogSaveHandler) {
  return handleSubmit((values) => {
    loading.value = true;
    return setHost(values.host)
      .then(() => {
        addSuccessMessage('issuerService.hostSaveSuccess');
        emit('save');
      })
      .catch((error) => handler.addError(error))
      .finally(() => (loading.value = false));
  })(evt);
}

function onCancel() {
  emit('cancel');
}
</script>
