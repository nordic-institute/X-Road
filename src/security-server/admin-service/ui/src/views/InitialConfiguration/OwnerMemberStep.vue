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
  <XrdWizardStep title="wizard.memberName" sub-title="wizard.client.memberNameTooltip">
    <v-slide-y-transition>
      <div v-if="memberName" class="readonly-info-field" data-test="selected-member-name">
        {{ memberName }}
      </div>
    </v-slide-y-transition>

    <XrdFormBlock>
      <XrdFormBlockRow description="wizard.client.memberClassTooltip" adjust-against-content>
        <v-select
          v-model="memberClassMdl"
          v-bind="memberClassRef"
          data-test="member-class-input"
          class="xrd"
          :label="$t('wizard.memberClass')"
          :items="memberClassItems"
          :disabled="isServerOwnerInitialized"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow description="wizard.client.memberCodeTooltip" adjust-against-content>
        <v-text-field
          v-model="memberCodeMdl"
          v-bind="memberCodeRef"
          data-test="member-code-input"
          class="xrd"
          type="text"
          autofocus
          :label="$t('wizard.memberCode')"
          :disabled="isServerOwnerInitialized"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow description="initialConfiguration.member.serverCodeHelp" adjust-against-content>
        <v-text-field
          v-model="securityServerCodeMdl"
          v-bind="securityServerCodeRef"
          data-test="security-server-code-input"
          class="xrd"
          type="text"
          :label="$t('fields.securityServerCode')"
          :disabled="isServerCodeInitialized"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>

    <template #footer>
      <v-spacer />

      <XrdBtn
        v-if="showPreviousButton"
        data-test="previous-button"
        class="previous-button mr-4"
        text="action.previous"
        variant="outlined"
        @click="previous"
      />
      <XrdBtn data-test="owner-member-save-button" variant="flat" :text="saveButtonText" :disabled="!meta.valid" @click="done" />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';
import { useForm } from 'vee-validate';

import { useNotifications, XrdWizardStep, XrdBtn, XrdFormBlock, XrdFormBlockRow, veeDefaultFieldConfig } from '@niis/shared-ui';

import { useGeneral } from '@/store/modules/general';
import { useInitializeServer } from '@/store/modules/initializeServer';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  components: { XrdWizardStep, XrdFormBlock, XrdFormBlockRow, XrdBtn },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.continue',
    },
    showPreviousButton: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['done', 'previous'],
  setup() {
    const { addError } = useNotifications();
    const { currentSecurityServer } = useUser();
    const { meta, values, validateField, setFieldValue, defineField } = useForm({
      validationSchema: {
        memberClass: 'required',
        memberCode: 'required|xrdIdentifier',
        securityServerCode: 'required|xrdIdentifier',
      },
      initialValues: {
        memberClass: currentSecurityServer?.member_class,
        memberCode: currentSecurityServer?.member_code,
        securityServerCode: currentSecurityServer?.server_code,
      },
    });
    const componentConfig = veeDefaultFieldConfig();
    const [memberClassMdl, memberClassRef] = defineField('memberClass', componentConfig);
    const [memberCodeMdl, memberCodeRef] = defineField('memberCode', componentConfig);
    const [securityServerCodeMdl, securityServerCodeRef] = defineField('securityServerCode', componentConfig);
    return {
      meta,
      values,
      validateField,
      setFieldValue,
      memberClassRef,
      memberClassMdl,
      memberCodeRef,
      memberCodeMdl,
      securityServerCodeRef,
      securityServerCodeMdl,
      addError,
    };
  },
  computed: {
    ...mapState(useGeneral, ['memberClassesCurrentInstance', 'memberName']),
    ...mapState(useUser, ['currentSecurityServer', 'isServerCodeInitialized', 'isServerOwnerInitialized']),
    ...mapState(useInitializeServer, ['initServerMemberClass', 'initServerMemberCode', 'initServerSSCode']),
    memberClassItems() {
      return this.memberClassesCurrentInstance.map((memberClass) => ({
        title: memberClass,
        value: memberClass,
      }));
    },
  },

  watch: {
    memberClassesCurrentInstance(val: string[]) {
      // Set first member class selected if there is only one
      if (val?.length === 1) {
        this.setFieldValue('memberClass', val[0]);
      }
    },
    'values.memberClass'(val) {
      if (val) {
        this.updateMemberName();
      }
    },
    'values.memberCode'(val) {
      if (val) {
        this.updateMemberName();
      }
    },
  },
  beforeMount() {
    this.fetchMemberClassesForCurrentInstance().catch((error) => {
      if (error.response.status === 500) {
        // this can happen if anchor is not ready
        return;
      }
      this.addError(error);
    });

    this.updateMemberName();
  },
  methods: {
    ...mapActions(useInitializeServer, ['storeInitServerSSCode', 'storeInitServerMemberClass', 'storeInitServerMemberCode']),

    ...mapActions(useGeneral, ['fetchMemberClassesForCurrentInstance', 'fetchMemberName']),

    done(): void {
      this.storeInitServerMemberClass(this.values.memberClass);
      this.storeInitServerMemberCode(this.values.memberCode);
      this.storeInitServerSSCode(this.values.securityServerCode);
      this.$emit('done');
    },
    previous(): void {
      this.$emit('previous');
    },

    async updateMemberName(): Promise<void> {
      if ((await this.validateField('memberClass')).valid && (await this.validateField('memberCode')).valid) {
        this.fetchMemberName(this.values.memberClass!, this.values.memberCode!).catch((error) => {
          if (error.response.status === 404) {
            // no match found
            return;
          }
          this.addError(error);
        });
      }
    },
  },
});
</script>

<style lang="scss" scoped>
.readonly-info-field {
  max-width: 405px;
  height: 60px;
}
</style>
