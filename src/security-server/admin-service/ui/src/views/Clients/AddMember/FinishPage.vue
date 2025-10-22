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
  <XrdWizardStep data-test="finish-content">
    <div class="wizard-step-form-content px-12 pt-10">
      <div class="body-regular font-weight-regular">
        <p class="mb-6">{{ $t('wizard.finish.infoLine1') }}</p>
        <p class="mb-6">{{ $t('wizard.finish.infoLine2') }}</p>
        <p>{{ $t('wizard.finish.todo1') }}</p>
        <p>{{ $t('wizard.finish.todo2') }}</p>
        <p>{{ $t('wizard.finish.todo3') }}</p>
        <p class="mt-10">{{ $t('wizard.finish.note') }}</p>
      </div>

      <div v-if="showRegisterOption" class="mt-6">
        <v-checkbox
          v-model="registerChecked"
          data-test="register-member-checkbox"
          class="xrd"
          density="compact"
          hide-details
          :label="$t('wizard.member.register')"
        />
      </div>
    </div>

    <!-- Accept warnings -->
    <WarningDialog
      v-if="warningDialog"
      :warnings="warningInfo"
      localization-parent="wizard.warning"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
    <template #footer>
      <XrdBtn
        data-test="cancel-button"
        variant="outlined"
        text="action.cancel"
        :disabled="disableCancel"
        @click="cancel"
      />
      <v-spacer />
      <XrdBtn
        data-test="previous-button"
        class="mr-4"
        variant="outlined"
        text="action.previous"
        :disabled="disableCancel"
        @click="previous"
      />

      <XrdBtn
        data-test="submit-button"
        text="action.submit"
        :loading="submitLoading"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import WarningDialog from '@/components/ui/WarningDialog.vue';
import { AddMemberWizardModes } from '@/global';
import { createClientId } from '@/util/helpers';
import { mapActions, mapState } from 'pinia';
import { useAddClient } from '@/store/modules/addClient';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { CodeWithDetails } from '@/openapi-types';
import { XrdWizardStep, XrdBtn, useNotifications } from '@niis/shared-ui';
import { useClient } from '@/store/modules/client';

export default defineComponent({
  components: {
    WarningDialog,
    XrdWizardStep,
    XrdBtn,
  },
  emits: ['cancel', 'previous', 'done'],
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      disableCancel: false as boolean,
      registerChecked: true as boolean,
      submitLoading: false as boolean,
      warningInfo: [] as CodeWithDetails[],
      warningDialog: false as boolean,
    };
  },
  computed: {
    ...mapState(useAddClient, [
      'addMemberWizardMode',
      'memberClass',
      'memberCode',
      'reservedMember',
    ]),

    ...mapState(useCsr, ['csrTokenId']),

    showRegisterOption() {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      ) {
        return true;
      }
      return false;
    },
  },

  methods: {
    ...mapActions(useClient, ['registerClient']),
    ...mapActions(useAddClient, ['createMember']),
    ...mapActions(useCsr, ['requestGenerateCsr', 'generateKeyAndCsr']),
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.cmpCreateMember(false);
    },
    cmpCreateMember(ignoreWarnings: boolean): void {
      this.disableCancel = true;
      this.submitLoading = true;

      this.createMember(ignoreWarnings).then(
        () => {
          if (
            this.addMemberWizardMode ===
              AddMemberWizardModes.CERTIFICATE_EXISTS &&
            this.registerChecked
          ) {
            this.doRegisterClient();
          } else if (
            this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
          ) {
            this.disableCancel = false;
            this.submitLoading = false;
            this.$emit('done');
          } else if (
            this.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS
          ) {
            this.generateCsr();
          } else {
            this.cmpGenerateKeyAndCsr();
          }
        },
        (error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.addError(error);
            this.disableCancel = false;
            this.submitLoading = false;
          }
        },
      );
    },

    cancelSubmit(): void {
      this.disableCancel = false;
      this.submitLoading = false;
      this.warningDialog = false;
    },
    acceptWarnings(): void {
      this.cmpCreateMember(true);
    },

    cmpGenerateKeyAndCsr(): void {
      if (!this.csrTokenId) {
        // Should not happen
        throw new Error('Token id does not exist');
      }

      this.generateKeyAndCsr(this.csrTokenId)
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.addError(error);
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    generateCsr(): void {
      this.requestGenerateCsr()
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.addError(error);
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    doRegisterClient(): void {
      if (!this.reservedMember) {
        // Should not happen
        throw new Error('Reserved member does not exist');
      }

      const clientId = createClientId(
        this.reservedMember.instanceId,
        this.memberClass,
        this.memberCode,
      );

      this.registerClient(clientId)
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.addError(error);
            this.$emit('done');
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
