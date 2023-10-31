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
  <main id="intermediate-cas" class="mt-8">
    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :header-props="{ showHeaderBorder: false }"
      :items="intermediateCas"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      data-test="intermediate-cas-table"
    >
      <template #top>
        <data-table-toolbar>
          <xrd-button
            outlined
            data-test="add-intermediate-ca-button"
            @click="showAddIntermediateCaDialog = true"
          >
            <v-icon class="xrd-large-button-icon" icon="icon-Add" />
            {{ $t('action.add') }}
          </xrd-button>
        </data-table-toolbar>
      </template>

      <template #[`item.ca_certificate.subject_common_name`]="{ item }">
        <div
          v-if="hasPermissionToDetails"
          class="xrd-clickable"
          @click="toDetails(item)"
        >
          {{ item.ca_certificate.subject_common_name }}
        </div>
        <div v-else>
          {{ item.ca_certificate.subject_common_name }}
        </div>
      </template>

      <template #[`item.ca_certificate.not_before`]="{ item }">
        <div>
          <date-time :value="item.ca_certificate.not_before" />
        </div>
      </template>

      <template #[`item.ca_certificate.not_after`]="{ item }">
        <div>
          <date-time :value="item.ca_certificate.not_after" />
        </div>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            text
            :outlined="false"
            data-test="view-intermediate-ca-certificate"
            @click="navigateToCertificateDetails(item)"
          >
            {{ $t('trustServices.viewCertificate') }}
          </xrd-button>
          <xrd-button
            text
            :outlined="false"
            data-test="delete-intermediate-ca"
            @click="openDeleteConfirmationDialog(item)"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>

      <template #bottom>
        <custom-data-table-footer />
      </template>
    </v-data-table>

    <!-- Add Intermediate CA dialog -->
    <add-intermediate-ca-dialog
      v-if="
        intermediateCasServiceStore.currentCs && showAddIntermediateCaDialog
      "
      :ca-id="intermediateCasServiceStore.currentCs.id"
      @cancel="hideAddIntermediateCaDialog"
      @save="hideAddIntermediateCaDialog"
    />

    <!-- Confirm delete dialog -->
    <xrd-confirm-dialog
      v-if="confirmDelete"
      :dialog="confirmDelete"
      title="trustServices.trustService.intermediateCas.delete.confirmationDialog.title"
      text="trustServices.trustService.intermediateCas.delete.confirmationDialog.message"
      :data="{
        name: selectedIntermediateCa?.ca_certificate.subject_common_name,
      }"
      :loading="deletingIntermediateCa"
      @cancel="confirmDelete = false"
      @accept="deleteIntermediateCa"
    />
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { DataTableHeader } from '@/ui-types';
import { mapActions, mapState, mapStores } from 'pinia';
import { useIntermediateCasService } from '@/store/modules/trust-services';
import { useNotifications } from '@/store/modules/notifications';
import { VDataTable } from 'vuetify/labs/VDataTable';
import {
  ApprovedCertificationService,
  CertificateAuthority,
} from '@/openapi-types';
import AddIntermediateCaDialog from '@/components/intermediateCas/AddIntermediateCaDialog.vue';
import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import DateTime from '@/components/ui/DateTime.vue';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';

export default defineComponent({
  components: {
    DataTableToolbar,
    CustomDataTableFooter,
    DateTime,
    AddIntermediateCaDialog,
    VDataTable,
  },
  props: {
    cs: {
      type: Object as () => ApprovedCertificationService,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      showAddIntermediateCaDialog: false,
      selectedIntermediateCa: undefined as undefined | CertificateAuthority,
      confirmDelete: false,
      deletingIntermediateCa: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapStores(useIntermediateCasService),
    intermediateCas(): CertificateAuthority[] {
      return this.intermediateCasServiceStore.currentIntermediateCas;
    },
    hasPermissionToDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_APPROVED_CA_DETAILS);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t(
            'trustServices.trustService.intermediateCas.intermediateCa',
          ) as string,
          align: 'start',
          key: 'ca_certificate.subject_common_name',
        },
        {
          title: this.$t('trustServices.validFrom') as string,
          align: 'start',
          key: 'ca_certificate.not_before',
        },
        {
          title: this.$t('trustServices.validTo') as string,
          align: 'start',
          key: 'ca_certificate.not_after',
        },
        {
          title: '',
          key: 'button',
          align: 'end',
          sortable: false,
        },
      ];
    },
  },
  created() {
    this.intermediateCasServiceStore.loadByCs(this.cs);
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    toDetails(intermediateCa: CertificateAuthority) {
      this.$router.push({
        name: RouteName.IntermediateCaDetails,
        params: {
          intermediateCaId: String(intermediateCa.id),
        },
      });
    },
    hideAddIntermediateCaDialog() {
      this.showAddIntermediateCaDialog = false;
    },
    openDeleteConfirmationDialog(intermediateCa: CertificateAuthority): void {
      this.selectedIntermediateCa = intermediateCa;
      this.confirmDelete = true;
    },
    fetchIntermediateCas(): void {
      this.intermediateCasServiceStore.fetchIntermediateCas();
    },
    deleteIntermediateCa(): void {
      if (!this.selectedIntermediateCa) return;

      this.deletingIntermediateCa = true;
      this.intermediateCasServiceStore
        .deleteIntermediateCa(this.selectedIntermediateCa.id as number)
        .then(() => {
          this.showSuccess(
            this.$t(
              'trustServices.trustService.intermediateCas.delete.success',
            ),
          );
          this.confirmDelete = false;
          this.deletingIntermediateCa = false;
          this.fetchIntermediateCas();
        })
        .catch((error) => {
          this.showError(error);
        });
    },
    navigateToCertificateDetails(intermediateCa: CertificateAuthority) {
      this.$router.push({
        name: RouteName.IntermediateCACertificateDetails,
        params: {
          intermediateCaId: String(intermediateCa.id),
        },
      });
    },
  },
});
</script>
<style lang="scss" scoped></style>
