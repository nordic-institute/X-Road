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
  <XrdWizardStep>
    <div class="auto-init-container">
      <p class="mb-6 body-regular">{{ $t('initialConfiguration.autoInit.info') }}</p>

      <div v-for="sub in subSteps" :key="sub.step" class="sub-step mb-4 d-flex align-center">
        <v-icon v-if="sub.state === 'completed'" icon="check_circle" color="success" filled class="mr-3" />
        <v-progress-circular v-else-if="sub.state === 'in-progress'" indeterminate size="20" width="2" class="mr-3" />
        <v-icon v-else-if="sub.state === 'failed'" icon="error" color="error" class="mr-3" />
        <v-icon v-else icon="circle" color="grey" class="mr-3" />

        <span class="body-regular">{{ $t(sub.label) }}</span>
      </div>
    </div>

    <template #footer>
      <v-spacer />
      <XrdBtn v-if="hasFailed" data-test="retry-button" text="action.retry" variant="flat" @click="execute" />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import { useNotifications, XrdWizardStep, XrdBtn } from '@niis/shared-ui';
import { useInitializationV2 } from '@/store/modules/initializationV2';

type SubStepState = 'pending' | 'in-progress' | 'completed' | 'failed';

interface SubStep {
  step: string;
  label: string;
  state: SubStepState;
}

const emit = defineEmits<{ done: [] }>();

const { addError } = useNotifications();
const { initGpgKey, initMessageLogEncryption } = useInitializationV2();

const subSteps = reactive<SubStep[]>([
  { step: 'GPG_KEY', label: 'initialConfiguration.autoInit.gpgKey', state: 'pending' },
  { step: 'MLOG_ENCRYPTION', label: 'initialConfiguration.autoInit.messageLog', state: 'pending' },
]);
const hasFailed = ref(false);

async function execute(): Promise<void> {
  hasFailed.value = false;

  // Reset any failed steps to pending
  for (const sub of subSteps) {
    if (sub.state === 'failed') {
      sub.state = 'pending';
    }
  }

  // GPG Key
  const gpgStep = subSteps.find((s) => s.step === 'GPG_KEY')!;
  if (gpgStep.state !== 'completed') {
    gpgStep.state = 'in-progress';
    try {
      await initGpgKey();
      gpgStep.state = 'completed';
    } catch (error) {
      gpgStep.state = 'failed';
      hasFailed.value = true;
      addError(error);
      return;
    }
  }

  // Message Log Encryption
  const mlogStep = subSteps.find((s) => s.step === 'MLOG_ENCRYPTION')!;
  if (mlogStep.state !== 'completed') {
    mlogStep.state = 'in-progress';
    try {
      await initMessageLogEncryption();
      mlogStep.state = 'completed';
    } catch (error) {
      mlogStep.state = 'failed';
      hasFailed.value = true;
      addError(error);
      return;
    }
  }

  emit('done');
}

onMounted(() => {
  execute();
});
</script>

<style lang="scss" scoped>
.auto-init-container {
  min-height: 120px;
}

.sub-step {
  min-height: 36px;
}
</style>
