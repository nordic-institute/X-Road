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
  <XrdView translated-title :title="title" :breadcrumbs="breadcrumbs">
    <template #append-header>
      <XrdBtn
        data-test="view-certificate-button"
        class="ml-auto"
        prepend-icon="editor_choice"
        variant="outlined"
        text="trustServices.viewCertificate"
        @click="navigateToCertificateDetails()"
      />
      <XrdBtn
        v-if="canDelete"
        data-test="delete-ds-tls-certification-authority"
        class="ml-4"
        prepend-icon="delete_forever"
        variant="flat"
        text="action.delete"
        @click="showDeleteDialog = true"
      />
    </template>
    <template #tabs>
      <XrdViewNavigation :allowed-tabs="allowedTabs" />
    </template>
    <router-view />

    <XrdConfirmDialog
      v-if="dsTlsCertificationAuthorityStore.current && showDeleteDialog"
      title="trustServices.dsTls.delete.confirmationDialog.title"
      text="trustServices.dsTls.delete.confirmationDialog.message"
      data-test="delete-ds-tls-certification-authority-confirm-dialog"
      focus-on-accept
      :loading="deleting"
      :data="{
        name: dsTlsCertificationAuthorityStore.current.name,
      }"
      @cancel="showDeleteDialog = false"
      @accept="confirmDelete"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { Permissions, RouteName } from '@/global';
import { useDsTlsCertificationAuthorityService } from '@/store/modules/trust-services';
import { XrdView, XrdBtn, useNotifications, XrdViewNavigation, XrdConfirmDialog } from '@niis/shared-ui';
import { computed, ref, watchEffect } from 'vue';
import { useRouter } from 'vue-router';
import { useUser } from '@/store/modules/user';

const props = defineProps({
  dsTlsCertificationAuthorityId: {
    type: String,
    required: true,
  },
});

const router = useRouter();
const { addError, addSuccessMessage } = useNotifications();
const dsTlsCertificationAuthorityStore = useDsTlsCertificationAuthorityService();
const { hasPermission, getAllowedTabs } = useUser();

const deleting = ref(false);
const showDeleteDialog = ref(false);

const title = computed(() => dsTlsCertificationAuthorityStore.current?.name || '');
const canDelete = computed(() => hasPermission(Permissions.DELETE_APPROVED_DS_TLS_CA));
const tabs = computed(() => [
  {
    key: 'ds-tls-certification-authority-details-tab-button',
    name: 'trustServices.trustService.pagenavigation.details',
    icon: 'list_alt',
    to: {
      name: RouteName.DsTlsCertificationAuthorityDetails,
      params: { dsTlsCertificationAuthorityId: props.dsTlsCertificationAuthorityId },
      replace: true,
    },
    permissions: [Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS],
  },
  {
    key: 'ds-tls-certification-authority-settings-tab-button',
    name: 'trustServices.trustService.pagenavigation.settings',
    icon: 'assured_workload',
    to: {
      name: RouteName.DsTlsCertificationAuthoritySettings,
      params: { dsTlsCertificationAuthorityId: props.dsTlsCertificationAuthorityId },
      replace: true,
    },
    permissions: [Permissions.EDIT_APPROVED_DS_TLS_CA],
  },
  {
    key: 'ds-tls-certification-authority-intermediate-cas-tab-button',
    name: 'trustServices.trustService.pagenavigation.intermediateCas',
    icon: 'contacts',
    to: {
      name: RouteName.DsTlsCertificationAuthorityIntermediateCas,
      params: { dsTlsCertificationAuthorityId: props.dsTlsCertificationAuthorityId },
      replace: true,
    },
    permissions: [Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS],
  },
]);

const allowedTabs = computed(() => getAllowedTabs(tabs.value));

const breadcrumbs = computed(() => [
  {
    title: 'tab.main.trustServices',
    to: {
      name: RouteName.TrustServices,
    },
  },
]);

function navigateToCertificateDetails() {
  router.push({
    name: RouteName.DsTlsCertificationAuthorityCertificateDetails,
    params: {
      dsTlsCertificationAuthorityId: props.dsTlsCertificationAuthorityId,
    },
  });
}

function confirmDelete() {
  if (!dsTlsCertificationAuthorityStore.current) return;
  deleting.value = true;
  dsTlsCertificationAuthorityStore
    .deleteById(dsTlsCertificationAuthorityStore.current.id)
    .then(() => {
      addSuccessMessage('trustServices.dsTls.delete.success', {}, true);
      router.replace({ name: RouteName.TrustServices });
    })
    .catch((error) => addError(error))
    .finally(() => {
      showDeleteDialog.value = false;
      deleting.value = false;
    });
}

watchEffect(() => {
  const dsTlsCertificationAuthorityId = Number(props.dsTlsCertificationAuthorityId);
  dsTlsCertificationAuthorityStore.loadById(dsTlsCertificationAuthorityId).catch((err) => addError(err, { navigate: true }));
});
</script>
