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
    <div class="wizard-step-form-content">
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

      <div v-if="showRegisterOption">
        <FormLabel :labelText="$t('wizard.client.register')" />
        <v-checkbox
          v-model="registerChecked"
          color="primary"
          class="register-checkbox"
          data-test="register-member-checkbox"
        ></v-checkbox>
      </div>
    </div>

    <div class="button-footer">
      <div class="button-group">
        <large-button
          outlined
          @click="cancel"
          :disabled="disableCancel"
          data-test="cancel-button"
          >{{ $t('action.cancel') }}</large-button
        >
      </div>

      <div>
        <large-button
          @click="previous"
          outlined
          :disabled="disableCancel"
          class="previous-button"
          data-test="previous-button"
          >{{ $t('action.previous') }}</large-button
        >

        <large-button
          @click="done"
          :loading="submitLoading"
          data-test="submit-button"
          >{{ $t('action.submit') }}</large-button
        >
      </div>
    </div>
    <!-- Accept warnings -->
    <warningDialog
      :dialog="warningDialog"
      :warnings="warningInfo"
      localizationParent="wizard.warning"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import { AddMemberWizardModes } from '@/global';
import { createClientId } from '@/util/helpers';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    WarningDialog,
  },
  computed: {
    ...mapGetters([
      'addMemberWizardMode',
      'memberClass',
      'memberCode',
      'subsystemCode',
      'currentSecurityServer',
    ]),
    showRegisterOption() {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      ) {
        return true;
      }
      return false;
    },
  },
  data() {
    return {
      disableCancel: false,
      registerChecked: true,
      submitLoading: false,
      warningInfo: [] as string[],
      warningDialog: false,
    };
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.createClient(false);
    },
    createClient(ignoreWarnings: boolean): void {
      this.disableCancel = true;
      this.submitLoading = true;

      this.$store.dispatch('createClient', ignoreWarnings).then(
        () => {
          if (
            this.addMemberWizardMode ===
              AddMemberWizardModes.CERTIFICATE_EXISTS &&
            this.registerChecked
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
            this.generateKeyAndCsr();
          }
        },
        (error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.$store.dispatch('showError', error);
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
      this.createClient(true);
    },

    generateKeyAndCsr(): void {
      const tokenId = this.$store.getters.csrTokenId;

      this.$store
        .dispatch('generateKeyAndCsr', tokenId)
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    generateCsr(): void {
      const tokenId = this.$store.getters.csrTokenId;

      this.$store
        .dispatch('generateCsr', tokenId)
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    registerClient(): void {
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
            this.$store.dispatch('showError', error);
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
@import '../../assets/wizards';
</style>
