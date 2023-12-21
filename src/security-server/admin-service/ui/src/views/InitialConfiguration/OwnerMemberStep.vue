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
    <div class="wizard-step-form-content">
      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('wizard.memberName')"
          :help-text="$t('wizard.client.memberNameTooltip')"
        />
        <div
          v-if="memberName"
          class="readonly-info-field"
          data-test="selected-member-name"
        >
          {{ memberName }}
        </div>
        <div v-else class="readonly-info-field"></div>
      </div>

      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('wizard.memberClass')"
          :help-text="$t('wizard.client.memberClassTooltip')"
        />
        <v-select
          v-bind="memberClassRef"
          :items="memberClassItems"
          :disabled="isServerOwnerInitialized"
          data-test="member-class-input"
          class="wizard-form-input"
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
          :disabled="isServerOwnerInitialized"
          autofocus
          data-test="member-code-input"
        ></v-text-field>
      </div>

      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('fields.securityServerCode')"
          :help-text="$t('initialConfiguration.member.serverCodeHelp')"
        />
        <v-text-field
          v-bind="securityServerCodeRef"
          class="wizard-form-input"
          type="text"
          :disabled="isServerCodeInitialized"
          data-test="security-server-code-input"
        ></v-text-field>
      </div>
    </div>
    <div class="button-footer">
      <v-spacer></v-spacer>
      <div>
        <xrd-button
          v-if="showPreviousButton"
          outlined
          class="previous-button"
          data-test="previous-button"
          @click="previous"
          >{{ $t('action.previous') }}
        </xrd-button>
        <xrd-button
          :disabled="!meta.valid"
          data-test="owner-member-save-button"
          @click="done"
          >{{ $t(saveButtonText) }}
        </xrd-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useGeneral } from '@/store/modules/general';
import { useUser } from '@/store/modules/user';
import { useInitializeServer } from '@/store/modules/initializeServer';
import { PublicPathState, useForm } from 'vee-validate';

export default defineComponent({
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
    const { currentSecurityServer } = useUser();
    const { meta, values, validateField, setFieldValue, defineComponentBinds } =
      useForm({
        validationSchema: {
          memberClass: 'required',
          memberCode: 'required|xrdIdentifier',
          securityServerCode: 'required|xrdIdentifier',
        },
        initialValues: {
          memberClass: currentSecurityServer.member_class,
          memberCode: currentSecurityServer.member_code,
          securityServerCode: currentSecurityServer.server_code,
        },
      });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const memberClassRef = defineComponentBinds('memberClass', componentConfig);
    const memberCodeRef = defineComponentBinds('memberCode', componentConfig);
    const securityServerCodeRef = defineComponentBinds(
      'securityServerCode',
      componentConfig,
    );
    return {
      meta,
      values,
      validateField,
      setFieldValue,
      memberClassRef,
      memberCodeRef,
      securityServerCodeRef,
    };
  },
  computed: {
    ...mapState(useGeneral, ['memberClassesCurrentInstance', 'memberName']),
    ...mapState(useUser, [
      'currentSecurityServer',
      'isServerCodeInitialized',
      'isServerOwnerInitialized',
    ]),
    ...mapState(useInitializeServer, [
      'initServerMemberClass',
      'initServerMemberCode',
      'initServerSSCode',
    ]),
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
      this.showError(error);
    });

    this.updateMemberName();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useInitializeServer, [
      'storeInitServerSSCode',
      'storeInitServerMemberClass',
      'storeInitServerMemberCode',
    ]),

    ...mapActions(useGeneral, [
      'fetchMemberClassesForCurrentInstance',
      'fetchMemberName',
    ]),

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
      if (
        (await this.validateField('memberClass')).valid &&
        (await this.validateField('memberCode')).valid
      ) {
        this.fetchMemberName(
          this.values.memberClass!,
          this.values.memberCode!,
        ).catch((error) => {
          if (error.response.status === 404) {
            // no match found
            return;
          }
          this.showError(error);
        });
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/wizards';

.readonly-info-field {
  max-width: 405px;
  height: 60px;
  padding-top: 12px;
}
</style>
