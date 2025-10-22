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
  <XrdSubView id="ocsp-responders" class="mt-8">
    <template #header>
      <v-spacer />
      <XrdBtn
        data-test="add-ocsp-responder-button"
        variant="flat"
        prepend-icon="add_circle"
        text="action.add"
        @click="showAddOcspResponderDialog = true"
      />
    </template>
    <!-- Table -->
    <v-data-table
      item-value="id"
      data-test="ocsp-responders-table"
      class="xrd"
      hide-default-footer
      must-sort
      :loading="loading"
      :headers="headers"
      :header-props="{ showHeaderBorder: false }"
      :items="ocspResponders"
      :items-per-page="-1"
    >
      <template #[`item.url`]="{ item }">
        <XrdLabelWithIcon icon="database" semi-bold :label="item.url" />
      </template>

      <template #[`item.button`]="{ item }">
        <XrdBtn
          data-test="delete-ocsp-responder"
          class="ml-4"
          text="action.delete"
          variant="text"
          color="tertiary"
          @click="openDeleteConfirmationDialog(item)"
        />
        <XrdBtn
          data-test="edit-ocsp-responder"
          class="ml-4"
          text="action.edit"
          variant="text"
          color="tertiary"
          @click="openEditOcspResponderDialog(item)"
        />
        <XrdBtn
          v-if="item.has_certificate"
          data-test="view-ocsp-responder-certificate"
          text="trustServices.viewCertificate"
          variant="text"
          color="tertiary"
          @click="navigateToOscpCertificate(item)"
        />
      </template>
    </v-data-table>

    <!-- Add Ocsp Responder dialog -->
    <AddOcspResponderDialog
      v-if="
        ocspResponderServiceStore.currentCa?.id && showAddOcspResponderDialog
      "
      @cancel="hideAddOcspResponderDialog"
      @save="hideAddOcspResponderDialog"
    />

    <!-- Edit Ocsp Responder dialog -->
    <EditOcspResponderDialog
      v-if="selectedOcspResponder && showEditOcspResponderDialog"
      :ocsp-responder="selectedOcspResponder"
      @cancel="hideEditOcspResponderDialog"
      @save="hideEditOcspResponderDialogAndRefetch"
    />

    <!-- Confirm delete dialog -->
    <XrdConfirmDialog
      v-if="confirmDelete"
      title="trustServices.trustService.ocspResponders.delete.confirmationDialog.title"
      text="trustServices.trustService.ocspResponders.delete.confirmationDialog.message"
      focus-on-accept
      :data="{ url: selectedOcspResponder?.url }"
      :loading="deletingOcspResponder"
      @cancel="confirmDelete = false"
      @accept="deleteOcspResponder"
    />
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { mapActions } from 'pinia';
import { useOcspResponderService } from '@/store/modules/trust-services';
import AddOcspResponderDialog from './dialogs/AddOcspResponderDialog.vue';
import EditOcspResponderDialog from './dialogs/EditOcspResponderDialog.vue';
import {
  ApprovedCertificationService,
  CertificateAuthority,
  OcspResponder,
} from '@/openapi-types';
import { RouteName } from '@/global';
import {
  XrdSubView,
  XrdBtn,
  XrdLabelWithIcon,
  useNotifications,
} from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

export default defineComponent({
  components: {
    XrdSubView,
    XrdBtn,
    XrdLabelWithIcon,
    EditOcspResponderDialog,
    AddOcspResponderDialog,
  },
  props: {
    ca: {
      type: Object as PropType<
        ApprovedCertificationService | CertificateAuthority
      >,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const ocspResponderServiceStore = useOcspResponderService();
    return { ocspResponderServiceStore, addError, addSuccessMessage };
  },
  data() {
    return {
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
    loading() {
      return this.ocspResponderServiceStore.loadingOcspResponders;
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
          align: 'end',
          sortable: false,
        },
      ];
    },
  },
  created() {
    this.ocspResponderServiceStore.loadByCa(this.ca);
  },
  methods: {
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
    navigateToOscpCertificate(ocspResponder: OcspResponder) {
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
          this.addSuccessMessage(
            'trustServices.trustService.ocspResponders.delete.success',
          );
          this.confirmDelete = false;
          this.deletingOcspResponder = false;
          this.fetchOcspResponders();
        })
        .catch((error) => this.addError(error));
    },
  },
});
</script>
