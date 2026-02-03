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
      <p class="mb-6 body-regular">{{ $t('initialConfigurationV2.autoInit.info') }}</p>

      <div v-for="sub in subSteps" :key="sub.step" class="sub-step mb-4 d-flex align-center">
        <v-icon v-if="sub.state === 'completed'" color="success" class="mr-3">mdi-check-circle</v-icon>
        <v-progress-circular v-else-if="sub.state === 'in-progress'" indeterminate size="20" width="2" class="mr-3" />
        <v-icon v-else-if="sub.state === 'failed'" color="error" class="mr-3">mdi-alert-circle</v-icon>
        <v-icon v-else color="grey" class="mr-3">mdi-circle-outline</v-icon>

        <span class="body-regular">{{ $t(sub.label) }}</span>
      </div>

      <v-alert v-if="errorMessage" type="error" variant="outlined" class="mt-4" closable @click:close="errorMessage = ''">
        {{ errorMessage }}
      </v-alert>
    </div>

    <template #footer>
      <v-spacer />
      <XrdBtn v-if="hasFailed" data-test="retry-button" text="action.retry" variant="flat" @click="execute" />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions } from 'pinia';

import { useNotifications, XrdWizardStep, XrdBtn } from '@niis/shared-ui';
import { useInitializationV2 } from '@/store/modules/initializationV2';

type SubStepState = 'pending' | 'in-progress' | 'completed' | 'failed';

interface SubStep {
  step: string;
  label: string;
  state: SubStepState;
}

export default defineComponent({
  components: { XrdWizardStep, XrdBtn },
  emits: ['done'],
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      subSteps: [
        { step: 'GPG_KEY', label: 'initialConfigurationV2.autoInit.gpgKey', state: 'pending' },
        { step: 'MLOG_ENCRYPTION', label: 'initialConfigurationV2.autoInit.messageLog', state: 'pending' },
      ] as SubStep[],
      errorMessage: '',
      hasFailed: false,
    };
  },
  mounted() {
    this.execute();
  },
  methods: {
    ...mapActions(useInitializationV2, ['initGpgKey', 'initMessageLogEncryption']),

    async execute(): Promise<void> {
      this.hasFailed = false;
      this.errorMessage = '';

      // Reset any failed steps to pending
      for (const sub of this.subSteps) {
        if (sub.state === 'failed') {
          sub.state = 'pending';
        }
      }

      // GPG Key
      const gpgStep = this.subSteps.find((s) => s.step === 'GPG_KEY')!;
      if (gpgStep.state !== 'completed') {
        gpgStep.state = 'in-progress';
        try {
          await this.initGpgKey();
          gpgStep.state = 'completed';
        } catch (error: any) {
          gpgStep.state = 'failed';
          this.hasFailed = true;
          this.errorMessage =
            error?.response?.data?.error?.description || error?.response?.data?.message || this.$t('initialConfigurationV2.autoInit.gpgKeyError');
          this.addError(error);
          return;
        }
      }

      // Message Log Encryption
      const mlogStep = this.subSteps.find((s) => s.step === 'MLOG_ENCRYPTION')!;
      if (mlogStep.state !== 'completed') {
        mlogStep.state = 'in-progress';
        try {
          await this.initMessageLogEncryption();
          mlogStep.state = 'completed';
        } catch (error: any) {
          mlogStep.state = 'failed';
          this.hasFailed = true;
          this.errorMessage =
            error?.response?.data?.error?.description ||
            error?.response?.data?.message ||
            this.$t('initialConfigurationV2.autoInit.messageLogError');
          this.addError(error);
          return;
        }
      }

      this.$emit('done');
    },
  },
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
