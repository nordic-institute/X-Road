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
  <div data-test="finish-content">
    <div class="wizard-step-form-content px-12 pt-10">
      <div class="wizard-finish-info">
        <p>{{ $t('wizard.finish.infoLine1') }}</p>
        <p>{{ $t('wizard.finish.infoLine2') }}</p>
        <br />
        <p>{{ $t('wizard.finish.todo1') }}</p>
        <p>{{ $t('wizard.finish.todo2') }}</p>
        <p>{{ $t('wizard.finish.todo3') }}</p>
        <br />
        <br />
        <p>{{ $t('wizard.finish.note') }}</p>
        <p></p>

        <div v-if="showRegisterOption && canRegisterClient">
          <xrd-form-label :label-text="$t('wizard.client.register')" />
          <v-checkbox
            v-model="registerChecked"
            class="register-checkbox"
            data-test="register-member-checkbox"
          ></v-checkbox>
        </div>
      </div>
    </div>
    <div class="button-footer">
      <xrd-button
        outlined
        :disabled="disableCancel"
        data-test="cancel-button"
        @click="cancel"
        >{{ $t('action.cancel') }}</xrd-button
      >

      <xrd-button
        outlined
        :disabled="disableCancel"
        class="previous-button"
        data-test="previous-button"
        @click="previous"
        >{{ $t('action.previous') }}</xrd-button
      >

      <xrd-button
        :loading="submitLoading"
        data-test="submit-button"
        @click="done"
        >{{ $t('action.submit') }}</xrd-button
      >
    </div>
    <!-- Accept warnings -->
    <WarningDialog
      :dialog="warningDialog"
      :warnings="warningInfo"
      localization-parent="wizard.warning"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import { AddMemberWizardModes } from '@/global';
import { createClientId } from '@/util/helpers';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { memberHasValidSignCert } from '@/util/ClientUtil';
import { mapActions, mapState } from 'pinia';

import { useNotifications } from '@/store/modules/notifications';
import { useAddClient } from '@/store/modules/addClient';
import { useUser } from '@/store/modules/user';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { CodeWithDetails } from '@/openapi-types';

export default defineComponent({
  components: {
    WarningDialog,
  },
  emits: ['cancel', 'previous', 'done'],
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
      'tokens',
    ]),
    ...mapState(useUser, ['currentSecurityServer']),
    ...mapState(useCsr, ['csrTokenId']),

    showRegisterOption(): boolean {
      return (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      );
    },
    canRegisterClient(): boolean {
      const memberName = `${this.currentSecurityServer.instance_id}:${this.memberClass}:${this.memberCode}`;
      return memberHasValidSignCert(memberName, this.tokens);
    },
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
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
            this.registerClient();
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
            this.showError(error);
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
          (error) => {
            this.showError(error);
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
            this.showError(error);
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    registerClient(): void {
      if (!this.currentSecurityServer.instance_id) {
        // Should not happen
        throw new Error('Current security server is missing instance id');
      }

      const clientId = createClientId(
        this.currentSecurityServer.instance_id,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );

      api
        .put(`/clients/${encodePathParameter(clientId)}/register`, {})
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.showError(error);
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

<style lang="scss" scoped>
@import '@/assets/wizards';
</style>
