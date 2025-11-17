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
  <XrdWizardStep>
    <XrdFormBlock>
      <XrdFormBlockRow description="csr.helpUsage">
        <v-text-field
          :model-value="usage"
          class="xrd"
          variant="plain"
          hide-details
          :label="$t('csr.usage')"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow description="csr.helpClient">
        <v-text-field
          :model-value="selectedMemberId"
          class="xrd"
          variant="plain"
          hide-details
          :label="$t('csr.client')"
        />
      </XrdFormBlockRow>

      <XrdFormBlockRow
        description="csr.helpCertificationService"
        adjust-against-content
      >
        <v-select
          v-bind="certServiceRef"
          data-test="csr-certification-service-select"
          item-title="name"
          item-value="name"
          class="xrd"
          :items="filteredServiceList"
          :label="$t('csr.certificationService')"
        />
      </XrdFormBlockRow>

      <XrdFormBlockRow description="csr.helpCsrFormat" adjust-against-content>
        <v-select
          v-bind="csrFormatRef"
          data-test="csr-format-select"
          class="xrd"
          :items="csrFormatList"
          :label="$t('csr.csrFormat')"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>
    <template #footer>
      <XrdBtn
        data-test="cancel-button"
        variant="outlined"
        text="action.cancel"
        @click="cancel"
      />
      <v-spacer />

      <XrdBtn
        v-if="showPreviousButton"
        data-test="previous-button"
        variant="outlined"
        class="mr-2"
        text="action.previous"
        @click="previous"
      />
      <XrdBtn
        data-test="save-button"
        :loading="loading"
        :text="saveButtonText"
        :disabled="!meta.valid"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { CsrFormat } from '@/openapi-types';
import { mapState, mapWritableState } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { useAddClient } from '@/store/modules/addClient';
import {
  XrdWizardStep,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdBtn,
} from '@niis/shared-ui';
import { PublicPathState, useForm } from 'vee-validate';

export default defineComponent({
  components: {
    XrdWizardStep,
    XrdFormBlock,
    XrdFormBlockRow,
    XrdBtn,
  },
  props: {
    loading: {
      type: Boolean,
      default: false,
    },
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
    const { certificationService, csrFormat } = useCsr();
    const { meta, values, setFieldValue, defineComponentBinds } = useForm({
      validationSchema: {
        certificationService: 'required',
        csrFormat: 'required',
      },
      initialValues: {
        certificationService: certificationService,
        csrFormat: csrFormat,
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const certServiceRef = defineComponentBinds(
      'certificationService',
      componentConfig,
    );
    const csrFormatRef = defineComponentBinds('csrFormat', componentConfig);
    return { meta, values, setFieldValue, certServiceRef, csrFormatRef };
  },
  data() {
    return {
      csrFormatList: Object.values(CsrFormat).map((format) => ({
        title: format,
        value: format,
      })),
    };
  },
  computed: {
    ...mapState(useCsr, ['filteredServiceList', 'usage']),
    ...mapWritableState(useCsr, ['csrFormat', 'certificationService']),
    ...mapState(useAddClient, ['selectedMemberId']),
  },

  watch: {
    filteredServiceList(val) {
      // Set first certification service selected as default when the list is updated
      if (val?.length === 1) {
        this.setFieldValue('certificationService', val[0].name);
      }
    },
  },
  methods: {
    done(): void {
      this.csrFormat = this.values.csrFormat;
      this.certificationService = this.values.certificationService;
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

<style lang="scss" scoped></style>
