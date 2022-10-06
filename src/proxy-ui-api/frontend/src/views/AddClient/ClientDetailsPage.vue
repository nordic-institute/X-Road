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
  <div class="step-content-wrapper">
    <div class="wizard-info-block">
      <div>
        {{ $t('wizard.clientInfo1') }}
        <br />
        <br />
        {{ $t('wizard.clientInfo2') }}
      </div>
      <div class="action-block">
        <xrd-button
          outlined
          data-test="select-client-button"
          @click="showSelectClient = true"
          >{{ $t('wizard.selectClient') }}</xrd-button
        >
      </div>
    </div>

    <ValidationObserver ref="form2" v-slot="{ invalid }">
      <div class="wizard-step-form-content">
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberName')"
            :help-text="$t('wizard.client.memberNameTooltip')"
          />
          <div data-test="selected-member-name">{{ selectedMemberName }}</div>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberClass')"
            :help-text="$t('wizard.client.memberClassTooltip')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="addClient.memberClass"
            rules="required"
          >
            <v-select
              v-model="memberClass"
              :items="memberClassesCurrentInstance"
              :error-messages="errors"
              class="wizard-form-input"
              data-test="member-class-input"
              :placeholder="$t('wizard.selectMemberClass')"
              outlined
            ></v-select>
          </ValidationProvider>
        </div>
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberCode')"
            :help-text="$t('wizard.client.memberCodeTooltip')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="addClient.memberCode"
            rules="required|xrdIdentifier"
          >
            <v-text-field
              v-model="memberCode"
              class="wizard-form-input"
              type="text"
              :error-messages="errors"
              autofocus
              :placeholder="$t('wizard.memberCode')"
              outlined
              data-test="member-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.subsystemCode')"
            :help-text="$t('wizard.client.subsystemCodeTooltip')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="addClient.subsystemCode"
            rules="required|xrdIdentifier"
          >
            <v-text-field
              v-model="subsystemCode"
              class="wizard-form-input"
              type="text"
              outlined
              :placeholder="$t('wizard.subsystemCode')"
              :error-messages="errors"
              data-test="subsystem-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
        <div v-if="duplicateClient" class="wizard-duplicate-warning">
          {{ $t('wizard.client.clientExists') }}
        </div>
      </div>
      <div class="button-footer">
        <xrd-button outlined data-test="cancel-button" @click="cancel">{{
          $t('action.cancel')
        }}</xrd-button>
        <xrd-button
          :disabled="invalid || duplicateClient"
          data-test="next-button"
          @click="done"
          >{{ $t('action.next') }}</xrd-button
        >
      </div>
    </ValidationObserver>

    <SelectClientDialog
      :title="$t('wizard.addClientTitle')"
      :search-label="$t('wizard.client.searchLabel')"
      :dialog="showSelectClient"
      :selectable-clients="selectableClients"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { debounce, isEmpty, containsClient } from '@/util/helpers';
import { Client } from '@/openapi-types';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { AddMemberWizardModes } from '@/global';
import { validate } from 'vee-validate';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useAddClient } from '@/store/modules/addClient';
import { useNotifications } from '@/store/modules/notifications';
import { useGeneral } from '@/store/modules/general';
import { useUser } from '@/store/modules/user';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  components: {
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  data() {
    return {
      showSelectClient: false,
      checkRunning: false,
      isMemberCodeValid: true,
    };
  },
  computed: {
    ...mapState(useAddClient, [
      'reservedClients',
      'selectableClients',
      'selectedMemberName',
    ]),
    ...mapWritableState(useAddClient, [
      'memberClass',
      'memberCode',
      'subsystemCode',
    ]),
    ...mapState(useGeneral, ['memberClassesCurrentInstance']),
    ...mapState(useUser, ['currentSecurityServer']),

    duplicateClient(): boolean {
      return containsClient(
        this.reservedClients,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );
    },
  },
  watch: {
    async memberCode(val) {
      // Set wizard mode to default (full)
      this.setAddMemberWizardMode(AddMemberWizardModes.FULL);

      // Needs to be done here, because the watcher runs before the setter
      validate(this.memberCode, 'required|xrdIdentifier', {
        // name is not used, but if it's undefined there is a warning in browser console
        name: 'addClient.memberCode',
      }).then((result) => {
        if (result.valid) {
          this.isMemberCodeValid = true;

          if (isEmpty(val) || isEmpty(this.memberClass)) {
            return;
          }
          this.checkClient();
        } else {
          this.isMemberCodeValid = false;
        }
      });
    },
    memberClass(val): void {
      // Set wizard mode to default (full)
      this.setAddMemberWizardMode(AddMemberWizardModes.FULL);
      if (isEmpty(val) || isEmpty(this.memberCode)) {
        return;
      }
      this.checkClient();
    },

    memberClassesCurrentInstance(val): void {
      // Set first member class selected as default when the list is updated
      if (val?.length === 1) {
        this.memberClass = val[0];
      }
    },
  },
  created() {
    that = this;
    this.setAddMemberWizardMode(AddMemberWizardModes.FULL);
    this.fetchSelectableClients(that.currentSecurityServer.instance_id);
    this.fetchMemberClassesForCurrentInstance();
  },
  mounted() {
    this.$refs.memberCodeVP;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useGeneral, ['fetchMemberClassesForCurrentInstance']),
    ...mapActions(useAddClient, [
      'setAddMemberWizardMode',
      'fetchSelectableClients',
      'setSelectedMember',
      'fetchReservedClients',
      'setSelectedMemberName',
      'searchTokens',
    ]),

    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$emit('done');
    },
    saveSelectedClient(selectedMember: Client): void {
      this.setSelectedMember(selectedMember);
      this.fetchReservedClients(selectedMember).catch((error) => {
        this.showError(error);
      });

      this.showSelectClient = false;
    },
    checkClient(): void {
      // don't continue if the identifier is invalid
      if (!this.isMemberCodeValid) {
        return;
      }
      this.checkRunning = true;

      // Find if the selectable clients array has a match
      const tempClient = this.selectableClients.find((client: Client) => {
        return (
          client.member_code === this.memberCode &&
          client.member_class === this.memberClass
        );
      });

      // Fill the name "field" if it's available or set it undefined
      this.setSelectedMemberName(tempClient?.member_name);

      // Pass the arguments so that we use the validated information instead of the state at that time
      this.checkClientDebounce(this.memberClass, this.memberCode);
    },
    checkClientDebounce: debounce((memberClass: string, memberCode: string) => {
      // Debounce is used to reduce unnecessary api calls
      // Search tokens for suitable CSR:s and certificates
      that
        .searchTokens({
          instanceId: that.currentSecurityServer.instance_id,
          memberClass: memberClass,
          memberCode: memberCode,
        })
        .then(
          () => {
            that.checkRunning = false;
          },
          (error: Error) => {
            that.showError(error);
            that.checkRunning = true;
          },
        );
    }, 600),
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/wizards';
</style>
