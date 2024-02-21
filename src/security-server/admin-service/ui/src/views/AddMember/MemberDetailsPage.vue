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
  <div>
    <div class="wizard-info-block">
      <div>
        {{ $t('wizard.member.info1') }}
        <br />
        <br />
        {{ $t('wizard.member.info2') }}
      </div>
      <div class="action-block">
        <xrd-button
          outlined
          data-test="select-client-button"
          @click="showSelectClient = true"
          >{{ $t('wizard.member.select') }}
        </xrd-button>
      </div>
    </div>

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
        <v-select
          v-bind="memberClassRef"
          :items="memberClassItems"
          class="wizard-form-input"
          data-test="member-class-input"
          :placeholder="$t('wizard.selectMemberClass')"
          variant="outlined"
        ></v-select>
      </div>
      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('wizard.memberCode')"
          :help-text="$t('wizard.client.memberCodeTooltip')"
        />

        <v-text-field
          v-bind="memberCodeRef"
          class="wizard-form-input"
          type="text"
          :placeholder="$t('wizard.memberCode')"
          autofocus
          variant="outlined"
          data-test="member-code-input"
        ></v-text-field>
      </div>

      <div v-if="duplicateOwnerClient" class="wizard-duplicate-warning">
        {{ $t('wizard.member.memberExists') }}
      </div>
    </div>
    <div class="button-footer">
      <div class="button-group">
        <xrd-button outlined data-test="cancel-button" @click="cancel"
          >{{ $t('action.cancel') }}
        </xrd-button>
      </div>
      <xrd-button
        :disabled="!meta.valid || duplicateOwnerClient || checkRunning"
        data-test="next-button"
        @click="done"
        >{{ $t('action.next') }}
      </xrd-button>
    </div>

    <SelectClientDialog
      :title="$t('wizard.addMemberTitle')"
      :search-label="$t('wizard.member.searchLabel')"
      :dialog="showSelectClient"
      :selectable-clients="selectableMembersWithoutOwner"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { Client } from '@/openapi-types';
import { debounce, isEmpty } from '@/util/helpers';
import { AddMemberWizardModes } from '@/global';
import { PublicPathState, useForm } from 'vee-validate';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useAddClient } from '@/store/modules/addClient';
import { useGeneral } from '@/store/modules/general';
import { useUser } from '@/store/modules/user';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    SelectClientDialog,
  },
  emits: ['cancel', 'done'],
  setup() {
    const { meta, values, validateField, setFieldValue, defineComponentBinds } =
      useForm({
        validationSchema: {
          'addClient.memberClass': 'required',
          'addClient.memberCode': 'required|max:255|xrdIdentifier',
        },
        initialValues: {
          addClient: {
            memberClass: '',
            memberCode: '',
          },
        },
      });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const memberClassRef = defineComponentBinds(
      'addClient.memberClass',
      componentConfig,
    );
    const memberCodeRef = defineComponentBinds(
      'addClient.memberCode',
      componentConfig,
    );
    return {
      meta,
      values,
      validateField,
      setFieldValue,
      memberClassRef,
      memberCodeRef,
    };
  },
  data() {
    return {
      showSelectClient: false as boolean,
      checkRunning: false as boolean,
      isMemberCodeValid: true,
    };
  },
  computed: {
    ...mapState(useAddClient, [
      'reservedMember',
      'selectedMemberName',
      'selectableMembers',
    ]),
    ...mapState(useGeneral, ['memberClassesCurrentInstance']),
    ...mapState(useUser, ['currentSecurityServer']),
    ...mapWritableState(useAddClient, ['memberClass', 'memberCode']),

    memberClassItems() {
      return this.memberClassesCurrentInstance.map((memberClass) => ({
        title: memberClass,
        value: memberClass,
      }));
    },

    selectableMembersWithoutOwner(): Client[] {
      return this.selectableMembers.filter(
        (client: Client) =>
          !(
            client.member_class === this.reservedMember?.memberClass &&
            client.member_code === this.reservedMember.memberCode
          ),
      );
    },

    duplicateOwnerClient(): boolean {
      return (
        !!this.values.addClient.memberClass &&
        !!this.values.addClient.memberCode &&
        this.reservedMember?.memberClass.toLowerCase() ===
          this.values.addClient.memberClass.toLowerCase() &&
        this.reservedMember?.memberCode.toLowerCase() ===
          this.values.addClient.memberCode.toLowerCase()
      );
    },
  },

  watch: {
    async 'values.addClient.memberCode'(newValue) {
      // Set wizard mode to default (full)
      this.setAddMemberWizardMode(AddMemberWizardModes.FULL);

      // Needs to be done here, because the watcher runs before the setter
      this.validateField('addClient.memberCode').then(({ valid }) => {
        if (valid) {
          this.isMemberCodeValid = true;
          if (isEmpty(newValue) || isEmpty(this.values.addClient.memberClass)) {
            return;
          }
          this.checkClient();
        } else {
          this.isMemberCodeValid = false;
        }
      });
    },
    'values.addClient.memberClass'(newValue): void {
      // Set wizard mode to default (full)
      this.setAddMemberWizardMode(AddMemberWizardModes.FULL);
      if (isEmpty(newValue) || isEmpty(this.values.addClient.memberCode)) {
        return;
      }
      this.checkClient();
    },

    memberClassesCurrentInstance(val): void {
      // Set first member class selected as default when the list is updated
      if (val?.length === 1) {
        this.setFieldValue('addClient.memberClass', val[0]);
      }
    },
  },

  created() {
    that = this;
    this.setAddMemberWizardMode(AddMemberWizardModes.FULL);
    this.fetchSelectableMembers(that.currentSecurityServer.instance_id);
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),

    ...mapActions(useAddClient, [
      'setSelectedMember',
      'fetchSelectableMembers',
      'setAddMemberWizardMode',
      'updateAddMemberWizardModeIfNeeded',
      'setSelectedMemberName',
    ]),

    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.memberClass = this.values.addClient.memberClass;
      this.memberCode = this.values.addClient.memberCode;
      this.$emit('done');
    },
    saveSelectedClient(selectedMember: Client): void {
      this.setSelectedMember(selectedMember);
      this.setFieldValue('addClient.memberClass', selectedMember.member_class);
      this.setFieldValue('addClient.memberCode', selectedMember.member_code);
      this.showSelectClient = false;
    },
    checkClient(): void {
      // check if the identifier is valid
      if (!this.isMemberCodeValid) {
        return;
      }
      this.checkRunning = true;

      // Find if the selectable clients array has a match
      const tempClient = this.selectableMembersWithoutOwner.find(
        (client: Client) =>
          client.member_code === this.values.addClient.memberCode &&
          client.member_class === this.values.addClient.memberClass,
      );

      // Fill the name "field" if it's available or set it undefined
      this.setSelectedMemberName(tempClient?.member_name);

      // Pass the arguments so that we use the validated information instead of the state at that time
      this.checkClientDebounce(
        this.values.addClient.memberClass,
        this.values.addClient.memberCode,
      );
    },
    checkClientDebounce: debounce((memberClass: string, memberCode: string) => {
      // Debounce is used to reduce unnecessary api calls
      // Search tokens for suitable CSR:s and certificates
      that
        .updateAddMemberWizardModeIfNeeded({
          instanceId: that.reservedMember.instanceId,
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
@import '../../assets/wizards';
</style>
