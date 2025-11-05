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
  <XrdElevatedViewFixedWidth
    title="cert.certificate"
    closeable
    :loading="loading"
    :breadcrumbs="breadcrumbs"
    @close="close"
  >
    <XrdCertificate
      v-if="certificate"
      :certificate="certificate.certificate_details"
    >
    </XrdCertificate>
    <template v-if="certificate" #append-header>
      <CertificateStatusChip class="ml-4" :active="certificate.active" />
    </template>
    <template v-if="certificate" #footer>
      <v-spacer />
      <XrdBtn
        v-if="showDelete"
        data-test="delete-button"
        class="ml-2"
        variant="outlined"
        text="action.delete"
        prepend-icon="delete_forever"
        :loading="deleting"
        @click="showConfirmDelete"
      />
      <XrdBtn
        v-if="showDisable"
        data-test="deactivate-button"
        class="ml-2"
        variant="outlined"
        text="action.deactivate"
        prepend-icon="cancel"
        :loading="disabling"
        @click="deactivateCertificate"
      />
      <XrdBtn
        v-if="showUnregister"
        data-test="unregister-button"
        class="ml-2"
        text="action.unregister"
        variant="outlined"
        @click="confirmUnregisterCertificate = true"
      />
      <XrdBtn
        v-if="showActivate"
        data-test="activate-button"
        class="ml-2"
        text="action.activate"
        prepend-icon="check_circle"
        :loading="activating"
        @click="activateCertificate"
      />

      <!-- Confirm dialog for delete -->
      <XrdConfirmDialog
        v-if="confirm"
        title="cert.deleteCertTitle"
        text="cert.deleteCertConfirm"
        :loading="deleting"
        @cancel="confirm = false"
        @accept="deleteCertificate"
      />

      <!-- Confirm dialog for unregister certificate -->
      <XrdConfirmDialog
        v-if="confirmUnregisterCertificate"
        :loading="unregistering"
        title="keys.unregisterTitle"
        text="keys.unregisterText"
        @cancel="confirmUnregisterCertificate = false"
        @accept="unregisterCert()"
      />

      <!-- Confirm dialog for unregister error handling -->
      <UnregisterErrorDialog
        v-if="unregisterErrorResponse"
        :error-response="unregisterErrorResponse"
        :dialog="confirmUnregisterError"
        :loading="marking"
        @cancel="confirmUnregisterError = false"
        @accept="markForDeletion()"
      />
    </template>
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts" setup>
import { ref, computed, watchEffect } from 'vue';
import { Permissions, RouteName } from '@/global';
import {
  KeyUsageType,
  PossibleAction,
  TokenCertificate,
} from '@/openapi-types';
import UnregisterErrorDialog from './UnregisterErrorDialog.vue';
import { PossibleActions } from '@/openapi-types/models/PossibleActions';
import { useUser } from '@/store/modules/user';
import {
  XrdElevatedViewFixedWidth,
  XrdCertificate,
  XrdBtn,
  useNotifications,
  useHistory,
} from '@niis/shared-ui';
import { useRouter } from 'vue-router';
import { useTokenCertificates } from '@/store/modules/token-certificates';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { useI18n } from 'vue-i18n';
import { useClient } from '@/store/modules/client';
import { clientTitle } from '@/util/ClientUtil';
import CertificateStatusChip from '@/components/certificate/CertificateStatusChip.vue';

const props = defineProps({
  hash: {
    type: String,
    required: true,
  },
  usage: {
    type: String,
    default: undefined,
  },
});

const emit = defineEmits(['refresh-list']);

const loading = ref(false);
const activating = ref(false);
const deleting = ref(false);
const disabling = ref(false);
const marking = ref(false);
const confirm = ref(false);
const certificate = ref<TokenCertificate | undefined>(undefined);
const possibleActions = ref<PossibleActions>([]);
const confirmUnregisterCertificate = ref(false);
const confirmUnregisterError = ref(false);
const unregistering = ref(false);
const unregisterErrorResponse = ref<Record<string, unknown> | undefined>(
  undefined,
);

const { t } = useI18n();
const router = useRouter();
const { previousPage } = useHistory();
const { addError, addSuccessMessage } = useNotifications();
const { hasPermission } = useUser();
const clientStore = useClient();
const {
  fetchTokenCertificate,
  fetchTokenCertificatePossibleActions,
  deleteTokenCertificate,
  activateTokenCertificate,
  deactivateTokenCertificate,
  unregisterTokenCertificate,
  markForDeletionTokenCertificate,
} = useTokenCertificates();

const showDelete = computed(() => {
  if (possibleActions.value.includes(PossibleAction.DELETE)) {
    if (props.usage === KeyUsageType.SIGNING) {
      return hasPermission(Permissions.DELETE_SIGN_CERT);
    } else if (props.usage === KeyUsageType.AUTHENTICATION) {
      return hasPermission(Permissions.DELETE_AUTH_CERT);
    } else {
      return hasPermission(Permissions.DELETE_UNKNOWN_CERT);
    }
  } else {
    return false;
  }
});

