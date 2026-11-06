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
  <XrdSimpleDialog
    title="securityServers.dialogs.editAddress.title"
    data-test="security-server-address-edit-dialog"
    save-button-text="action.save"
    submittable
    :scrollable="false"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="save"
    @cancel="close"
  >
    <template #content="{ dialogHandler }">
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="securityServerAddress"
            v-bind="securityServerAddressAttrs"
            data-test="security-server-address-edit-field"
            name="securityServerAddress"
            class="xrd"
            autofocus
            :label="$t('securityServers.dialogs.editAddress.addressField')"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
      <template v-if="hasSystemPair">
        <div class="body-regular font-weight-bold mt-4" data-test="security-server-published-values-title">
          {{ $t('securityServers.dialogs.editAddress.publishedValues') }}
        </div>
        <div class="body-small mb-4">{{ $t('securityServers.dialogs.editAddress.publishedValuesHint') }}</div>
        <XrdFormBlock>
          <XrdFormBlockRow full-length>
            <v-text-field
              v-model="systemDidInput"
              v-bind="systemDidInputAttrs"
              data-test="security-server-system-did-edit-field"
              name="systemDid"
              class="xrd"
              :label="$t('securityServers.dialogs.editAddress.systemDidField')"
            />
          </XrdFormBlockRow>
          <XrdFormBlockRow full-length>
            <div class="d-flex align-center ga-4">
              <XrdBtn
                data-test="security-server-system-did-check-button"
                text="securityServers.dialogs.editAddress.checkDid"
                variant="outlined"
                :disabled="!systemDidInput"
                :loading="checking"
                @click="check(dialogHandler)"
              />
              <div v-if="checkOutcome" class="d-flex align-center" data-test="security-server-system-did-check-result">
                <v-icon class="mr-2" :icon="checkOutcome.icon" :color="checkOutcome.color" filled />
                <span class="body-regular">{{ $t(checkOutcome.text) }}</span>
              </div>
            </div>
          </XrdFormBlockRow>
          <XrdFormBlockRow full-length>
            <v-text-field
              v-model="dspBaseUrlInput"
              v-bind="dspBaseUrlInputAttrs"
              data-test="security-server-dsp-base-url-edit-field"
              name="dspBaseUrl"
              class="xrd"
              :label="$t('securityServers.dialogs.editAddress.dspBaseUrlField')"
            />
          </XrdFormBlockRow>
        </XrdFormBlock>
      </template>
      <v-alert
        v-if="warningText"
        data-test="security-server-edit-warning"
        class="mt-4"
        type="warning"
        variant="outlined"
        border="start"
        density="compact"
        :text="$t(warningText)"
      />
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useForm } from 'vee-validate';
import {
  XrdBtn,
  XrdSimpleDialog,
  useBasicForm,
  XrdFormBlock,
  XrdFormBlockRow,
  getErrorInfo,
  getTranslatedFieldErrors,
} from '@niis/shared-ui';
import type { DialogSaveHandler } from '@niis/shared-ui';
import type { AxiosError } from 'axios';

import { SystemDidCheck, SystemDidCheckStatus, SystemDidRefusalReason, SystemDidResolutionReason } from '@/openapi-types';
import { useSecurityServer } from '@/store/modules/security-servers';

const props = defineProps({
  securityServerId: {
    type: String,
    required: true,
  },
  address: {
    type: String,
    required: true,
  },
  systemDid: {
    type: String,
    default: undefined,
  },
  dspBaseUrl: {
    type: String,
    default: undefined,
  },
  systemPairStale: {
    type: Boolean,
    default: false,
  },
});

const emits = defineEmits(['save', 'cancel']);

const REFUSAL_TEXT: Record<SystemDidRefusalReason, string> = {
  WRONG_SCHEME: 'error_code.system_pair_wrong_scheme',
  DID_TOO_LONG: 'error_code.system_pair_did_too_long',
  NOT_CANONICAL: 'error_code.system_pair_not_canonical',
  HOST_DIFFERS: 'error_code.system_pair_host_differs',
  UNPARSEABLE_REGISTERED_ADDRESS: 'error_code.system_pair_unparseable_address',
  NON_ASCII_HOST: 'error_code.system_pair_non_ascii_host',
  FOREIGN_SYSTEM_DID: 'error_code.system_pair_foreign_did',
  DID_HELD_BY_ANOTHER_SERVER: 'error_code.system_pair_did_held_by_another_server',
  NO_STORED_VALUE: 'error_code.system_pair_not_stored',
};

const RESOLUTION_TEXT: Record<SystemDidResolutionReason, string> = {
  MALFORMED_DID: 'securityServers.dialogs.editAddress.check.not_resolved_malformed_did',
  NO_DOCUMENT: 'securityServers.dialogs.editAddress.check.not_resolved_no_document',
  ID_MISMATCH: 'securityServers.dialogs.editAddress.check.not_resolved_id_mismatch',
  FETCH_FAILURE: 'securityServers.dialogs.editAddress.check.not_resolved_fetch_failure',
};

