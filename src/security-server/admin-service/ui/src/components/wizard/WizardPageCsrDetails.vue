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
    <div class="wizard-step-form-content">
      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('csr.usage')"
          :help-text="$t('csr.helpUsage')"
        />
        <v-select
          v-bind="usageRef"
          :items="usageList"
          class="wizard-form-input"
          :disabled="isUsageReadOnly || !permissionForUsage"
          data-test="csr-usage-select"
          variant="outlined"
        ></v-select>
      </div>

      <div v-show="showClient" key="csr-client-field" class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('csr.client')"
          :help-text="$t('csr.helpClient')"
        />
        <v-select
          v-bind="csrClientRef"
          :items="memberIdItems"
          class="wizard-form-input"
          data-test="csr-client-select"
          variant="outlined"
        ></v-select>
      </div>

      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('csr.certificationService')"
          :help-text="$t('csr.helpCertificationService')"
        />
        <v-select
          v-bind="certServiceRef"
          :items="filteredServiceList"
          item-title="name"
          item-value="name"
          class="wizard-form-input"
          data-test="csr-certification-service-select"
          variant="outlined"
        ></v-select>
      </div>

      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('csr.csrFormat')"
          :help-text="$t('csr.helpCsrFormat')"
        />
        <v-select
          v-bind="csrFormatRef"
          :items="csrFormatList"
          class="wizard-form-input"
          data-test="csr-format-select"
          variant="outlined"
        ></v-select>
      </div>
    </div>
    <div class="button-footer">
      <xrd-button outlined data-test="cancel-button" @click="cancel"
        >{{ $t('action.cancel') }}
      </xrd-button>

      <xrd-button
        v-if="showPreviousButton"
        outlined
        class="previous-button"
        data-test="previous-button"
        @click="previous"
        >{{ $t('action.previous') }}
      </xrd-button>
      <xrd-button :disabled="!meta.valid" data-test="save-button" @click="done">
        {{ $t(saveButtonText) }}
      </xrd-button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { Permissions } from '@/global';
import { CsrFormat, KeyUsageType } from '@/openapi-types';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { defineRule, PublicPathState, useForm } from 'vee-validate';
import { FieldValidationMetaInfo } from '@vee-validate/i18n';
import i18n from '@/plugins/i18n';

defineRule(
  'requiredIfSigning',
  (value: string, _, ctx: FieldValidationMetaInfo): string | boolean => {
    const usage = (ctx.form.csr as Record<string, never>).usage;
    if (usage === KeyUsageType.SIGNING && (!value || !value.length)) {
      return i18n.global.t('customValidation.requiredIf', {
        fieldName: i18n.global.t(`fields.${ctx.field}`),
      });
    }
    return true;
  },
);

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
  emits: ['done', 'previous', 'cancel'],
  setup() {
    const { usage, csrClient, certificationService, csrFormat } = useCsr();
    const { meta, values, setFieldValue, defineComponentBinds } = useForm({
      validationSchema: {
        'csr.usage': 'required',
        'csr.client': 'requiredIfSigning',
        'csr.certificationService': 'required',
        'csr.csrFormat': 'required',
      },
      initialValues: {
        csr: {
          usage: usage,
          client: csrClient,
          certificationService: certificationService,
          csrFormat: csrFormat,
        },
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const usageRef = defineComponentBinds('csr.usage', componentConfig);
    const csrClientRef = defineComponentBinds('csr.client', componentConfig);
    const certServiceRef = defineComponentBinds(
      'csr.certificationService',
      componentConfig,
    );
    const csrFormatRef = defineComponentBinds('csr.csrFormat', componentConfig);
    return {
      meta,
      values,
      setFieldValue,
      usageRef,
      csrClientRef,
      certServiceRef,
      csrFormatRef,
    };
  },
  data() {
    return {
      usageTypes: KeyUsageType,
      usageList: Object.values(KeyUsageType).map((usageType) => ({
        title: usageType,
        value: usageType,
      })),
      csrFormatList: Object.values(CsrFormat).map((csrFormat) => ({
        title: csrFormat,
        value: csrFormat,
      })),
      permissionForUsage: true,
    };
  },
  computed: {
    ...mapState(useCsr, [
      'memberIds',
      'filteredServiceList',
      'isUsageReadOnly',
    ]),
    ...mapWritableState(useCsr, [
      'usage',
      'csrClient',
      'csrFormat',
      'certificationService',
    ]),
    ...mapState(useUser, ['hasPermission']),
    memberIdItems() {
      return this.memberIds.map((id) => ({ title: id, value: id }));
    },
    showClient(): boolean {
      return this.values.csr.usage === this.usageTypes.SIGNING;
    },
  },

  watch: {
    filteredServiceList(val) {
      // Set first certification service selected as default when the list is updated
      if (val?.length === 1) {
        this.setFieldValue('csr.certificationService', val[0].name);
      }
    },
    memberIds(val) {
      // Set first client selected as default when the list is updated
      if (val?.length === 1) {
        this.setFieldValue('csr.client', val[0].id);
      }
    },
    usage(val) {
      this.setFieldValue('csr.usage', val);
    },
  },

  created() {
    // Fetch member id:s for the client selection dropdown
    this.fetchAllMemberIds();

    // Check if the user has permission for only one type of CSR
    const signPermission = this.hasPermission(
      Permissions.GENERATE_SIGN_CERT_REQ,
    );
    const authPermission = this.hasPermission(
      Permissions.GENERATE_AUTH_CERT_REQ,
    );

    if (signPermission && !authPermission) {
      // lock usage type to sign
      this.setFieldValue('csr.usage', KeyUsageType.SIGNING);
      this.permissionForUsage = false;
    }

    if (!signPermission && authPermission) {
      // lock usage type to auth
      this.setFieldValue('csr.usage', KeyUsageType.AUTHENTICATION);
      this.permissionForUsage = false;
    }
  },
  methods: {
    ...mapActions(useCsr, ['fetchAllMemberIds']),
    done(): void {
      this.usage = this.values.csr.usage;
      this.csrClient = this.values.csr.client;
      this.certificationService = this.values.csr.certificationService;
      this.csrFormat = this.values.csr.csrFormat;
      this.$emit('done');
    },
    previous(): void {
      this.$emit('previous');
    },
    cancel(): void {
      this.$emit('cancel');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
