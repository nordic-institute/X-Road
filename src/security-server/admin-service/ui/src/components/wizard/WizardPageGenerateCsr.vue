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
      <div v-for="item in csrForm" :key="item.id" class="wizard-row-wrap">
        <div class="wizard-label">
          {{ $t(`certificateProfile.${item.label_key}`) }}
        </div>

        <div>
          <v-text-field
            v-bind="componentRef(item.id)"
            class="wizard-form-input"
            :name="item.id"
            type="text"
            variant="outlined"
            :disabled="item.read_only"
            :data-test="`dynamic-csr-input_${item.id}`"
            :autofocus="autofocusField === item.id"
          ></v-text-field>
        </div>
      </div>
      <div class="generate-row">
        <div>{{ $t('csr.saveInfo') }}</div>
        <xrd-button
          :disabled="generateCsrDisabled"
          data-test="generate-csr-button"
          :loading="generateCsrLoading"
          @click="generateCsr(false)"
        >{{ $t('csr.generateCsr') }}
        </xrd-button>
      </div>
      <div v-if="acmeCapable" class="generate-row">
        <div>{{ $t('csr.orderAcmeCertificate') }}
          <v-alert
            v-if="externalAccountBindingRequiredButMissing"
            border="start"
            type="error"
            variant="outlined"
            density="compact"
          >
            {{ externalAccountBindingRequiredButMissingHint }}
          </v-alert>
        </div>
        <xrd-button
          :disabled="orderCertificateDisabled"
          data-test="acme-order-certificate-button"
          :loading="orderCertificateLoading"
          @click="generateCsr(true)"
        >{{ $t('keys.orderAcmeCertificate') }}
        </xrd-button>
      </div>
    </div>
    <div class="button-footer">
      <xrd-button
        outlined
        :disabled="!disableDone"
        data-test="cancel-button"
        @click="cancel"
      >{{ $t('action.cancel') }}
      </xrd-button>

      <xrd-button
        outlined
        class="previous-button"
        data-test="previous-button"
        :disabled="!disableDone"
        @click="previous"
      >{{ $t('action.previous') }}
      </xrd-button>
      <xrd-button :disabled="disableDone" data-test="save-button" @click="done"
      >{{ $t(saveButtonText) }}
      </xrd-button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, Ref } from 'vue';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { AxiosError } from 'axios';
import { PublicPathState, useForm } from 'vee-validate';
import { CsrSubjectFieldDescription } from '@/openapi-types';

export default defineComponent({
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
    const { csrForm }: { csrForm: CsrSubjectFieldDescription[] } = useCsr();
    const validationSchema: Record<string, string> = csrForm.reduce(
      (acc, cur) => ({ ...acc, [cur.id]: cur.required && 'required' }),
      {},
    );
    const initialValues: Record<string, string> = csrForm.reduce(
      (acc, cur) => ({ ...acc, [cur.id]: cur.default_value }),
      {},
    );
    const { meta, values, defineComponentBinds } = useForm({
      validationSchema,
      initialValues,
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const componentBinds: Record<string, Ref> = csrForm.reduce(
      (acc, cur) => ({
        ...acc,
        [cur.id]: defineComponentBinds(cur.id, componentConfig),
      }),
      {},
    );
    return { meta, values, ...componentBinds, csrForm };
  },
  data() {
    return {
      disableDone: true,
      genCsrLoading: false,
    };
  },
  computed: {
    ...mapState(useCsr, [
      'csrTokenId',
      'acmeCapable',
      'eabRequired',
      'acmeEabCredentialsStatus',
    ]),
    ...mapWritableState(useCsr, ['acmeOrder']),
    externalAccountBindingRequiredButMissing(): boolean {
      return (
        !!this.eabRequired &&
        !this.acmeEabCredentialsStatus?.has_acme_external_account_credentials
      );
    },
    externalAccountBindingRequiredButMissingHint(): string | undefined {
      return this.externalAccountBindingRequiredButMissing
        ? this.$t('csr.eabCredRequired')
        : undefined;
    },
    orderCertificateDisabled(): boolean {
      return (
        !this.meta.valid ||
        !this.disableDone ||
        this.externalAccountBindingRequiredButMissing ||
        this.generateCsrLoading
      );
    },
    generateCsrDisabled(): boolean {
      return (
        !this.meta.valid ||
        !this.disableDone ||
        this.orderCertificateLoading
      );
    },
    orderCertificateLoading(): boolean {
      return (this.genCsrLoading && this.acmeOrder);
    },
    generateCsrLoading(): boolean {
      return (this.genCsrLoading && !this.acmeOrder);
    },
    autofocusField(): string | undefined {
      return this.csrForm
        .filter(field => !field.read_only)
        .map(field => field.id)
        .shift();
    },
  },
  created() {
    this.acmeOrder = false;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsr, [
      'setCsrForm',
      'requestGenerateCsr',
      'generateKeyAndCsr',
    ]),
    componentRef(id: string): Ref {
      return (this as never)[id];
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
              this.showSuccess(this.$t('keys.acmeCertOrdered'));
            }
            this.disableDone = false;
          })
          .catch((error) => {
            this.disableDone = true;
            // Error comes from axios, so it most probably is AxiosError
            this.showError(error as AxiosError);
          })
          .finally(() => {
            this.genCsrLoading = false;
          });
      } else {
        // Create only CSR
        await this.requestGenerateCsr()
          .then(() => {
            if (this.acmeOrder) {
              this.showSuccess(this.$t('keys.acmeCertOrdered'));
            }
            this.disableDone = false;
          })
          .catch((error) => {
            this.disableDone = true;
            // Error comes from axios, so it most probably is AxiosError
            this.showError(error as AxiosError);
          })
          .finally(() => {
            this.genCsrLoading = false;
          });
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.generate-row {
  margin-top: 40px;
  width: 840px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
</style>
