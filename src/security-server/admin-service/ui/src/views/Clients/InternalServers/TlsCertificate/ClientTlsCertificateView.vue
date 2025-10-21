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
    go-back-on-close
    fixed-height
    :loading="loading"
    :breadcrumbs="breadcrumbs"
  >
    <XrdCertificate
      v-if="certificate"
      data-test="certificate-details-dialog"
      :certificate="certificate"
    />

    <!-- Confirm dialog for delete -->
    <XrdConfirmDialog
      v-if="confirm"
      title="cert.deleteCertTitle"
      text="cert.deleteCertConfirm"
      :loading="deleting"
      @cancel="confirm = false"
      @accept="doDeleteCertificate()"
    />

    <template v-if="showDeleteButton" #footer>
      <v-spacer />
      <XrdBtn
        data-test="tls-certificate-delete-button"
        variant="outlined"
        text="action.delete"
        prepend-icon="delete_forever"
        @click="deleteCertificate()"
      />
    </template>
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts" setup>
import { ref, computed, watchEffect } from 'vue';

import { Permissions, RouteName } from '@/global';
import { CertificateDetails } from '@/openapi-types';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import { XrdCertificate, XrdElevatedViewFixedWidth, XrdBtn, useNotifications } from '@niis/shared-ui';
import { useRouter } from 'vue-router';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { useI18n } from 'vue-i18n';
import { clientTitle } from '@/util/ClientUtil';

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
  hash: {
    type: String,
    required: true,
  },
});

const router = useRouter();
const { t } = useI18n();
const { addError, addSuccessMessage } = useNotifications();
const { hasPermission } = useUser();
const clientStore = useClient();
const { fetchTlsCertificate, deleteTlsCertificate, fetchClient } = clientStore;

const confirm = ref(false);
const loading = ref(false);
const deleting = ref(false);
const certificate = ref<CertificateDetails | null>(null);

const showDeleteButton = computed(() =>
  hasPermission(Permissions.DELETE_CLIENT_INTERNAL_CERT),
);

const breadcrumbs = computed(() => {
  const crumbs: BreadcrumbItem[] = [
    {
      title: t('tab.main.clients'),
      to: { name: RouteName.Clients },
    },
  ];
  if (clientStore.client && certificate.value) {
    crumbs.push(
      {
        title: clientTitle(clientStore.client, loading.value),
        to: { name: RouteName.MemberDetails, params: { id: props.id } },
      },
      {
        title: t('internalServers.tlsTitle'),
        to: { name: RouteName.MemberServers, params: { id: props.id } },
      },
      {
        title: certificate.value.issuer_common_name,
      },
    );
  }
  return crumbs;
});

function close() {
  router.back();
}

async function fetchData(id: string, hash: string) {
  loading.value = true;
  return fetchClient(id)
    .then(() => fetchTlsCertificate(id, hash))
    .then((cert) => (certificate.value = cert))
    .catch((error) => addError(error, true))
    .finally(() => (loading.value = false));
}

function deleteCertificate() {
  confirm.value = true;
}

function doDeleteCertificate() {
  deleting.value = true;

  deleteTlsCertificate(props.id, props.hash)
    .then(() => {
      addSuccessMessage('cert.certDeleted', {}, true);
      close();
    })
    .catch((error) => addError(error))
    .finally(() => (deleting.value = false))
    .finally(() => (confirm.value = false));
}

watchEffect(() => fetchData(props.id, props.hash));
</script>

<style lang="scss" scoped></style>
