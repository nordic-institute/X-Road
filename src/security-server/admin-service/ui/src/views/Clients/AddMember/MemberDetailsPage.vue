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
  <XrdWizardStep title="wizard.member.info1" sub-title="wizard.member.info2">
    <template #append-header>
      <XrdBtn data-test="select-client-button" variant="outlined" text="wizard.member.select" @click="showSelectClient = true" />
    </template>
    <XrdFormBlock>
      <v-slide-y-transition>
        <XrdFormBlockRow v-if="selectedMemberName" description="wizard.client.memberNameTooltip" adjust-against-content>
          <v-text-field
            data-test="selected-member-name"
            class="xrd"
            variant="plain"
            :model-value="selectedMemberName"
            :label="$t('wizard.memberName')"
          />
        </XrdFormBlockRow>
      </v-slide-y-transition>
      <XrdFormBlockRow description="wizard.client.memberClassTooltip" adjust-against-content>
        <v-select
          v-model="memberClassMdl"
          v-bind="memberClassAttr"
          class="xrd"
          data-test="member-class-input"
          :items="memberClassItems"
          :label="$t('wizard.memberClass')"
        />
      </XrdFormBlockRow>

      <XrdFormBlockRow description="wizard.client.memberCodeTooltip" adjust-against-content>
        <v-text-field
          v-model="memberCodeMdl"
          v-bind="memberCodeAttr"
          data-test="selected-member-name"
          class="xrd"
          autofocus
          :label="$t('wizard.memberCode')"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>
    <v-slide-y-transition>
      <v-banner
        v-if="duplicateOwnerClient"
        :text="$t('wizard.member.memberExists')"
        class="my-4 border border-s-xl"
        variant="outlined"
        icon="error__filled"
        color="error"
        border="error"
        bg-color="background"
        rounded
      />
    </v-slide-y-transition>
    <SelectClientDialog
      v-if="showSelectClient"
      title="wizard.addMemberTitle"
      :search-label="$t('wizard.member.searchLabel')"
      :selectable-clients="selectableMembersWithoutOwner"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
    <template #footer>
      <XrdBtn data-test="cancel-button" variant="outlined" text="action.cancel" @click="cancel" />
      <v-spacer />
      <XrdBtn
        data-test="next-button"
        text="action.next"
        :loading="checkRunning"
        :disabled="!meta.valid || duplicateOwnerClient || checkRunning"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { Client } from '@/openapi-types';
import { debounce, isEmpty } from '@/util/helpers';
import { AddMemberWizardModes } from '@/global';
import { useForm } from 'vee-validate';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useAddClient } from '@/store/modules/addClient';
import { useGeneral } from '@/store/modules/general';
import { useUser } from '@/store/modules/user';
import { XrdWizardStep, XrdBtn, XrdFormBlock, XrdFormBlockRow, useNotifications, veeDefaultFieldConfig } from '@niis/shared-ui';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    SelectClientDialog,
    XrdWizardStep,
    XrdBtn,
    XrdFormBlock,
    XrdFormBlockRow,
  },

  emits: ['cancel', 'done'],
  setup() {
    const { addError } = useNotifications();
    const { meta, values, validateField, setFieldValue, defineField } = useForm({
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
    const componentConfig = veeDefaultFieldConfig();
    const [memberClassMdl, memberClassAttr] = defineField('addClient.memberClass', componentConfig);
    const [memberCodeMdl, memberCodeAttr] = defineField('addClient.memberCode', componentConfig);
    return {
      addError,
      meta,
      values,
      validateField,
      setFieldValue,
      memberClassMdl,
      memberClassAttr,
      memberCodeMdl,
      memberCodeAttr,
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
    ...mapState(useAddClient, ['reservedMember', 'selectedMemberName', 'selectableMembers']),
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
          !(client.member_class === this.reservedMember?.memberClass && client.member_code === this.reservedMember.memberCode),
      );
    },

    duplicateOwnerClient(): boolean {
      return (
        !!this.values.addClient.memberClass &&
        !!this.values.addClient.memberCode &&
        this.reservedMember?.memberClass.toLowerCase() === this.values.addClient.memberClass.toLowerCase() &&
        this.reservedMember?.memberCode.toLowerCase() === this.values.addClient.memberCode.toLowerCase()
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
    //eslint-disable-next-line @typescript-eslint/no-this-alias
    that = this;
    this.setAddMemberWizardMode(AddMemberWizardModes.FULL);
    this.fetchSelectableMembers(that.currentSecurityServer.instance_id);
  },
  methods: {
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
          client.member_code === this.values.addClient.memberCode && client.member_class === this.values.addClient.memberClass,
      );

      // Fill the name "field" if it's available or set it undefined
      this.setSelectedMemberName(tempClient?.member_name);

      // Pass the arguments so that we use the validated information instead of the state at that time
      this.checkClientDebounce(this.values.addClient.memberClass, this.values.addClient.memberCode);
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
            that.addError(error);
            that.checkRunning = true;
          },
        );
    }, 600),
  },
});
</script>

<style lang="scss" scoped></style>
