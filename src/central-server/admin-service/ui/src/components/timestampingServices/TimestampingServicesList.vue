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
    <titled-view title-key="trustServices.timestampingServices">
      <template #header-buttons>
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
      </template>
      <v-data-table
        v-if="showTsaList"
        data-test="timestamping-services-table"
        class="elevation-0 data-table"
        item-value="id"
        :loading="loading"
        :headers="headers"
        :items="timestampingServices"
        :must-sort="true"
        :items-per-page="-1"
        :loader-height="2"
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

        <template #bottom>
          <custom-data-table-footer />
        </template>
      </v-data-table>
    </titled-view>

    <!-- Confirm delete dialog -->
    <xrd-confirm-dialog
      v-if="selectedTimestampingService && confirmDelete"
      :dialog="confirmDelete"
      title="trustServices.trustService.timestampingService.delete.dialog.title"
      text="trustServices.trustService.timestampingService.delete.dialog.message"
      :data="{ url: selectedTimestampingService.url }"
      :loading="deletingTimestampingService"
      @cancel="confirmDelete = false"
      @accept="deleteTimestampingService"
    />

    <add-timestamping-service-dialog
      v-if="showAddDialog"
      :show-dialog="showAddDialog"
      @save="hideAddDialog"
      @cancel="hideAddDialog"
    />
    <edit-timestamping-service-dialog
      v-if="selectedTimestampingService && showEditDialog"
      :tsa-service="selectedTimestampingService"
      @save="hideEditDialog"
      @cancel="hideEditDialog"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import AddTimestampingServiceDialog from '@/components/timestampingServices/AddTimestampingServiceDialog.vue';
import EditTimestampingServiceDialog from '@/components/timestampingServices/EditTimestampingServiceDialog.vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { TimestampingService } from '@/openapi-types';
import { useTimestampingServicesStore } from '@/store/modules/trust-services';
import { Permissions, RouteName } from '@/global';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { DataTableHeader } from '@/ui-types';
import TitledView from '@/components/ui/TitledView.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';

export default defineComponent({
  components: {
    CustomDataTableFooter,
    TitledView,
    AddTimestampingServiceDialog,
    EditTimestampingServiceDialog,
    VDataTable,
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
    ...mapStores(useTimestampingServicesStore),
    ...mapState(useUser, ['hasPermission']),

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
          title: this.$t(
            'trustServices.trustService.timestampingService.url',
          ) as string,
          align: 'start',
          key: 'url',
        },
        {
          title: this.$t(
            'trustServices.trustService.timestampingService.timestampingInterval',
          ) as string,
          align: 'start',
          key: 'timestamping_interval',
        },
        {
          title: this.$t(
            'trustServices.trustService.timestampingService.cost',
          ) as string,
          align: 'start',
          key: 'cost',
        },
        {
          title: '',
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
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
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
@import '@/assets/tables';
</style>
