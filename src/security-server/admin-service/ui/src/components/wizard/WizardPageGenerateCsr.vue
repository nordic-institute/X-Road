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
      <XrdFormBlockRow v-for="item in csrForm" :key="item.id" :description="`certificateProfile.${item.label_key}`" adjust-against-content>
        <v-text-field
          v-model="componentModel(item.id).value"
          v-bind="componentAttr(item.id).value"
          class="xrd"
          :name="item.id"
          type="text"
          :disabled="item.read_only"
          :label="$t(`certificateProfile.${item.label_key}`)"
          :data-test="`dynamic-csr-input_${item.id}`"
          :autofocus="autofocusField === item.id"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>
    <div class="generate-row d-flex flex-row align-center justify-space-between mt-6">
      <div class="body-regular font-weight-medium">
        {{ $t('csr.saveInfo') }}
      </div>
      <XrdBtn
        :disabled="generateCsrDisabled"
        data-test="generate-csr-button"
        prepend-icon="question_exchange"
        :loading="generateCsrLoading"
        text="csr.generateCsr"
        @click="generateCsr(false)"
      />
    </div>
    <template v-if="acmeCapable">
      <div v-if="acmeCapable" class="generate-row d-flex flex-row align-center justify-space-between mt-6">
        <div class="body-regular font-weight-medium">
          {{ $t('csr.orderAcmeCertificate') }}
        </div>
        <XrdBtn
          :disabled="orderCertificateDisabled"
          data-test="acme-order-certificate-button"
          :loading="orderCertificateLoading"
          text="keys.orderAcmeCertificate"
          @click="generateCsr(true)"
        />
      </div>
      <v-alert
        v-if="externalAccountBindingRequiredButMissing"
        class="mt-4"
        border="start"
        type="error"
        variant="outlined"
        density="compact"
      >
        {{ externalAccountBindingRequiredButMissingHint }}
      </v-alert>
    </template>
    <template #footer>
      <XrdBtn data-test="cancel-button" variant="text" text="action.cancel" :disabled="!disableDone" @click="cancel" />
      <v-spacer />
      <XrdBtn
        data-test="previous-button"
        variant="outlined"
        text="action.previous"
        prepend-icon="arrow_back"
        class="mr-2"
        :disabled="!disableDone"
        @click="previous"
      />
      <XrdBtn data-test="save-button" :text="saveButtonText" prepend-icon="check" :disabled="disableDone" @click="done" />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent, Ref } from 'vue';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { AxiosError } from 'axios';
import { useForm } from 'vee-validate';
import { CsrSubjectFieldDescription } from '@/openapi-types';
import { XrdWizardStep, XrdBtn, XrdFormBlock, XrdFormBlockRow, useNotifications, veeDefaultFieldConfig } from '@niis/shared-ui';

export default defineComponent({
  components: {
    XrdWizardStep,
    XrdBtn,
    XrdFormBlock,
    XrdFormBlockRow,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.done',
    },
    // Creating Key + CSR or just CSR
    keyAndCsr: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['cancel', 'previous', 'done'],
  setup() {
    const { addSuccessMessage, addError } = useNotifications();
    const { csrForm }: { csrForm: CsrSubjectFieldDescription[] } = useCsr();
    const validationSchema: Record<string, string> = csrForm.reduce((acc, cur) => ({ ...acc, [cur.id]: cur.required && 'required' }), {});
    const initialValues: Record<string, string> = csrForm.reduce((acc, cur) => ({ ...acc, [cur.id]: cur.default_value }), {});
    const { meta, values, defineField } = useForm({
      validationSchema,
      initialValues,
    });

    const formModels = {} as Record<string, Ref>;
    const formAttrs = {} as Record<string, Ref>;

    csrForm.forEach((formField) => {
      const [field, fieldAttr] = defineField(formField.id, veeDefaultFieldConfig());
      formModels[formField.id] = field;
      formAttrs[formField.id] = fieldAttr;
    });

    return {
      meta,
      values,
      formModels,
      formAttrs,
      csrForm,
      addSuccessMessage,
      addError,
    };
  },
  data() {
    return {
      disableDone: true,
      genCsrLoading: false,
    };
  },
  computed: {
    ...mapState(useCsr, ['csrTokenId', 'acmeCapable', 'eabRequired', 'acmeEabCredentialsStatus']),
    ...mapWritableState(useCsr, ['acmeOrder']),
    externalAccountBindingRequiredButMissing(): boolean {
      return !!this.eabRequired && !this.acmeEabCredentialsStatus?.has_acme_external_account_credentials;
    },
    externalAccountBindingRequiredButMissingHint(): string | undefined {
      return this.externalAccountBindingRequiredButMissing ? this.$t('csr.eabCredRequired') : undefined;
    },
    orderCertificateDisabled(): boolean {
      return !this.meta.valid || !this.disableDone || this.externalAccountBindingRequiredButMissing || this.generateCsrLoading;
    },
    generateCsrDisabled(): boolean {
      return !this.meta.valid || !this.disableDone || this.orderCertificateLoading;
    },
    orderCertificateLoading(): boolean {
      return this.genCsrLoading && this.acmeOrder;
    },
    generateCsrLoading(): boolean {
      return this.genCsrLoading && !this.acmeOrder;
    },
    autofocusField(): string | undefined {
      return this.csrForm
        .filter((field) => !field.read_only)
        .map((field) => field.id)
        .shift();
    },
  },
  created() {
    this.acmeOrder = false;
  },
  methods: {
    ...mapActions(useCsr, ['setCsrForm', 'requestGenerateCsr', 'generateKeyAndCsr']),
    componentModel(id: string): Ref<string> {
      return this.formModels[id];
    },
    componentAttr(id: string): Ref {
      return this.formAttrs[id];
    },
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.$emit('done');
    },
    async generateCsr(withAcmeOrder: boolean): Promise<void> {
      this.genCsrLoading = true;
      this.setCsrForm(
        this.csrForm.map((field: CsrSubjectFieldDescription) => ({
          ...field,
          default_value: this.values[field.id],
        })),
      );
      this.acmeOrder = withAcmeOrder;
      if (this.keyAndCsr) {
        // Create Key AND CSR

        if (!this.csrTokenId) {
          // Should not happen
          throw new Error('Token id does not exist');
        }

        // Create key and CSR
        await this.generateKeyAndCsr(this.csrTokenId)
          .then(() => {
            if (this.acmeOrder) {
              this.addSuccessMessage('keys.acmeCertOrdered');
            }
            this.disableDone = false;
          })
          .catch((error) => {
            this.disableDone = true;
            // Error comes from axios, so it most probably is AxiosError
            this.addError(error as AxiosError);
          })
          .finally(() => {
            this.genCsrLoading = false;
          });
      } else {
        // Create only CSR
        await this.requestGenerateCsr()
          .then(() => {
            if (this.acmeOrder) {
              this.addSuccessMessage(this.$t('keys.acmeCertOrdered'));
            }
            this.disableDone = false;
          })
          .catch((error) => {
            this.disableDone = true;
            // Error comes from axios, so it most probably is AxiosError
            this.addError(error as AxiosError);
          })
          .finally(() => {
            this.genCsrLoading = false;
          });
      }
    },
  },
});
</script>

<style lang="scss" scoped></style>
