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
            v-model="componentBinds[item.id][0].value"
            v-bind="componentBinds[item.id][1].value"
            class="wizard-form-input"
            :name="item.id"
            type="text"
            variant="outlined"
            :disabled="item.read_only"
            :data-test="`dynamic-csr-input_${item.id}`"
            :autofocus = "autoFocusedField === item.id"
          ></v-text-field>
        </div>
      </div>
      <div class="generate-row">
        <div>{{ $t('csr.saveInfo') }}</div>
        <xrd-button
          :disabled="!meta.valid || !disableDone"
          data-test="generate-csr-button"
          :loading="genCsrLoading && !csr.acmeOrder"
          @click="generateCsr(false)"
        >{{ $t('csr.generateCsr') }}
        </xrd-button>
      </div>
      <div v-if="csr.acmeCapable" class="generate-row">
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
          :disabled="!meta.valid || !disableDone || externalAccountBindingRequiredButMissing"
          data-test="acme-order-certificate-button"
          :loading="genCsrLoading && csr.acmeOrder"
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

<script lang="ts" setup>
import { computed, ref, Ref } from 'vue';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { useForm } from 'vee-validate';
import { CsrSubjectFieldDescription } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { AxiosError } from 'axios';
import { i18n } from '@/plugins/i18n';

const props = defineProps({
  saveButtonText: {
    type: String,
    default: 'action.done',
  },
  // Creating Key + CSR or just CSR
  keyAndCsr: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['cancel', 'previous', 'done']);

const { t } = i18n.global;

const { csrForm }: { csrForm: CsrSubjectFieldDescription[] } = useCsr();

const validationSchema = {} as Record<string, string>;
const initialValues = {} as Record<string, string | undefined>;

csrForm.forEach((field) => {
  validationSchema[field.id] = field.required ? 'required' : '';
  initialValues[field.id] = field.default_value;
});

const { meta, values, defineField } = useForm({
  validationSchema,
  initialValues,
});

const componentBinds = {} as Record<string, Ref[]>;

csrForm.forEach((field) => {
  componentBinds[field.id] =
    defineField(field.id, {
      props: (state) => ({ 'error-messages': state.errors }),
    });
});

const disableDone = ref(true);
const genCsrLoading = ref(false);

const csr = useCsr();

const { showError, showSuccess } = useNotifications();

const externalAccountBindingRequiredButMissing = computed(() => {
  return (
    !!csr.eabRequired &&
    !csr.acmeEabCredentialsStatus?.has_acme_external_account_credentials
  );
});

const externalAccountBindingRequiredButMissingHint = computed(() => {
  return externalAccountBindingRequiredButMissing.value
    ? t('csr.eabCredRequired')
    : undefined;
});

const autoFocusedField = computed(() => {
 return  csrForm
    .filter((field) => !field.read_only)
    .map((field) => field.id)
    .shift();
});

function cancel() {
  emit('cancel');
}

function previous() {
  emit('previous');
}

function done() {
  emit('done');
}

async function generateCsr(withAcmeOrder: boolean): Promise<void> {
  genCsrLoading.value = true;
  csr.setCsrForm(csrForm.map((field: CsrSubjectFieldDescription) => ({
      ...field,
      default_value: values[field.id],
    })),
  );
  csr.acmeOrder = withAcmeOrder;
  if (props.keyAndCsr) {
    // Create Key AND CSR

    if (!csr.csrTokenId) {
      // Should not happen
      throw new Error('Token id does not exist');
    }

    // Create key and CSR
    await csr.generateKeyAndCsr(csr.csrTokenId)
      .then(() => {
        if (csr.acmeOrder) {
          showSuccess(t('keys.acmeCertOrdered'));
        }
        disableDone.value = false;
      })
      .catch((error) => {
        disableDone.value = true;
        // Error comes from axios, so it most probably is AxiosError
        showError(error as AxiosError);
      })
      .finally(() => {
        genCsrLoading.value = false;
      });
  } else {
    // Create only CSR
    await csr.requestGenerateCsr()
      .then(() => {
        if (csr.acmeOrder) {
          showSuccess(t('keys.acmeCertOrdered'));
        }
        disableDone.value = false;
      })
      .catch((error) => {
        disableDone.value = true;
        // Error comes from axios, so it most probably is AxiosError
        showError(error as AxiosError);
      })
      .finally(() => {
        genCsrLoading.value = false;
      });
  }
}

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
