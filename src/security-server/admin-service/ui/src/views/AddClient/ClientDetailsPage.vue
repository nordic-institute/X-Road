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
          >{{ $t('wizard.selectClient') }}
        </xrd-button>
      </div>
    </div>

    <div class="wizard-step-form-content">
      <wizard-row-wrap-t
        label="wizard.memberName"
        tooltip="wizard.client.memberNameTooltip"
      >
        <div data-test="selected-member-name">{{ selectedMemberName }}</div>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        label="wizard.memberClass"
        tooltip="wizard.client.memberClassTooltip"
      >
        <v-select
          v-model="memberClassMdl"
          v-bind="memberClassAttr"
          :items="memberClassItems"
          class="wizard-form-input"
          data-test="member-class-input"
          :placeholder="$t('wizard.selectMemberClass')"
          variant="outlined"
        ></v-select>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        label="wizard.memberCode"
        tooltip="wizard.client.memberCodeTooltip"
      >
        <v-text-field
          v-model="memberCodeMdl"
          v-bind="memberCodeAttr"
          class="wizard-form-input"
          type="text"
          autofocus
          :placeholder="$t('wizard.memberCode')"
          variant="outlined"
          data-test="member-code-input"
        ></v-text-field>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        label="wizard.subsystemCode"
        tooltip="wizard.client.subsystemCodeTooltip"
      >
        <v-text-field
          v-model="subsystemCodeMdl"
          v-bind="subsystemCodeAttr"
          class="wizard-form-input"
          type="text"
          variant="outlined"
          :placeholder="$t('wizard.subsystemCode')"
          data-test="subsystem-code-input"
        ></v-text-field>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        v-if="doesSupportSubsystemNames"
        label="wizard.subsystemName"
        tooltip="wizard.client.subsystemNameTooltip"
      >
        <v-text-field
          v-model="subsystemNameMdl"
          v-bind="subsystemNameAttr"
          class="wizard-form-input"
          type="text"
          variant="outlined"
          :placeholder="$t('wizard.subsystemName')"
          data-test="subsystem-name-input"
        ></v-text-field>
      </wizard-row-wrap-t>
    </div>
    <div class="button-footer">
      <xrd-button outlined data-test="cancel-button" @click="cancel"
        >{{ $t('action.cancel') }}
      </xrd-button>
      <xrd-button :disabled="!meta.valid" data-test="next-button" @click="done"
        >{{ $t('action.next') }}
      </xrd-button>
    </div>

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
import { defineComponent } from 'vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { containsClient, debounce, isEmpty } from '@/util/helpers';
import { Client } from '@/openapi-types';
import { AddMemberWizardModes } from '@/global';
import { defineRule, PublicPathState, useForm } from 'vee-validate';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useAddClient } from '@/store/modules/addClient';
import { useNotifications } from '@/store/modules/notifications';
import { useGeneral } from '@/store/modules/general';
import { useUser } from '@/store/modules/user';
import WizardRowWrapT from '@/components/ui/WizardRowWrapT.vue';
import { useI18n } from 'vue-i18n';
import { useSystem } from '@/store/modules/system';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    WizardRowWrapT,
    SelectClientDialog,
  },
  emits: ['cancel', 'done'],
  setup() {
    const { reservedClients, memberClass, memberCode } = useAddClient();
    const { t } = useI18n();

    function uniqueClient(subsystemCode: string) {
      if (
        containsClient(reservedClients, memberClass, memberCode, subsystemCode)
      ) {
        return t('wizard.client.clientExists');
      }
      return true;
    }

    defineRule('uniqueClient', uniqueClient);
    const { meta, values, validateField, setFieldValue, defineField } = useForm(
      {
        validationSchema: {
          'addClient.memberClass': 'required',
          'addClient.memberCode': 'required|max:255|xrdIdentifier',
          'addClient.subsystemCode':
            'required|max:255|xrdIdentifier|uniqueClient',
          'addClient.subsystemName': 'max:255|xrdIdentifier',
        },
        initialValues: {
          addClient: {
            memberClass: '',
            memberCode: '',
            subsystemCode: '',
            subsystemName: '',
          },
        },
      },
    );
    const componentConfig = {
      props: (state: PublicPathState) => ({ 'error-messages': state.errors }),
    };
    const [memberClassMdl, memberClassAttr] = defineField(
      'addClient.memberClass',
      componentConfig,
    );
    const [memberCodeMdl, memberCodeAttr] = defineField(
      'addClient.memberCode',
      componentConfig,
    );
    const [subsystemCodeMdl, subsystemCodeAttr] = defineField(
      'addClient.subsystemCode',
      componentConfig,
    );
    const [subsystemNameMdl, subsystemNameAttr] = defineField(
      'addClient.subsystemName',
      componentConfig,
    );
    return {
      meta,
      values,
      validateField,
      setFieldValue,
      memberClassMdl,
      memberClassAttr,
      memberCodeMdl,
      memberCodeAttr,
      subsystemCodeMdl,
      subsystemCodeAttr,
      subsystemNameMdl,
      subsystemNameAttr,
    };
  },
  data() {
    return {
      showSelectClient: false,
      checkRunning: false,
      isMemberCodeValid: true,
    };
  },
  computed: {
    ...mapState(useAddClient, ['selectableClients', 'selectedMemberName']),
    ...mapWritableState(useAddClient, [
      'memberClass',
      'memberCode',
      'subsystemCode',
      'subsystemName',
    ]),
    ...mapState(useGeneral, ['memberClassesCurrentInstance']),
    ...mapState(useUser, ['currentSecurityServer']),
    ...mapState(useSystem, ['doesSupportSubsystemNames']),
    memberClassItems() {
      return this.memberClassesCurrentInstance.map((memberClass) => ({
        title: memberClass,
        value: memberClass,
      }));
    },
  },
  watch: {
    async 'values.addClient.memberCode'(val) {
      // Set wizard mode to default (full)
      this.setAddMemberWizardMode(AddMemberWizardModes.FULL);

      // Needs to be done here, because the watcher runs before the setter
      this.validateField('addClient.memberCode').then((result) => {
        if (result.valid) {
          this.isMemberCodeValid = true;

          if (isEmpty(val) || isEmpty(this.values.addClient.memberClass)) {
            return;
          }
          this.checkClient();
        } else {
          this.isMemberCodeValid = false;
        }
      });
    },
    'values.addClient.memberClass'(val): void {
      // Set wizard mode to default (full)
      this.setAddMemberWizardMode(AddMemberWizardModes.FULL);
      if (isEmpty(val) || isEmpty(this.values.addClient.memberCode)) {
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
    this.fetchSelectableClients(that.currentSecurityServer.instance_id);
    this.fetchMemberClassesForCurrentInstance();
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
      'updateAddMemberWizardModeIfNeeded',
    ]),

    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.memberClass = this.values.addClient.memberClass;
      this.memberCode = this.values.addClient.memberCode;
      this.subsystemCode = this.values.addClient.subsystemCode;
      this.subsystemName = this.values.addClient.subsystemName;
      this.$emit('done');
    },
    saveSelectedClient(selectedMember: Client): void {
      this.setSelectedMember(selectedMember);
      this.setFieldValue('addClient.memberClass', selectedMember.member_class);
      this.setFieldValue('addClient.memberCode', selectedMember.member_code);
      this.setFieldValue(
        'addClient.subsystemCode',
        selectedMember.subsystem_code ?? '',
      );
      this.setFieldValue(
        'addClient.subsystemName',
        selectedMember.subsystem_name ?? '',
      );
      this.fetchReservedClients(selectedMember).catch((error) =>
        this.showError(error),
      );

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
          client.member_code === this.values.addClient.memberCode &&
          client.member_class === this.values.addClient.memberClass
        );
      });

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
@use '@niis/shared-ui/src/assets/wizards';
</style>
