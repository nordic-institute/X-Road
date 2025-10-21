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
        data-test="delete-trust-service"
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
      v-if="certificationServiceStore.current && showDeleteDialog"
      title="trustServices.trustService.delete.confirmationDialog.title"
      text="trustServices.trustService.delete.confirmationDialog.message"
      data-test="delete-trust-service-confirm-dialog"
      focus-on-accept
      :loading="deleting"
      :data="{
        name: certificationServiceStore.current.name,
      }"
      @cancel="showDeleteDialog = false"
      @accept="confirmDelete"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { Permissions, RouteName } from '@/global';
import { useCertificationService } from '@/store/modules/trust-services';
import {
  XrdView,
  XrdBtn,
  useNotifications,
  XrdViewNavigation,
} from '@niis/shared-ui';
import { computed, ref, watchEffect } from 'vue';
import { useRouter } from 'vue-router';
import { useUser } from '@/store/modules/user';

const props = defineProps({
  certificationServiceId: {
    type: String,
    required: true,
  },
});

const router = useRouter();
const { addError, addSuccessMessage } = useNotifications();
const certificationServiceStore = useCertificationService();
const { hasPermission, getAllowedTabs } = useUser();

const deleting = ref(false);
const showDeleteDialog = ref(false);

const title = computed(() => certificationServiceStore.current?.name || '');
const canDelete = computed(() => hasPermission(Permissions.DELETE_APPROVED_CA));
const tabs = computed(() => [
  {
    key: 'certification-service-details-tab-button',
    name: 'trustServices.trustService.pagenavigation.details',
    icon: 'list_alt',
    to: {
      name: RouteName.CertificationServiceDetails,
      params: { certificationServiceId: props.certificationServiceId },
      replace: true,
    },
    permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
  },

  {
    key: 'certification-service-settings-tab-button',
    name: 'trustServices.trustService.pagenavigation.settings',
    icon: 'assured_workload',
    to: {
      name: RouteName.CertificationServiceSettings,
      params: { certificationServiceId: props.certificationServiceId },
      replace: true,
    },
    permissions: [Permissions.EDIT_APPROVED_CA],
  },

  {
    key: 'certification-service-ocsp-responders-tab-button',
    name: 'trustServices.trustService.pagenavigation.ocspResponders',
    icon: 'database',
    to: {
      name: RouteName.CertificationServiceOcspResponders,
      params: { certificationServiceId: props.certificationServiceId },
      replace: true,
    },
    permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
  },

  {
    key: 'certification-service-intermediate-cas-tab-button',
    name: 'trustServices.trustService.pagenavigation.intermediateCas',
    icon: 'contacts',
    to: {
      name: RouteName.CertificationServiceIntermediateCas,
      params: { certificationServiceId: props.certificationServiceId },
      replace: true,
    },
    permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
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
    name: RouteName.CertificationServiceCertificateDetails,
    params: {
      certificationServiceId: props.certificationServiceId,
    },
  });
}

function confirmDelete() {
  if (!certificationServiceStore.current) return;
  deleting.value = true;
  certificationServiceStore
    .deleteById(certificationServiceStore.current.id)
    .then(() => {
      addSuccessMessage('trustServices.trustService.delete.success', {}, true);
      router.replace({ name: RouteName.TrustServices });
    })
    .catch((error) => addError(error))
    .finally(() => {
      showDeleteDialog.value = false;
      deleting.value = false;
    });
}

watchEffect(() => {
  const certId = Number(props.certificationServiceId);
  certificationServiceStore
    .loadById(certId)
    .catch((err) => addError(err, { navigate: true }));
});
</script>
