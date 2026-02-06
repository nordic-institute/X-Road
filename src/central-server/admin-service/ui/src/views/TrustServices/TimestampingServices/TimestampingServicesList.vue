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
  <XrdCard data-test="timestamping-services" title="trustServices.timestampingServices">
    <template #title-actions>
      <XrdBtn
        v-if="showAddTsaButton"
        data-test="add-timestamping-service"
        class="mr-4"
        text="action.add"
        prepend-icon="add_circle"
        variant="outlined"
        @click="showAddDialog = true"
      />
    </template>

    <v-data-table
      data-test="timestamping-services-table"
      class="xrd"
      item-value="id"
      hide-default-footer
      must-sort
      :loading="loading"
      :headers="headers"
      :items="timestampingServices"
      :items-per-page="-1"
    >
      <template #[`item.url`]="{ item }">
        <XrdLabelWithIcon icon="link" label-color="on-surface" :label="item.url" />
      </template>
      <template #[`item.timestamping_interval`]="{ item }">
        {{
          $t('trustServices.trustService.timestampingService.timestampingIntervalMinutes', { min: toMinutes(item.timestamping_interval) })
        }}
      </template>
      <template #[`item.cost_type`]="{ item }">
        {{ $t(`trustServices.trustService.costType.${item.cost_type}`) }}
      </template>
      <template #[`item.button`]="{ item }">
        <XrdBtn
          data-test="view-timestamping-service-certificate"
          text="trustServices.viewCertificate"
          variant="text"
          color="tertiary"
          @click="navigateToCertificateDetails(item)"
        />
        <XrdBtn
          v-if="showEditTsaButton"
          data-test="edit-timestamping-service"
          text="action.edit"
          variant="text"
          color="tertiary"
          @click="openEditDialog(item)"
        />
        <XrdBtn
          v-if="showDeleteTsaButton"
          data-test="delete-timestamping-service"
          text="action.delete"
          variant="text"
          color="tertiary"
          @click="showDeleteDialog(item)"
        />
      </template>
    </v-data-table>

    <!-- Confirm delete dialog -->
    <XrdConfirmDialog
      v-if="selectedTimestampingService && confirmDelete"
      title="trustServices.trustService.timestampingService.delete.dialog.title"
      text="trustServices.trustService.timestampingService.delete.dialog.message"
      focus-on-accept
      :data="{ url: selectedTimestampingService.url }"
      :loading="deletingTimestampingService"
      @cancel="confirmDelete = false"
      @accept="deleteTimestampingService"
    />

    <AddTimestampingServiceDialog v-if="showAddDialog" :show-dialog="showAddDialog" @save="hideAddDialog" @cancel="hideAddDialog" />
    <EditTimestampingServiceDialog
      v-if="selectedTimestampingService && showEditDialog"
      :tsa-service="selectedTimestampingService"
      @save="hideEditDialog"
      @cancel="hideEditDialog"
    />
  </XrdCard>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import AddTimestampingServiceDialog from './dialogs/AddTimestampingServiceDialog.vue';
import EditTimestampingServiceDialog from './dialogs/EditTimestampingServiceDialog.vue';
import { mapState, mapStores } from 'pinia';
import { useUser } from '@/store/modules/user';
import { TimestampingService } from '@/openapi-types';
import { useTimestampingServices } from '@/store/modules/trust-services';
import { Permissions, RouteName } from '@/global';
import { XrdCard, XrdBtn, XrdLabelWithIcon, useNotifications, XrdConfirmDialog } from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

export default defineComponent({
  components: {
    XrdBtn,
    XrdCard,
    XrdLabelWithIcon,
    AddTimestampingServiceDialog,
    EditTimestampingServiceDialog,
    XrdConfirmDialog,
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      loading: false,
      confirmDelete: false,
      deletingTimestampingService: false,
      selectedTimestampingService: undefined as undefined | TimestampingService,
      showAddDialog: false,
      showEditDialog: false,
    };
  },
  computed: {
    ...mapStores(useTimestampingServices),
    ...mapState(useUser, ['hasPermission']),

    timestampingServices(): TimestampingService[] {
      return this.timestampingServicesStore.timestampingServices;
    },
    showAddTsaButton(): boolean {
      return this.hasPermission(Permissions.ADD_APPROVED_TSA);
    },
    showDeleteTsaButton(): boolean {
      return this.hasPermission(Permissions.DELETE_APPROVED_TSA);
    },
    showEditTsaButton(): boolean {
      return this.hasPermission(Permissions.EDIT_APPROVED_TSA);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('trustServices.trustService.timestampingService.url') as string,
          align: 'start',
          key: 'url',
        },
        {
          title: this.$t('trustServices.trustService.timestampingService.timestampingInterval') as string,
          align: 'start',
          key: 'timestamping_interval',
        },
        {
          title: this.$t('trustServices.trustService.cost') as string,
          align: 'start',
          key: 'cost_type',
        },
        {
          title: '',
          align: 'end',
          sortable: false,
          key: 'button',
        },
      ];
    },
  },
  created() {
    this.fetchTimestampingServices();
  },
  methods: {
    fetchTimestampingServices(): void {
      this.loading = true;
      this.timestampingServicesStore.fetchTimestampingServices().finally(() => (this.loading = false));
    },
    showDeleteDialog(item: TimestampingService): void {
      this.selectedTimestampingService = item;
      this.confirmDelete = true;
    },
    hideAddDialog(): void {
      this.showAddDialog = false;
    },
    openEditDialog(item: TimestampingService): void {
      this.selectedTimestampingService = item;
      this.showEditDialog = true;
    },
    hideEditDialog(): void {
      this.showEditDialog = false;
    },
    deleteTimestampingService(): void {
      if (!this.selectedTimestampingService) return;
      this.deletingTimestampingService = true;
      this.timestampingServicesStore
        .delete(this.selectedTimestampingService.id)
        .then(() => {
          this.addSuccessMessage('trustServices.trustService.timestampingService.delete.success');
          this.confirmDelete = false;
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.deletingTimestampingService = false));
    },
    toMinutes(seconds: number): string {
      return '' + parseFloat((seconds / 60).toFixed(2));
    },
    navigateToCertificateDetails(item: TimestampingService) {
      this.$router.push({
        name: RouteName.TimestampingServiceCertificateDetails,
        params: {
          timestampingServiceId: String(item.id),
        },
      });
    },
  },
});
</script>

<style lang="scss" scoped></style>
