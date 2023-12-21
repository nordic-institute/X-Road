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

        <div class="readonly-info-field">{{ usage }}</div>
      </div>

      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('csr.client')"
          :help-text="$t('csr.helpClient')"
        />

        <div class="readonly-info-field">{{ selectedMemberId }}</div>
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
import { CsrFormat } from '@/openapi-types';
import { mapState, mapWritableState } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { useAddClient } from '@/store/modules/addClient';
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

<style lang="scss" scoped>
@import '../../assets/wizards';

.readonly-info-field {
  max-width: 300px;
  height: 60px;
  padding-top: 12px;
}
</style>