const showUnregister = computed(() => {
  return !!(
    possibleActions.value.includes(PossibleAction.UNREGISTER) &&
    hasPermission(Permissions.SEND_AUTH_CERT_DEL_REQ)
  );
});

const showActivate = computed(() => {
  if (!certificate.value) {
    return false;
  }

  if (possibleActions.value.includes(PossibleAction.ACTIVATE)) {
    if (props.usage === KeyUsageType.SIGNING) {
      return hasPermission(Permissions.ACTIVATE_DISABLE_SIGN_CERT);
    } else {
      return hasPermission(Permissions.ACTIVATE_DISABLE_AUTH_CERT);
    }
  }

  return false;
});

const clientId = computed(() => history.state?.clientId);

const showDisable = computed(() => {
  if (!certificate.value) {
    return false;
  }

  if (possibleActions.value.includes(PossibleAction.DISABLE)) {
    if (props.usage === KeyUsageType.SIGNING) {
      return hasPermission(Permissions.ACTIVATE_DISABLE_SIGN_CERT);
    } else {
      return hasPermission(Permissions.ACTIVATE_DISABLE_AUTH_CERT);
    }
  }

  return false;
});

const fromSignAndAuthKeys = computed(
  () => previousPage?.location.name === RouteName.SignAndAuthKeys,
);

const breadcrumbs = computed(() => {
  const crumbs: BreadcrumbItem[] = [];

  if (certificate.value) {
    if (clientId.value && clientStore.client) {
      const routeName = history.state.subsystem
        ? RouteName.SubsystemDetails
        : RouteName.MemberDetails;
      crumbs.push(
        {
          title: t('tab.main.clients'),
          to: { name: RouteName.Clients },
        },
        {
          title: clientTitle(clientStore.client, clientStore.clientLoading),
          to: {
            name: routeName,
            params: { id: clientId.value },
          },
        },
        {
          title: t('cert.signCertificate'),
          to: {
            name: routeName,
            params: { id: clientId.value },
          },
        },
      );
    } else if (fromSignAndAuthKeys.value) {
      crumbs.push({
        title: t('tab.keys.signAndAuthKeys'),
        to: {
          name: RouteName.SignAndAuthKeys,
        },
      });
    }
    crumbs.push({
      title: certificate.value.certificate_details.issuer_common_name,
    });
  }
  return crumbs;
});

function close(): void {
  if (!previousPage?.location.name) {
    router.push({
      name: RouteName.SignAndAuthKeys,
    });
  } else {
    router.back();
  }
}

async function fetchData(hash: string) {
  // Fetch certificate data
  loading.value = true;
  return fetchTokenCertificate(hash)
    .then((cert) => (certificate.value = cert))
    .then(() => (clientId.value ? clientStore.fetchClient(clientId.value) : {}))
    .then(() => fetchTokenCertificatePossibleActions(hash))
    .then((data) => (possibleActions.value = data))
    .catch((error) => addError(error, { navigate: true }))
    .finally(() => (loading.value = false));
}

function showConfirmDelete() {
  confirm.value = true;
}

async function deleteCertificate() {
  deleting.value = true;

  return deleteTokenCertificate(props.hash)
    .then(() => {
      addSuccessMessage('cert.certDeleted', {}, true);
      deleting.value = false;
      confirm.value = false;
      close();
    })
    .catch((error) => addError(error))
    .finally(() => (deleting.value = false));
}

async function activateCertificate() {
  activating.value = true;
  return activateTokenCertificate(props.hash)
    .then(() => {
      addSuccessMessage('cert.activateSuccess');
      return fetchData(props.hash);
    })
    .catch((error) => addError(error))
    .finally(() => (activating.value = false));
}

async function deactivateCertificate() {
  disabling.value = true;
  return deactivateTokenCertificate(props.hash)
    .then(() => {
      addSuccessMessage('cert.disableSuccess');
      return fetchData(props.hash);
    })
    .catch((error) => addError(error))
    .finally(() => (disabling.value = false));
}

async function unregisterCert() {
  if (!certificate.value) {
    return;
  }

  unregistering.value = true;
  return unregisterTokenCertificate(props.hash)
    .then(() => {
      addSuccessMessage('keys.certificateUnregistered');
      return fetchData(props.hash);
    })
    .catch((error) => {
      if (
        error?.response?.data?.error?.code ===
        'management_request_sending_failed'
      ) {
        unregisterErrorResponse.value = error.response;
      } else {
        addError(error);
      }

      confirmUnregisterError.value = true;
    })
    .finally(() => {
      confirmUnregisterCertificate.value = false;
      unregistering.value = false;
    });
}

async function markForDeletion() {
  if (!certificate.value) {
    return;
  }

  marking.value = true;
  markForDeletionTokenCertificate(props.hash)
    .then(() => {
      addSuccessMessage('keys.certMarkedForDeletion');
      confirmUnregisterError.value = false;
      emit('refresh-list');
    })
    .catch((error) => {
      addError(error);
      confirmUnregisterError.value = false;
    })
    .finally(() => (marking.value = false));
}

watchEffect(() => {
  fetchData(props.hash);
});
</script>

<style lang="scss" scoped></style>
