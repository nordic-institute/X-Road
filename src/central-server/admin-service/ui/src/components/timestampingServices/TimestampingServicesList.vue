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
  <div data-test="timestamping-services">
    <!-- Title and button -->
    <div class="table-toolbar align-fix mt-8 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('trustServices.timestampingServices') }}
      </div>

      <xrd-button
        v-if="showAddTsaButton"
        data-test="add-timestamping-service"
        @click="showAddDialog = true"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <XrdIconAdd />
        </xrd-icon-base>
        {{ $t('trustServices.timestampingService.dialog.add.title') }}
      </xrd-button>
    </div>

    <!-- Table -->
    <v-data-table
      v-if="showTsaList"
      :loading="loading"
      :headers="headers"
      :items="timestampingServices"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
      data-test="timestamping-services-table"
    >
      <template #[`item.timestamping_interval`]="{ item }">
        {{
          $t(
            'trustServices.trustService.timestampingService.timestampingIntervalMinutes',
            { min: toMinutes(item.timestamping_interval) },
          )
        }}
      </template>
      <template #[`item.cost`]="{ item }">
        {{
          $t(
            'trustServices.trustService.timestampingService.costValues.' +
              item.cost,
          )
        }}
      </template>
      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            text
            :outlined="false"
            data-test="view-timestamping-service-certificate"
            @click="navigateToCertificateDetails(item)"
          >
            {{ $t('trustServices.viewCertificate') }}
          </xrd-button>
          <xrd-button
            v-if="showEditTsaButton"
            text
            :outlined="false"
            data-test="edit-timestamping-service"
            @click="openEditDialog(item)"
          >
            {{ $t('action.edit') }}
          </xrd-button>
          <xrd-button
            v-if="showDeleteTsaButton"
            text
            :outlined="false"
            data-test="delete-timestamping-service"
            @click="showDeleteDialog(item)"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Confirm delete dialog -->
    <xrd-confirm-dialog
      v-if="confirmDelete"
      :dialog="confirmDelete"
      title="trustServices.trustService.timestampingService.delete.dialog.title"
      text="trustServices.trustService.timestampingService.delete.dialog.message"
      :data="{ url: selectedTimestampingService.url }"
      :loading="deletingTimestampingService"
      @cancel="confirmDelete = false"
      @accept="deleteTimestampingService"
    />

    <AddTimestampingServiceDialog
      v-if="showAddDialog"
      :show-dialog="showAddDialog"
      @save="hideAddDialog"
      @cancel="hideAddDialog"
    >
    </AddTimestampingServiceDialog>
    <EditTimestampingServiceDialog
      v-if="showEditDialog"
      :show-dialog="showEditDialog"
      :tsa-service="selectedTimestampingService"
      @save="hideEditDialog"
      @cancel="hideEditDialog"
    >
    </EditTimestampingServiceDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import AddTimestampingServiceDialog from '@/components/timestampingServices/AddTimestampingServiceDialog.vue';
import EditTimestampingServiceDialog from '@/components/timestampingServices/EditTimestampingServiceDialog.vue';
import { DataTableHeader } from 'vuetify';
import { mapActions, mapState, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { userStore } from '@/store/modules/user';
import { TimestampingService } from '@/openapi-types';
import { timestampingServicesStore } from '@/store/modules/trust-services';
import { Permissions, RouteName } from '@/global';

export default Vue.extend({
  name: 'TimestampingServicesList',
  components: {
    AddTimestampingServiceDialog,
    EditTimestampingServiceDialog,
  },
  props: {},
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
    ...mapStores(timestampingServicesStore, notificationsStore),
    ...mapState(userStore, ['hasPermission']),

    timestampingServices(): TimestampingService[] {
      return this.timestampingServicesStore.timestampingServices;
    },
    showTsaList(): boolean {
      return this.hasPermission(Permissions.VIEW_APPROVED_TSAS);
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
          text: this.$t(
            'trustServices.trustService.timestampingService.url',
          ) as string,
          align: 'start',
          value: 'url',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t(
            'trustServices.trustService.timestampingService.timestampingInterval',
          ) as string,
          align: 'start',
          value: 'timestamping_interval',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t(
            'trustServices.trustService.timestampingService.cost',
          ) as string,
          align: 'start',
          value: 'cost',
          class: 'xrd-table-header text-uppercase',
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
    this.fetchTimestampingServices();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    fetchTimestampingServices(): void {
      this.loading = true;
      this.timestampingServicesStore
        .fetchTimestampingServices()
        .finally(() => (this.loading = false));
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
          this.showSuccess(
            this.$t(
              'trustServices.trustService.timestampingService.delete.success',
            ),
          );
          this.confirmDelete = false;
          this.deletingTimestampingService = false;
        })
        .catch((error) => {
          this.showError(error);
        });
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

<style lang="scss" scoped>
@import '~styles/tables';

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
