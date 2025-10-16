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
    :width="840"
    title="services.addRest"
    submittable
    :loading="saving"
    :disable-save="!meta.valid"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-radio-group
            v-bind="serviceTypeRef"
            name="serviceType"
            class="xrd"
            inline
            :label="$t('services.serviceType')"
          >
            <v-radio
              data-test="rest-radio-button"
              class="xrd"
              value="REST"
              :label="$t('services.restApiBasePath')"
            />
            <v-radio
              data-test="openapi3-radio-button"
              class="xrd ml-6"
              value="OPENAPI3"
              :label="$t('services.OpenApi3Description')"
            />
          </v-radio-group>
        </XrdFormBlockRow>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-bind="serviceUrlRef"
            data-test="service-url-text-field"
            class="xrd"
            autofocus
            :label="$t('services.url')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-bind="serviceCodeRef"
            data-test="service-code-text-field"
            class="xrd"
            :label="$t('services.serviceCode')"
            :maxlength="255"
          ></v-text-field>
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
  <ServiceWarningDialog
    v-if="warningDialog"
    :warnings="warningInfo"
    :loading="saving"
    @cancel="warningDialog = false"
    @accept="saveWithWarning"
  />
</template>

<script lang="ts" setup>
import { PublicPathState, useForm } from 'vee-validate';
import { XrdFormBlock, XrdFormBlockRow, useNotifications, DialogSaveHandler } from '@niis/shared-ui';
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
const { saveRest } = useServiceDescriptions();

const warningDialog = ref(false);
const saving = ref(false);
const warningInfo = ref<CodeWithDetails[]>([]);
const dialogHandler = ref<DialogSaveHandler | undefined>(undefined);

const { meta, resetForm, values, defineComponentBinds } = useForm({
  validationSchema: {
    serviceType: 'required',
    serviceUrl: 'required|max:255|restUrl',
    serviceCode: 'required|max:255|xrdIdentifier',
  },
});
const componentConfig = (state: PublicPathState) => ({
  props: {
    'error-messages': state.errors,
  },
});
const serviceTypeRef = defineComponentBinds('serviceType', componentConfig);
const serviceUrlRef = defineComponentBinds('serviceUrl', componentConfig);
const serviceCodeRef = defineComponentBinds('serviceCode', componentConfig);

function cancel(): void {
  warningDialog.value = false;
  emit('cancel');
  clear();
}

function save(handler: DialogSaveHandler): void {
  dialogHandler.value = handler;
  saving.value = true;
  warningDialog.value = false;
  saveRest(
    props.clientId,
    values.serviceUrl,
    values.serviceCode,
    values.serviceType,
  )
    .then(() =>
      addSuccessMessage(
        values.serviceType === 'OPENAPI3'
          ? 'services.openApi3Added'
          : 'services.restAdded',
      ),
    )
    .then(() => emit('save'))
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
  saving.value = true;
  warningDialog.value = false;
  saveRest(
    props.clientId,
    values.serviceUrl,
    values.serviceCode,
    values.serviceType,
    true,
  )
    .then(() =>
      addSuccessMessage(
        values.serviceType === 'OPENAPI3'
          ? 'services.openApi3Added'
          : 'services.restAdded',
      ),
    )
    .then(() => emit('save'))
    .catch((error) => dialogHandler.value?.addError(error))
    .finally(() => (saving.value = false));
}

function clear(): void {
  requestAnimationFrame(() => {
    resetForm();
  });
}
</script>

<style lang="scss" scoped></style>
