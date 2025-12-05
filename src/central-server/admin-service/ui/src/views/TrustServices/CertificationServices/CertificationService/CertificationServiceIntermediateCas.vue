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
  <XrdSubView id="intermediate-cas" class="mt-8">
    <template #header>
      <v-spacer />
      <XrdBtn
        data-test="add-intermediate-ca-button"
        prepend-icon="add_circle"
        text="action.add"
        @click="showAddIntermediateCaDialog = true"
      />
    </template>
    <!-- Table -->
    <v-data-table
      data-test="intermediate-cas-table"
      item-key="id"
      class="xrd border"
      hide-default-footer
      must-sort
      :loading="loading"
      :headers="headers"
      :header-props="{ showHeaderBorder: false }"
      :items="intermediateCas"
      :items-per-page="-1"
    >
      <template #[`item.ca_certificate.subject_common_name`]="{ item }">
        <XrdLabelWithIcon
          icon="assured_workload"
          semi-bold
          :label="item.ca_certificate.subject_common_name"
          :clickable="hasPermissionToDetails"
          @navigate="toDetails(item)"
        />
      </template>

      <template #[`item.ca_certificate.not_before`]="{ item }">
        <XrdDateTime :value="item.ca_certificate.not_before" />
      </template>

      <template #[`item.ca_certificate.not_after`]="{ item }">
        <XrdDateTime :value="item.ca_certificate.not_after" />
      </template>

      <template #[`item.button`]="{ item }">
        <XrdBtn
          data-test="delete-intermediate-ca"
          text="action.delete"
          variant="text"
          color="tertiary"
          @click="openDeleteConfirmationDialog(item)"
        />
        <XrdBtn
          data-test="view-intermediate-ca-certificate"
          text="trustServices.viewCertificate"
          variant="text"
          color="tertiary"
          @click="navigateToCertificateDetails(item)"
        />
      </template>
    </v-data-table>

    <!-- Add Intermediate CA dialog -->
    <AddIntermediateCaDialog
      v-if="intermediateCasServiceStore.currentCs && showAddIntermediateCaDialog"
      @cancel="hideAddIntermediateCaDialog"
      @save="hideAddIntermediateCaDialog"
    />

    <!-- Confirm delete dialog -->
    <XrdConfirmDialog
      v-if="confirmDelete"
      :dialog="confirmDelete"
      title="trustServices.trustService.intermediateCas.delete.confirmationDialog.title"
      text="trustServices.trustService.intermediateCas.delete.confirmationDialog.message"
      :data="{
        name: selectedIntermediateCa?.ca_certificate.subject_common_name,
      }"
      focus-on-accept
      :loading="deletingIntermediateCa"
      @cancel="confirmDelete = false"
      @accept="deleteIntermediateCa"
    />
  </XrdSubView>
</template>

<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue';

import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { useNotifications, XrdBtn, XrdDateTime, XrdLabelWithIcon, XrdSubView, XrdConfirmDialog } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { CertificateAuthority } from '@/openapi-types';
import { useCertificationService, useIntermediateCasService } from '@/store/modules/trust-services';
import { useUser } from '@/store/modules/user';

import AddIntermediateCaDialog from './IntermediateCa/AddIntermediateCaDialog.vue';

const loading = ref(false);
const showAddIntermediateCaDialog = ref(false);
const confirmDelete = ref(false);
const deletingIntermediateCa = ref(false);
const selectedIntermediateCa = ref(undefined as undefined | CertificateAuthority);

const router = useRouter();
const { t } = useI18n();

const { hasPermission } = useUser();
const { addError, addSuccessMessage } = useNotifications();
const intermediateCasServiceStore = useIntermediateCasService();
const certificationServiceStore = useCertificationService();

const intermediateCas = computed(() => intermediateCasServiceStore.currentIntermediateCas);
const hasPermissionToDetails = computed(() => hasPermission(Permissions.VIEW_APPROVED_CA_DETAILS));
const headers = computed(
  () =>
    [
      {
        title: t('trustServices.trustService.intermediateCas.intermediateCa') as string,
        align: 'start',
        key: 'ca_certificate.subject_common_name',
      },
      {
        title: t('trustServices.validFrom') as string,
        align: 'start',
        key: 'ca_certificate.not_before',
      },
      {
        title: t('trustServices.validTo') as string,
        align: 'start',
        key: 'ca_certificate.not_after',
      },
      {
        title: '',
        key: 'button',
        align: 'end',
        sortable: false,
      },
    ] as DataTableHeader[],
);

function toDetails(intermediateCa: CertificateAuthority) {
  router.push({
    name: RouteName.IntermediateCaDetails,
    params: {
      intermediateCaId: String(intermediateCa.id),
    },
  });
}

function hideAddIntermediateCaDialog() {
  showAddIntermediateCaDialog.value = false;
}

function openDeleteConfirmationDialog(intermediateCa: CertificateAuthority): void {
  selectedIntermediateCa.value = intermediateCa;
  confirmDelete.value = true;
}

function fetchIntermediateCas(): void {
  intermediateCasServiceStore.fetchIntermediateCas();
}

function deleteIntermediateCa(): void {
  if (!selectedIntermediateCa.value) return;

  deletingIntermediateCa.value = true;
  intermediateCasServiceStore
    .deleteIntermediateCa(selectedIntermediateCa.value.id as number)
    .then(() => {
      addSuccessMessage('trustServices.trustService.intermediateCas.delete.success');
      confirmDelete.value = false;
      deletingIntermediateCa.value = false;
      fetchIntermediateCas();
    })
    .catch((error) => addError(error));
}

function navigateToCertificateDetails(intermediateCa: CertificateAuthority) {
  router.push({
    name: RouteName.IntermediateCACertificateDetails,
    params: {
      intermediateCaId: String(intermediateCa.id),
    },
  });
}

watchEffect(() => {
  if (certificationServiceStore.current) {
    intermediateCasServiceStore.loadByCs(certificationServiceStore.current);
  }
});
</script>
