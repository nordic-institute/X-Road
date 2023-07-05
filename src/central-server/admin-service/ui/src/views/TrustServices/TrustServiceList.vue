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
      <!-- Title and button -->
      <div class="table-toolbar align-fix mt-0 pl-0">
        <div class="xrd-view-title align-fix">
          {{ $t('trustServices.certificationServices') }}
        </div>

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
      </div>

      <!-- Table -->
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
        hide-default-footer
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
          <div>{{ item.not_before | formatDateTime }}</div>
        </template>
        <template #[`item.not_after`]="{ item }">
          <div>{{ item.not_after | formatDateTime }}</div>
        </template>

        <template #footer>
          <div class="custom-footer"></div>
        </template>
      </v-data-table>
    </div>

    <TimestampingServicesList v-if="showTsaList"></TimestampingServicesList>

    <!-- Dialogs -->
    <AddCertificationServiceDialog
      v-if="showAddCSDialog"
      :show-dialog="showAddCSDialog"
      @save="addCertificationService"
      @cancel="hideAddCSDialog"
    >
    </AddCertificationServiceDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import AddCertificationServiceDialog from '@/components/certificationServices/AddCertificationServiceDialog.vue';
import { DataTableHeader } from 'vuetify';
import { mapActions, mapState, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { useCertificationServiceStore } from '@/store/modules/trust-services';
import { userStore } from '@/store/modules/user';
import { Permissions, RouteName } from '@/global';
import {
  ApprovedCertificationService,
  ApprovedCertificationServiceListItem,
  CertificationServiceFileAndSettings,
} from '@/openapi-types';
import TimestampingServicesList from '@/components/timestampingServices/TimestampingServicesList.vue';

export default Vue.extend({
  name: 'TrustServiceList',
  components: {
    AddCertificationServiceDialog,
    TimestampingServicesList,
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
    ...mapStores(useCertificationServiceStore, notificationsStore),
    ...mapState(userStore, ['hasPermission']),
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
          text: this.$t('trustServices.approvedCertificationService') as string,
          align: 'start',
          value: 'name',
          class: 'xrd-table-header ts-table-header-server-code text-uppercase',
        },
        {
          text: this.$t('trustServices.validFrom') as string,
          align: 'start',
          value: 'not_before',
          class: 'xrd-table-header ts-table-header-valid-from text-uppercase',
        },
        {
          text: this.$t('trustServices.validTo') as string,
          align: 'start',
          value: 'not_after',
          class: 'xrd-table-header ts-table-header-valid-to text-uppercase',
        },
      ];
    },
  },
  created() {
    this.fetchCertificationServices();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
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
@import '~styles/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
