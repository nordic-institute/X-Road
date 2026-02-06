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
  <XrdWizardStep sub-title="initialConfiguration.pin.info1">
    <v-alert
      data-test="alert-token-policy-enabled"
      class="mt-6 mb-6 body-regular"
      variant="outlined"
      border="start"
      density="compact"
      type="info"
    >
      <p class="font-weight-bold">{{ $t('token.tokenPinPolicyHeader') }}</p>
      <p>{{ $t('token.tokenPinPolicy') }}</p>
    </v-alert>

    <XrdFormBlock>
      <XrdFormBlockRow>
        <v-text-field
          v-model="pinMdl"
          v-bind="pinRef"
          data-test="pin-input"
          class="xrd"
          autofocus
          type="password"
          :label="$t('initialConfiguration.pin.pin')"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow>
        <v-text-field
          v-model="confirmPinMdl"
          v-bind="confirmPinRef"
          class="xrd"
          :label="$t('initialConfiguration.pin.confirmPin')"
          type="password"
          data-test="confirm-pin-input"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>

    <template #footer>
      <v-spacer />

      <XrdBtn
        variant="outlined"
        data-test="previous-button"
        class="previous-button mr-4"
        text="action.previous"
        @click="emit('previous')"
      />
      <XrdBtn data-test="soft-token-save-button" text="action.continue" :disabled="!meta.valid" :loading="busy" @click="submit" />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useForm } from 'vee-validate';

import { useNotifications, XrdWizardStep, XrdBtn, XrdFormBlock, XrdFormBlockRow, veeDefaultFieldConfig } from '@niis/shared-ui';
import { useInitializationV2 } from '@/store/modules/initializationV2';

const emit = defineEmits<{
  done: [];
  previous: [];
}>();

const { addError } = useNotifications();
const { initSoftToken } = useInitializationV2();

const { meta, values, defineField } = useForm({
  validationSchema: {
    pin: 'required',
    confirmPin: 'required|confirmed:@pin',
  },
});
const componentConfig = veeDefaultFieldConfig();
const [pinMdl, pinRef] = defineField('pin', componentConfig);
const [confirmPinMdl, confirmPinRef] = defineField('confirmPin', componentConfig);

const busy = ref(false);

async function submit(): Promise<void> {
  busy.value = true;
  try {
    await initSoftToken(values.pin!);
    emit('done');
  } catch (error) {
    addError(error);
  } finally {
    busy.value = false;
  }
}
</script>

<style lang="scss" scoped></style>
