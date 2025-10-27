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
    title="services.addWsdl"
    submittable
    :disable-save="!meta.valid"
    :width="840"
    :loading="saving"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="value"
            data-test="service-url-text-field"
            class="xrd"
            autofocus
            :label="$t('services.url')"
            :error-messages="errors"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
  <!-- Accept "save WSDL" warnings -->
  <ServiceWarningDialog
    v-if="warningDialog"
    :warnings="warningInfo"
    @cancel="warningDialog = false"
    @accept="saveWithWarning"
  />
</template>

<script lang="ts" setup>
import { useField } from 'vee-validate';
import {
  useNotifications,
  DialogSaveHandler,
  XrdFormBlock,
  XrdFormBlockRow,
} from '@niis/shared-ui';
import { ref } from 'vue';
import { CodeWithDetails } from '@/openapi-types';
import ServiceWarningDialog from '@/components/service/ServiceWarningDialog.vue';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

const props = defineProps({
  clientId: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'save']);

const { addSuccessMessage } = useNotifications();
const { saveWsdl } = useServiceDescriptions();

const { value, meta, errors, resetField } = useField(
  'serviceUrl',
  {
    required: true,
    wsdlUrl: true,
    max: 255,
  },
  { initialValue: '' },
);

const warningDialog = ref(false);
const saving = ref(false);
const warningInfo = ref<CodeWithDetails[]>([]);
const dialogHandler = ref<DialogSaveHandler | undefined>(undefined);

function cancel(): void {
  emit('cancel');
  clear();
}

function save(handler: DialogSaveHandler): void {
  warningDialog.value = false;
  saving.value = true;
  dialogHandler.value = handler;
  saveWsdl(props.clientId, value.value)
    .then(() => addSuccessMessage('services.wsdlAdded'))
    .then(() => emit('save'))
    .then(() => clear())
    .catch((error) => {
      if (error?.response?.data?.warnings) {
        warningInfo.value = error.response.data.warnings;
        warningDialog.value = true;
      } else {
        handler.addError(error);
      }
    })
    .finally(() => (saving.value = false));
}

function saveWithWarning(): void {
  warningDialog.value = false;
  saving.value = true;
  saveWsdl(props.clientId, value.value, true)
    .then(() => addSuccessMessage('services.wsdlAdded'))
    .then(() => emit('save'))
    .then(() => clear())
    .catch((error) => dialogHandler.value?.addError(error))
    .finally(() => (saving.value = false));
}

function clear(): void {
  resetField();
}
</script>

<style lang="scss" scoped></style>
