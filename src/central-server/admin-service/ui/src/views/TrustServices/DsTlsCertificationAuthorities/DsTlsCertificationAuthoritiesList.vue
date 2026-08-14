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
  <XrdCard data-test="ds-tls-certification-authorities" title="trustServices.dsTls.dsTlsCertificationAuthorities">
    <template #title-actions>
      <XrdBtn
        v-if="showAddButton"
        data-test="add-ds-tls-certification-authority"
        class="mr-4"
        text="action.add"
        prepend-icon="add_circle"
        variant="outlined"
        @click="showAddDialog = true"
      />
    </template>
    <v-data-table
      item-key="id"
      class="xrd"
      hide-default-footer
      must-sort
      :loading="loading"
      :headers="headers"
      :items="dsTlsCertificationAuthorityStore.dsTlsCertificationAuthorities"
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
    <AddDsTlsCertificationAuthorityDialog v-if="showAddDialog" @save="hideAddDialog" @cancel="hideAddDialog" />
  </XrdCard>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { XrdBtn, XrdCard, XrdDateTime, XrdLabelWithIcon } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { ApprovedDsTlsCertificationAuthorityListItem } from '@/openapi-types';
import { useDsTlsCertificationAuthorityService } from '@/store/modules/trust-services';
import { useUser } from '@/store/modules/user';

import AddDsTlsCertificationAuthorityDialog from './dialogs/AddDsTlsCertificationAuthorityDialog.vue';

const router = useRouter();
const { t } = useI18n();
const { hasPermission } = useUser();
const dsTlsCertificationAuthorityStore = useDsTlsCertificationAuthorityService();

const showAddDialog = ref(false);
const loading = ref(false);

const showAddButton = computed(() => hasPermission(Permissions.ADD_APPROVED_DS_TLS_CA));
const hasPermissionToDetails = computed(() => hasPermission(Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS));
const headers = computed(
  () =>
    [
      {
        title: t('trustServices.dsTls.dsTlsCertificationAuthority'),
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

function fetchDsTlsCertificationAuthorities(): void {
  loading.value = true;
  dsTlsCertificationAuthorityStore.fetchAll().finally(() => (loading.value = false));
}

function toDetails(dsTlsCertificationAuthority: ApprovedDsTlsCertificationAuthorityListItem): void {
  router.push({
    name: RouteName.DsTlsCertificationAuthorityDetails,
    params: { dsTlsCertificationAuthorityId: String(dsTlsCertificationAuthority.id) },
  });
}

function hideAddDialog(): void {
  showAddDialog.value = false;
}

fetchDsTlsCertificationAuthorities();
</script>

<style lang="scss" scoped></style>
