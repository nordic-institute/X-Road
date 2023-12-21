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
  <main id="ocsp-responders" class="mt-8">
    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :header-props="{ showHeaderBorder: false }"
      :items="ocspResponders"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-value="id"
      :loader-height="2"
      data-test="ocsp-responders-table"
    >
      <template #top>
        <data-table-toolbar>
          <xrd-button
            variant="outlined"
            data-test="add-ocsp-responder-button"
            @click="showAddOcspResponderDialog = true"
          >
            <v-icon class="xrd-large-button-icon" icon="icon-Add" />
            {{ $t('action.add') }}
          </xrd-button>
        </data-table-toolbar>
      </template>

      <template #[`item.url`]="{ item }">
        <div class="xrd-clickable">
          {{ item.url }}
        </div>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            text
            :outlined="false"
            data-test="view-ocsp-responder-certificate"
            v-if="item.has_certificate"
            @click="navigateToCertificateDetails(item)"
          >
            {{ $t('trustServices.viewCertificate') }}
          </xrd-button>
          <xrd-button
            text
            :outlined="false"
            data-test="edit-ocsp-responder"
            @click="openEditOcspResponderDialog(item)"
          >
            {{ $t('action.edit') }}
          </xrd-button>
          <xrd-button
            text
            :outlined="false"
            data-test="delete-ocsp-responder"
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

    <!-- Add Ocsp Responder dialog -->
    <add-ocsp-responder-dialog
      v-if="
        ocspResponderServiceStore.currentCa?.id && showAddOcspResponderDialog
      "
      :ca-id="ocspResponderServiceStore.currentCa.id"
      @cancel="hideAddOcspResponderDialog"
      @save="hideAddOcspResponderDialog"
    />

    <!-- Edit Ocsp Responder dialog -->
    <edit-ocsp-responder-dialog
      v-if="selectedOcspResponder && showEditOcspResponderDialog"
      :ocsp-responder="selectedOcspResponder"
      @cancel="hideEditOcspResponderDialog"
      @save="hideEditOcspResponderDialogAndRefetch"
    />

    <!-- Confirm delete dialog -->
    <xrd-confirm-dialog
      v-if="confirmDelete"
      :dialog="confirmDelete"
      title="trustServices.trustService.ocspResponders.delete.confirmationDialog.title"
      text="trustServices.trustService.ocspResponders.delete.confirmationDialog.message"
      :data="{ url: selectedOcspResponder?.url }"
      :loading="deletingOcspResponder"
      @cancel="confirmDelete = false"
      @accept="deleteOcspResponder"
    />
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { mapActions } from 'pinia';
import { useOcspResponderService } from '@/store/modules/trust-services';
import { useNotifications } from '@/store/modules/notifications';
import AddOcspResponderDialog from '@/components/ocspResponders/AddOcspResponderDialog.vue';
import {
  ApprovedCertificationService,
  CertificateAuthority,
  OcspResponder,
} from '@/openapi-types';
import EditOcspResponderDialog from '@/components/ocspResponders/EditOcspResponderDialog.vue';
import { RouteName } from '@/global';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';

export default defineComponent({
  components: {
    CustomDataTableFooter,
    DataTableToolbar,
    EditOcspResponderDialog,
    AddOcspResponderDialog,
    VDataTable,
  },
  props: {
    ca: {
      type: [
        Object as () => ApprovedCertificationService,
        Object as () => CertificateAuthority,
      ],
      required: true,
    },
  },
  setup() {
    const ocspResponderServiceStore = useOcspResponderService();
    return { ocspResponderServiceStore };
  },
  data() {
    return {
      loading: false,
      showAddOcspResponderDialog: false,
      showEditOcspResponderDialog: false,
      selectedOcspResponder: undefined as undefined | OcspResponder,
      confirmDelete: false,
      deletingOcspResponder: false,
    };
  },
  computed: {
    ocspResponders(): OcspResponder[] {
      return this.ocspResponderServiceStore.currentOcspResponders;
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t(
            'trustServices.trustService.ocspResponders.url',
          ) as string,
          align: 'start',
          key: 'url',
        },
        {
          title: '',
          key: 'button',
          sortable: false,
        },
      ];
    },
  },
  created() {
    this.ocspResponderServiceStore.loadByCa(this.ca);
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    hideAddOcspResponderDialog() {
      this.showAddOcspResponderDialog = false;
    },
    hideEditOcspResponderDialog() {
      this.showEditOcspResponderDialog = false;
    },
    hideEditOcspResponderDialogAndRefetch() {
      this.showEditOcspResponderDialog = false;
      this.fetchOcspResponders();
    },
    openDeleteConfirmationDialog(ocspResponder: OcspResponder): void {
      this.selectedOcspResponder = ocspResponder;
      this.confirmDelete = true;
    },
    openEditOcspResponderDialog(ocspResponder: OcspResponder): void {
      this.selectedOcspResponder = ocspResponder;
      this.showEditOcspResponderDialog = true;
    },
    navigateToCertificateDetails(ocspResponder: OcspResponder) {
      this.$router.push({
        name: RouteName.OcspResponderCertificateDetails,
        params: {
          ocspResponderId: String(ocspResponder.id),
        },
      });
    },
    fetchOcspResponders(): void {
      this.ocspResponderServiceStore.fetchOcspResponders();
    },
    deleteOcspResponder(): void {
      if (!this.selectedOcspResponder) return;

      this.deletingOcspResponder = true;
      this.ocspResponderServiceStore
        .deleteOcspResponder(this.selectedOcspResponder.id)
        .then(() => {
          this.showSuccess(
            this.$t('trustServices.trustService.ocspResponders.delete.success'),
          );
          this.confirmDelete = false;
          this.deletingOcspResponder = false;
          this.fetchOcspResponders();
        })
        .catch((error) => {
          this.showError(error);
        });
    },
  },
});
</script>
<style lang="scss" scoped>
@import '@/assets/tables';
</style>
