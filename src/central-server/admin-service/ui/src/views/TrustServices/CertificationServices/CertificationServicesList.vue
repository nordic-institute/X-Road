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
  <XrdCard data-test="certification-services" title="trustServices.certificationServices">
    <template #title-actions>
      <XrdBtn
        v-if="showAddCSButton"
        data-test="add-certification-service"
        class="mr-4"
        text="action.add"
        prepend-icon="add_circle"
        variant="outlined"
        @click="showAddCSDialog = true"
      />
    </template>
    <v-data-table
      item-key="id"
      class="xrd"
      hide-default-footer
      must-sort
      :loading="loading"
      :headers="headers"
      :items="certificationServiceStore.certificationServices"
      :search="search"
      :items-per-page="-1"
    >
      <template #[`item.name`]="{ item }">
        <XrdLabelWithIcon icon="shield_lock" semi-bold :label="item.name" :clickable="hasPermissionToDetails" @navigate="toDetails(item)" />
      </template>
      <template #[`item.not_before`]="{ item }">
        <div>
          <XrdDateTime :value="item.not_before" />
        </div>
      </template>
      <template #[`item.not_after`]="{ item }">
        <div>
          <XrdDateTime :value="item.not_after" />
        </div>
      </template>
    </v-data-table>
    <!-- Dialogs -->
    <AddCertificationServiceDialog v-if="showAddCSDialog" @save="hideAddCSDialog" @cancel="hideAddCSDialog" />
  </XrdCard>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { XrdBtn, XrdCard, XrdDateTime, XrdLabelWithIcon } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { ApprovedCertificationServiceListItem } from '@/openapi-types';
import { useCertificationService } from '@/store/modules/trust-services';
import { useUser } from '@/store/modules/user';

import AddCertificationServiceDialog from './dialogs/AddCertificationServiceDialog.vue';

const router = useRouter();
const { t } = useI18n();
const { hasPermission } = useUser();
const certificationServiceStore = useCertificationService();

const showAddCSDialog = ref(false);
const loading = ref(false);
const search = ref('');

const showAddCSButton = computed(() => hasPermission(Permissions.ADD_APPROVED_CA));
const hasPermissionToDetails = computed(() => hasPermission(Permissions.VIEW_APPROVED_CA_DETAILS));
const headers = computed(
  () =>
    [
      {
        title: t('trustServices.approvedCertificationService'),
        align: 'start',
        key: 'name',
      },
      {
        title: t('trustServices.validFrom'),
        align: 'start',
        key: 'not_before',
      },
      {
        title: t('trustServices.validTo'),
        align: 'start',
        key: 'not_after',
      },
    ] as DataTableHeader[],
);

function fetchCertificationServices(): void {
  loading.value = true;
  certificationServiceStore.fetchAll().finally(() => (loading.value = false));
}

function toDetails(certificationService: ApprovedCertificationServiceListItem): void {
  router.push({
    name: RouteName.CertificationServiceDetails,
    params: { certificationServiceId: String(certificationService.id) },
  });
}

function hideAddCSDialog(): void {
  showAddCSDialog.value = false;
}

fetchCertificationServices();
</script>

<style lang="scss" scoped></style>
