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
    <v-alert v-if="errorMessage" type="error" variant="outlined" class="mb-4" closable @click:close="errorMessage = ''">
      {{ errorMessage }}
    </v-alert>

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
        @click="$emit('previous')"
      />
      <XrdBtn
        data-test="server-conf-save-button"
        variant="flat"
        text="action.continue"
        :disabled="!meta.valid"
        :loading="busy"
        @click="submit"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';
import { useForm } from 'vee-validate';

import { useNotifications, XrdWizardStep, XrdBtn, XrdFormBlock, XrdFormBlockRow, veeDefaultFieldConfig } from '@niis/shared-ui';

import { useGeneral } from '@/store/modules/general';
import { useInitializationV2 } from '@/store/modules/initializationV2';

export default defineComponent({
  components: { XrdWizardStep, XrdFormBlock, XrdFormBlockRow, XrdBtn },
  props: {
    showPreviousButton: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['done', 'previous'],
  setup() {
    const { addError } = useNotifications();
    const { meta, values, validateField, setFieldValue, defineField } = useForm({
      validationSchema: {
        memberClass: 'required',
        memberCode: 'required|xrdIdentifier',
        securityServerCode: 'required|xrdIdentifier',
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
  data() {
    return {
      busy: false,
      errorMessage: '',
    };
  },
  computed: {
    ...mapState(useGeneral, ['memberClassesCurrentInstance', 'memberName']),
    memberClassItems() {
      return this.memberClassesCurrentInstance.map((memberClass: string) => ({
        title: memberClass,
        value: memberClass,
      }));
    },
  },
  watch: {
    memberClassesCurrentInstance(val: string[]) {
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
      if (error.response?.status === 500) {
        return;
      }
      this.addError(error);
    });
    this.updateMemberName();
  },
  methods: {
    ...mapActions(useGeneral, ['fetchMemberClassesForCurrentInstance', 'fetchMemberName']),
    ...mapActions(useInitializationV2, ['initServerConf']),

    async submit(): Promise<void> {
      this.busy = true;
      this.errorMessage = '';
      try {
        await this.initServerConf({
          security_server_code: this.values.securityServerCode!,
          owner_member_class: this.values.memberClass!,
          owner_member_code: this.values.memberCode!,
        });
        this.$emit('done');
      } catch (error: any) {
        this.errorMessage =
          error?.response?.data?.error?.description || error?.response?.data?.message || this.$t('initialConfigurationV2.serverConf.error');
        this.addError(error);
      } finally {
        this.busy = false;
      }
    },

    async updateMemberName(): Promise<void> {
      if ((await this.validateField('memberClass')).valid && (await this.validateField('memberCode')).valid) {
        this.fetchMemberName(this.values.memberClass!, this.values.memberCode!).catch((error) => {
          if (error.response?.status === 404) {
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
