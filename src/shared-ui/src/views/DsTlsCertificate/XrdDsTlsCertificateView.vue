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
  <XrdView data-test="ds-tls-certificate-view" :title="title" :loading="loading">
    <template v-if="$slots.tabs" #tabs>
      <slot name="tabs" />
    </template>

    <XrdSubView>
      <template #header>
        <v-spacer />
        <XrdBtn
          v-if="canGenerateKey"
          data-test="ds-tls-generate-key-button"
          variant="outlined"
          prepend-icon="autorenew"
          :text="`${translationsPrefix}.generateKey.button`"
          @click="showGenerateKeyDialog = true"
        />
        <XrdBtn
          v-if="canGenerateCsr"
          data-test="ds-tls-generate-csr-button"
          class="ml-4"
          variant="outlined"
          prepend-icon="request_page"
          :text="`${translationsPrefix}.generateCsr.button`"
          @click="showGenerateCsrDialog = true"
        />
        <XrdBtn
          v-if="canUpload"
          data-test="ds-tls-upload-certificate-button"
          class="ml-4"
          variant="outlined"
          prepend-icon="upload"
          :text="`${translationsPrefix}.uploadCertificate.button`"
          @click="showUploadCertificateDialog = true"
        />
        <XrdBtn
          v-if="canDownload"
          data-test="ds-tls-download-certificate-button"
          class="ml-4"
          variant="outlined"
          prepend-icon="download"
          :text="`${translationsPrefix}.downloadCertificate`"
          :loading="loadingDownload"
          @click="download"
        />
        <XrdBtn
          v-if="orderVisible"
          data-test="ds-tls-order-certificate-button"
          class="ml-4"
          variant="outlined"
          prepend-icon="verified"
          :text="`${translationsPrefix}.orderCertificate.button`"
          @click="showOrderDialog = true"
        />
      </template>

      <XrdCard>
        <div class="ds-tls-certificate-card pa-4">
          <div class="d-flex align-center">
            <v-icon icon="shield_lock" size="24" filled />
            <span class="font-weight-medium ml-2">{{ $t(`${translationsPrefix}.keyText`) }}</span>
          </div>

          <div class="mt-2 ml-9">
            <span v-if="!keyGenerated" data-test="ds-tls-key-not-generated" class="on-surface opacity-60">
              {{ $t(`${translationsPrefix}.keyNotGenerated`) }}
            </span>
            <XrdLabelWithIcon
              v-else-if="certificateHash"
              data-test="ds-tls-certificate-hash"
              icon="editor_choice"
              :label="certificateHash"
              :clickable="canViewCertificate"
              @navigate="navigateToCertificateDetails"
            >
              <template #label>
                <XrdHashValue :value="certificateHash" />
              </template>
            </XrdLabelWithIcon>
            <span v-else data-test="ds-tls-certificate-pending" class="on-surface opacity-60">
              {{ $t(`${translationsPrefix}.keyGeneratedPending`) }}
            </span>
          </div>

          <div class="mt-4 ml-9 d-flex flex-wrap align-center ds-tls-status-row">
            <XrdStatusChip :type="methodChip.type" data-test="ds-tls-enrollment-method">
              <template #text>
                <span class="font-weight-medium body-small">{{ $t(methodChip.textKey) }}</span>
              </template>
            </XrdStatusChip>

            <span v-if="renewalState.kind === 'error'" data-test="ds-tls-renewal-error" class="body-small text-error">
              {{ $t(`${translationsPrefix}.renewalStatus.error`) }}&nbsp;{{ renewalState.text }}
              <v-tooltip activator="parent" location="top">{{ renewalState.text }}</v-tooltip>
            </span>
            <span v-else-if="renewalState.kind === 'next'" data-test="ds-tls-renewal-next" class="body-small">
              {{ $t(`${translationsPrefix}.renewalStatus.nextRenewal`) }}&nbsp;{{ formatDate(renewalState.date) }}
              <v-tooltip activator="parent" location="top">{{ formatDateTime(renewalState.date) }}</v-tooltip>
            </span>
            <span v-else data-test="ds-tls-renewal-na" class="body-small on-surface opacity-60">
              {{ $t(`${translationsPrefix}.renewalStatus.na`) }}
            </span>
          </div>
        </div>
      </XrdCard>

      <GenerateKeyDialog
        v-if="showGenerateKeyDialog"
        :handler="handler"
        :translations-prefix="translationsPrefix"
        @accept="closeGenerateKeyDialog"
        @cancel="showGenerateKeyDialog = false"
      />
      <DsTlsGenerateCsrDialog
        v-if="showGenerateCsrDialog"
        :handler="handler"
        :default-distinguished-name="defaultDistinguishedName"
        :default-subject-alt-name="defaultSubjectAltName"
        :translations-prefix="translationsPrefix"
        @generate="showGenerateCsrDialog = false"
        @cancel="showGenerateCsrDialog = false"
      />
      <UploadCertificateDialog
        v-if="showUploadCertificateDialog"
        :handler="handler"
        :translations-prefix="translationsPrefix"
        @upload="closeUploadCertificateDialog"
        @cancel="showUploadCertificateDialog = false"
      />
      <DsTlsOrderCertificateDialog
        v-if="showOrderDialog"
        :handler="handler"
        :acme-cas="acmeCas"
        :default-distinguished-name="defaultDistinguishedName"
        :default-subject-alt-name="defaultSubjectAltName"
        :translations-prefix="translationsPrefix"
        @order="closeOrderDialog"
        @cancel="showOrderDialog = false"
      />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, onMounted, PropType, ref } from 'vue';
