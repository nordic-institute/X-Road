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
  <div>
    <div data-test="certification-services">
      <titled-view title-key="trustServices.certificationServices">
        <template #header-buttons>
          <xrd-button
            v-if="showAddCSButton"
            data-test="add-certification-service"
            @click="showAddCSDialog = true"
          >
            <xrd-icon-base class="xrd-large-button-icon">
              <XrdIconAdd />
            </xrd-icon-base>
            {{ $t('trustServices.addCertificationService') }}
          </xrd-button>
        </template>
        <v-data-table
          :loading="loading"
          :headers="headers"
          :items="certificationServices"
          :search="search"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="id"
          :loader-height="2"
        >
          <template #[`item.name`]="{ item }">
            <div
              v-if="hasPermissionToDetails"
              class="xrd-clickable"
              @click="toDetails(item)"
            >
              {{ item.name }}
            </div>
            <div v-else>
              {{ item.name }}
            </div>
          </template>
          <template #[`item.not_before`]="{ item }">
            <div>
              <date-time :value="item.not_before" />
            </div>
          </template>
          <template #[`item.not_after`]="{ item }">
            <div>
              <date-time :value="item.not_after" />
            </div>
          </template>

          <template #bottom>
            <custom-data-table-footer />
          </template>
        </v-data-table>
      </titled-view>
    </div>

    <timestamping-services-list v-if="showTsaList" class="tsa-list" />

    <!-- Dialogs -->
    <add-certification-service-dialog
      v-if="showAddCSDialog"
      @add="addCertificationService"
      @cancel="hideAddCSDialog"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import AddCertificationServiceDialog from '@/components/certificationServices/AddCertificationServiceDialog.vue';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { mapActions, mapState, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useCertificationService } from '@/store/modules/trust-services';
import { useUser } from '@/store/modules/user';
import { Permissions, RouteName } from '@/global';
import {
  ApprovedCertificationService,
  ApprovedCertificationServiceListItem,
  CertificationServiceFileAndSettings,
} from '@/openapi-types';
import TimestampingServicesList from '@/components/timestampingServices/TimestampingServicesList.vue';
import DateTime from '@/components/ui/DateTime.vue';
import { DataTableHeader } from '@/ui-types';
import TitledView from '@/components/ui/TitledView.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';

export default defineComponent({
  name: 'TrustServiceList',
  components: {
    CustomDataTableFooter,
    TitledView,
    DateTime,
    AddCertificationServiceDialog,
    TimestampingServicesList,
    VDataTable,
  },
  data() {
    return {
      search: '' as string,
      loading: false,
      showAddCSDialog: false,
      permissions: Permissions,
    };
  },
  computed: {
    ...mapStores(useCertificationService, useNotifications),
    ...mapState(useUser, ['hasPermission']),
    certificationServices(): ApprovedCertificationServiceListItem[] {
      return this.certificationServiceStore.certificationServices;
    },
    hasPermissionToDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_APPROVED_CA_DETAILS);
    },
    showAddCSButton(): boolean {
      return this.hasPermission(Permissions.ADD_APPROVED_CA);
    },
    showTsaList(): boolean {
      return this.hasPermission(Permissions.VIEW_APPROVED_TSAS);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t(
            'trustServices.approvedCertificationService',
          ) as string,
          align: 'start',
          key: 'name',
        },
        {
          title: this.$t('trustServices.validFrom') as string,
          align: 'start',
          key: 'not_before',
        },
        {
          title: this.$t('trustServices.validTo') as string,
          align: 'start',
          key: 'not_after',
        },
      ];
    },
  },
  created() {
    this.fetchCertificationServices();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    fetchCertificationServices(): void {
      this.loading = true;
      this.certificationServiceStore
        .fetchAll()
        .finally(() => (this.loading = false));
    },
    hideAddCSDialog(): void {
      this.showAddCSDialog = false;
    },
    toDetails(certificationService: ApprovedCertificationService): void {
      this.$router.push({
        name: RouteName.CertificationServiceDetails,
        params: { certificationServiceId: String(certificationService.id) },
      });
    },
    addCertificationService(
      addCertificationService: CertificationServiceFileAndSettings,
    ): void {
      this.certificationServiceStore
        .add(addCertificationService)
        .then(() => {
          this.showSuccess(this.$t('trustServices.certImportedSuccessfully'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => this.hideAddCSDialog());
    },
  },
});
</script>
<style lang="scss" scoped>
@import '@/assets/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.tsa-list {
  margin-top: 20px;
}
</style>
