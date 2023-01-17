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
      item-key="id"
      :loader-height="2"
      hide-default-footer
      data-test="ocsp-responders-table"
    >
      <template #header>
        <thead class="borderless-table-header">
          <tr>
            <th />
            <th class="text-right">
              <div class="button-wrap mb-6 mt-4">
                <xrd-button
                  outlined
                  data-test="add-ocsp-responder-button"
                  @click="showAddOcspResponderDialog = true"
                >
                  <v-icon class="xrd-large-button-icon">icon-Add</v-icon>
                  {{ $t('action.add') }}
                </xrd-button>
              </div>
            </th>
          </tr>
        </thead>
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

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Add Ocsp Responder dialog -->
    <AddOcspResponderDialog
      v-if="showAddOcspResponderDialog"
      :ca-id="ocspResponderServiceStore.currentCa.id"
      @cancel="hideAddOcspResponderDialog"
      @save="hideAddOcspResponderDialog"
    ></AddOcspResponderDialog>

    <!-- Edit Ocsp Responder dialog -->
    <EditOcspResponderDialog
      v-if="showEditOcspResponderDialog"
      :ocsp-responder="selectedOcspResponder"
      @cancel="hideEditOcspResponderDialog"
      @save="hideEditOcspResponderDialogAndRefetch"
    ></EditOcspResponderDialog>

    <!-- Confirm delete dialog -->
    <xrd-confirm-dialog
      v-if="confirmDelete"
      :dialog="confirmDelete"
      title="trustServices.trustService.ocspResponders.delete.confirmationDialog.title"
      text="trustServices.trustService.ocspResponders.delete.confirmationDialog.message"
      :data="{ url: selectedOcspResponder.url }"
      :loading="deletingOcspResponder"
      @cancel="confirmDelete = false"
      @accept="deleteOcspResponder"
    />
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { mapActions, mapStores } from 'pinia';
import { useOcspResponderStore } from '@/store/modules/trust-services';
import { notificationsStore } from '@/store/modules/notifications';
import AddOcspResponderDialog from '@/components/ocspResponders/AddOcspResponderDialog.vue';
import {
  ApprovedCertificationService,
  CertificateAuthority,
  OcspResponder,
} from '@/openapi-types';
import EditOcspResponderDialog from '@/components/ocspResponders/EditOcspResponderDialog.vue';
import { RouteName } from '@/global';

export default Vue.extend({
  name: 'OcspRespondersList',
  components: { EditOcspResponderDialog, AddOcspResponderDialog },
  props: {
    ca: {
      type: [
        Object as () => ApprovedCertificationService,
        Object as () => CertificateAuthority,
      ],
      required: true,
    },
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
    ...mapStores(useOcspResponderStore),
    ocspResponders(): OcspResponder[] {
      return this.ocspResponderServiceStore.currentOcspResponders;
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t(
            'trustServices.trustService.ocspResponders.url',
          ) as string,
          align: 'start',
          value: 'url',
          class: 'xrd-table-header mr-table-header-id',
        },
        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header mr-table-header-buttons',
        },
      ];
    },
  },
  created() {
    this.ocspResponderServiceStore.loadByCa(this.ca);
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
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
@import '~styles/tables';

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