import { useRouter } from 'vue-router';

import { XrdSubView, XrdView } from '../../layouts';
import { XrdBtn, XrdCard, XrdHashValue, XrdLabelWithIcon, XrdStatusChip } from '../../components';
import { useNotifications } from '../../composables';
import { formatDate, formatDateTime } from '../../utils';
import { DsTlsAcmeCertificationAuthority, DsTlsCertificateEnrollmentStatus, DsTlsCertificateStatus } from '../../openapi-types';
import { DsTlsCertificateHandler } from '../../types';

import GenerateKeyDialog from '../TlsCertificates/dialogs/GenerateKeyDialog.vue';
import UploadCertificateDialog from '../TlsCertificates/dialogs/UploadCertificateDialog.vue';
import DsTlsGenerateCsrDialog from './dialogs/DsTlsGenerateCsrDialog.vue';
import DsTlsOrderCertificateDialog from './dialogs/DsTlsOrderCertificateDialog.vue';

const props = defineProps({
  canDownload: {
    type: Boolean,
    required: true,
  },
  canUpload: {
    type: Boolean,
    required: true,
  },
  canViewCertificate: {
    type: Boolean,
    required: true,
  },
  canGenerateKey: {
    type: Boolean,
    required: true,
  },
  canGenerateCsr: {
    type: Boolean,
    required: true,
  },
  canOrder: {
    type: Boolean,
    required: true,
  },
  handler: {
    type: Object as PropType<DsTlsCertificateHandler>,
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
  certDetailsViewName: {
    type: String,
    required: true,
  },
  translationsPrefix: {
    type: String,
    default: 'dsTlsCertificates',
  },
});

const router = useRouter();
const { addError } = useNotifications();

const loading = ref(false);
const loadingDownload = ref(false);
const status = ref<DsTlsCertificateStatus | undefined>(undefined);
const enrollmentStatus = ref<DsTlsCertificateEnrollmentStatus | undefined>(undefined);

const showGenerateKeyDialog = ref(false);
const showGenerateCsrDialog = ref(false);
const showUploadCertificateDialog = ref(false);
const showOrderDialog = ref(false);

const keyGenerated = computed(() => status.value?.key_generated === true);
const certificateHash = computed(() => status.value?.certificate?.hash);

const acmeCas = computed<DsTlsAcmeCertificationAuthority[]>(() => enrollmentStatus.value?.acme_cas ?? []);
const publicHostname = computed(() => enrollmentStatus.value?.public_hostname);
const defaultDistinguishedName = computed(() => (publicHostname.value ? `CN=${publicHostname.value}` : ''));
const defaultSubjectAltName = computed(() => publicHostname.value ?? '');

const orderVisible = computed(() => props.canOrder && enrollmentStatus.value?.acme_available === true && keyGenerated.value);

const methodChip = computed(() => {
  switch (enrollmentStatus.value?.enrollment_method) {
    case 'ACME':
      return { type: 'success' as const, textKey: `${props.translationsPrefix}.enrollmentStatus.method.acme` };
    case 'MANUAL':
      return { type: 'info' as const, textKey: `${props.translationsPrefix}.enrollmentStatus.method.manual` };
    default:
      return { type: 'inactive' as const, textKey: `${props.translationsPrefix}.enrollmentStatus.method.none` };
  }
});

type RenewalState = { kind: 'error'; text: string } | { kind: 'next'; date: string } | { kind: 'na' };

const renewalState = computed<RenewalState>(() => {
  if (enrollmentStatus.value?.last_error) {
    return { kind: 'error', text: enrollmentStatus.value.last_error };
  }
  if (enrollmentStatus.value?.next_renewal_time) {
    return { kind: 'next', date: enrollmentStatus.value.next_renewal_time };
  }
  return { kind: 'na' };
});

function fetchData(): void {
  loading.value = true;
  Promise.all([props.handler.fetchStatus(), props.handler.fetchEnrollmentStatus()])
    .then(([currentStatus, currentEnrollmentStatus]) => {
      status.value = currentStatus;
      enrollmentStatus.value = currentEnrollmentStatus;
    })
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
}

onMounted(() => fetchData());

function download() {
  loadingDownload.value = true;
  props.handler
    .downloadCertificate()
    .catch((error) => addError(error))
    .finally(() => (loadingDownload.value = false));
}

function closeGenerateKeyDialog() {
  showGenerateKeyDialog.value = false;
  fetchData();
}

function closeUploadCertificateDialog() {
  showUploadCertificateDialog.value = false;
  fetchData();
}

function closeOrderDialog() {
  showOrderDialog.value = false;
  fetchData();
}

function navigateToCertificateDetails() {
  router.push({ name: props.certDetailsViewName });
}
</script>

<style lang="scss" scoped>
.ds-tls-status-row {
  gap: 8px;
}
</style>
