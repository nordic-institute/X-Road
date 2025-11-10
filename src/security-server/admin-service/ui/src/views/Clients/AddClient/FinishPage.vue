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
        <template v-if="acmeOrder">
          <p class="mb-6">{{ $t('wizard.finish.acme.infoLine') }}</p>
        </template>
        <template v-else>
          <p>{{ $t('wizard.finish.infoLine1') }}</p>
          <p class="mb-6">{{ $t('wizard.finish.infoLine2') }}</p>
          <p>{{ $t('wizard.finish.todo1') }}</p>
          <p>{{ $t('wizard.finish.todo2') }}</p>
          <p class="mb-6">{{ $t('wizard.finish.todo3') }}</p>
        </template>
        <p class="mt-10">{{ $t('wizard.finish.note') }}</p>

        <div v-if="showRegisterOption && canRegisterClient">
          <v-checkbox
            v-model="registerChecked"
            data-test="register-member-checkbox"
            class="xrd"
            hide-details
            :label="$t('wizard.client.register')"
          />
        </div>
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
        :disabled="disableCancel"
        variant="outlined"
        data-test="cancel-button"
        text="action.cancel"
        @click="cancel"
      />
      <v-spacer />

      <XrdBtn
        data-test="previous-button"
        class="mr-2"
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
import { memberHasValidSignCert } from '@/util/ClientUtil';
import { mapActions, mapState } from 'pinia';

import { useAddClient } from '@/store/modules/addClient';
import { useUser } from '@/store/modules/user';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { CodeWithDetails } from '@/openapi-types';
import { XrdWizardStep, XrdBtn, useNotifications } from '@niis/shared-ui';
import { useClient } from '@/store/modules/client';

export default defineComponent({
  components: {
    XrdWizardStep,
    WarningDialog,
    XrdBtn,
  },
  emits: ['cancel', 'previous', 'done'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      disableCancel: false,
      registerChecked: true,
      submitLoading: false,
      warningInfo: [] as CodeWithDetails[],
      warningDialog: false,
    };
  },
  computed: {
    ...mapState(useAddClient, [
      'addMemberWizardMode',
      'memberClass',
      'memberCode',
      'subsystemCode',
      'subsystemName',
      'tokens',
    ]),
    ...mapState(useUser, ['currentSecurityServer']),
    ...mapState(useCsr, ['csrTokenId', 'acmeOrder']),

    showRegisterOption(): boolean {
      return (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      );
    },
    canRegisterClient(): boolean {
      const memberName = `${this.currentSecurityServer?.instance_id}:${this.memberClass}:${this.memberCode}`;
      return memberHasValidSignCert(memberName, this.tokens);
    },
  },

  methods: {
    ...mapActions(useClient, ['registerClient']),
    ...mapActions(useAddClient, ['createClient']),
    ...mapActions(useCsr, ['generateKeyAndCsr', 'requestGenerateCsr']),
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.requestCreateClient(false);
    },
    requestCreateClient(ignoreWarnings: boolean): void {
      this.disableCancel = true;
      this.submitLoading = true;

      this.createClient(ignoreWarnings).then(
        () => {
          if (
            this.addMemberWizardMode ===
              AddMemberWizardModes.CERTIFICATE_EXISTS &&
            this.registerChecked &&
            this.canRegisterClient
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
            this.requestGenerateKeyAndCsr();
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
      this.requestCreateClient(true);
    },
    requestGenerateKeyAndCsr(): void {
      if (!this.csrTokenId) {
        // Should not happen
        throw new Error('Token id is missing');
      }

      const tokenId = this.csrTokenId;

      this.generateKeyAndCsr(tokenId)
        .then(
          () => {
            this.$emit('done');
          },
          (error) => this.addError(error),
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
          (error) => this.addError(error),
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    doRegisterClient(): void {
      if (!this.currentSecurityServer?.instance_id) {
        // Should not happen
        throw new Error('Current security server is missing instance id');
      }

      const clientId = createClientId(
        this.currentSecurityServer.instance_id,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
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