type CheckOutcome = { icon: string; color: 'success' | 'warning' | 'error'; text: string };

const hasSystemPair = !!props.systemDid && !!props.dspBaseUrl;

const { meta, resetForm, setFieldError, defineField, handleSubmit } = useForm({
  validationSchema: {
    securityServerAddress: 'required|address',
    ...(hasSystemPair ? { systemDid: 'required', dspBaseUrl: 'required' } : {}),
  },
  initialValues: {
    securityServerAddress: props.address,
    ...(hasSystemPair ? { systemDid: props.systemDid, dspBaseUrl: props.dspBaseUrl } : {}),
  },
});
const fieldConfig = { props: (state: { errors: string[] }) => ({ 'error-messages': state.errors }) };
const [securityServerAddress, securityServerAddressAttrs] = defineField('securityServerAddress', fieldConfig);
const [systemDidInput, systemDidInputAttrs] = defineField('systemDid', fieldConfig);
const [dspBaseUrlInput, dspBaseUrlInputAttrs] = defineField('dspBaseUrl', fieldConfig);

const { t } = useI18n();
const { updateAddress, checkSystemDid } = useSecurityServer();
const { addSuccessMessage, addError, loading } = useBasicForm();

const checking = ref(false);
const checkResult = ref<SystemDidCheck | undefined>(undefined);
let latestCheck = 0;

watch(
  [securityServerAddress, systemDidInput, dspBaseUrlInput],
  () => {
    latestCheck += 1;
    checkResult.value = undefined;
    checking.value = false;
  },
  { flush: 'sync' },
);

const addressChanged = computed(() => securityServerAddress.value !== props.address);
const systemPairChanged = computed(
  () => hasSystemPair && (systemDidInput.value !== props.systemDid || dspBaseUrlInput.value !== props.dspBaseUrl),
);

const warningText = computed(() => {
  if (!hasSystemPair || systemPairChanged.value) {
    return undefined;
  }
  if (addressChanged.value) {
    return 'securityServers.dialogs.editAddress.oldHostWarning';
  }
  return props.systemPairStale ? 'securityServers.staleValuesWarning' : undefined;
});

const checkOutcome = computed<CheckOutcome | undefined>(() => {
  const result = checkResult.value;
  if (!result) {
    return undefined;
  }
  switch (result.status) {
    case SystemDidCheckStatus.RESOLVED:
      return { icon: 'check_circle', color: 'success', text: 'securityServers.dialogs.editAddress.check.resolved' };
    case SystemDidCheckStatus.NOT_RESOLVED:
      return {
        icon: 'warning',
        color: 'warning',
        text:
          RESOLUTION_TEXT[result.resolution_reason as SystemDidResolutionReason] ??
          'securityServers.dialogs.editAddress.check.not_resolved_fetch_failure',
      };
    case SystemDidCheckStatus.RESOLVER_UNAVAILABLE:
      return { icon: 'warning', color: 'warning', text: 'securityServers.dialogs.editAddress.check.resolver_unavailable' };
    default:
      return {
        icon: 'error',
        color: 'error',
        text: REFUSAL_TEXT[result.refusal_reason as SystemDidRefusalReason] ?? 'error_code.internal_error',
      };
  }
});

function close() {
  resetForm();
  emits('cancel');
}

function check(handler: DialogSaveHandler) {
  latestCheck += 1;
  const thisCheck = latestCheck;
  const isLatest = () => thisCheck === latestCheck;
  checking.value = true;
  checkSystemDid(props.securityServerId, securityServerAddress.value as string, systemDidInput.value as string)
    .then((result) => isLatest() && (checkResult.value = result))
    .catch((error) => isLatest() && handler.addError(error))
    .finally(() => isLatest() && (checking.value = false));
}

function showSaveError(error: AxiosError, handler: DialogSaveHandler) {
  const errorInfo = getErrorInfo(error);
  const fieldErrors = errorInfo.error?.validation_errors;
  if (errorInfo.status === 400 && errorInfo.error?.code === 'validation_failure' && fieldErrors) {
    setFieldError('securityServerAddress', getTranslatedFieldErrors('securityServerEditDto.serverAddress', fieldErrors));
  } else {
    handler.addError(error);
  }
}

function save(evt: Event, handler: DialogSaveHandler) {
  return handleSubmit(() => {
    loading.value = true;
    const systemPair = systemPairChanged.value
      ? { system_did: systemDidInput.value as string, dsp_base_url: dspBaseUrlInput.value as string }
      : undefined;
    return updateAddress(props.securityServerId, securityServerAddress.value as string, systemPair)
      .then(() => {
        addSuccessMessage('securityServers.dialogs.editAddress.success');
        if (hasSystemPair) {
          addError(t('securityServers.dialogs.editAddress.notify'), { warning: true });
        }
        emits('save');
      })
      .catch((error) => showSaveError(error, handler))
      .finally(() => (loading.value = false));
  })(evt);
}
</script>
