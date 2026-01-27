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
  <XrdElevatedViewFixedWidth closeable :translated-title="serviceClientId" :breadcrumbs="breadcrumbs" @close="close">
    <v-table data-test="service-clients-table" class="xrd border xrd-rounded-12 mb-6">
      <thead>
        <tr>
          <th>{{ $t('serviceClients.name') }}</th>
          <th>{{ $t('serviceClients.id') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td class="identifier-wrap">
            <XrdLabelWithIcon icon="settings_system_daydream" semi-bold>
              <template #label>
                <ClientName :service-client="serviceClient" />
              </template>
            </XrdLabelWithIcon>
          </td>
          <td class="identifier-wrap">{{ serviceClient.id }}</td>
        </tr>
      </tbody>
    </v-table>

    <div class="d-flex flex-row align-center mt-4 mb-4">
      <div class="title-component font-weight-medium">
        {{ $t('serviceClients.accessRights') }}
      </div>
      <v-spacer />
      <XrdBtn
        v-if="canEdit && serviceClientAccessRights.length > 0"
        data-test="remove-all-access-rights"
        variant="outlined"
        text="serviceClients.removeAll"
        @click="showConfirmDeleteAll = true"
      />
      <XrdBtn
        v-if="canEdit"
        data-test="add-subjects-dialog"
        class="ml-2"
        text="serviceClients.addService"
        @click="showAddServiceDialog()"
      />
    </div>

    <v-data-table
      class="xrd border xrd-rounded-12 mb-6"
      data-test="service-client-access-rights-table"
      no-data-text="serviceClients.noAccessRights"
      items-per-page="-1"
      hide-default-footer
      :items="keyedServiceClientAccessRights()"
      :headers="headers"
      item-key="uiKey"
    >
      <template #item.service_code="{ value }">
        <XrdLabelWithIcon icon="settings_system_daydream" semi-bold :label="value" />
      </template>
      <template #item.rights_given_at="{ item }">
        <XrdDateTime :value="item.rights_given_at" with-seconds />
      </template>
      <template #item.actions="{ item }">
        <XrdBtn v-if="canEdit" data-test="access-right-remove" variant="text" color="tertiary" text="action.remove" @click="remove(item)" />
      </template>
    </v-data-table>

    <AddServiceClientServiceDialog
      v-if="isAddServiceDialogVisible"
      :dialog="isAddServiceDialogVisible"
      :service-candidates="serviceCandidates()"
      @save="addService"
      @cancel="hideAddService"
    />

    <!-- Confirm dialog delete group -->
    <XrdConfirmDialog
      v-if="showConfirmDeleteAll"
      :dialog="showConfirmDeleteAll"
      title="serviceClients.removeAllTitle"
      text="serviceClients.removeAllText"
      focus-on-accept
      @cancel="showConfirmDeleteAll = false"
      @accept="removeAll()"
    />

    <XrdConfirmDialog
      v-if="showConfirmDeleteOne"
      :dialog="showConfirmDeleteOne"
      title="serviceClients.removeOneTitle"
      text="serviceClients.removeOneText"
      focus-on-accept
      @cancel="resetDeletionSettings()"
      @accept="doRemoveAccessRight()"
    />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { AccessRight, AccessRights, ServiceClient } from '@/openapi-types';
import AddServiceClientServiceDialog from '@/views/Clients/ServiceClients/AddServiceClientServiceDialog.vue';
import { serviceCandidatesForServiceClient } from '@/util/serviceClientUtils';

import { ServiceCandidate } from '@/ui-types';
import { Permissions, RouteName } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { XrdElevatedViewFixedWidth, XrdBtn, XrdDateTime, useNotifications, XrdLabelWithIcon, XrdConfirmDialog } from '@niis/shared-ui';
import ClientName from '@/components/client/ClientName.vue';
import { useServiceClients } from '@/store/modules/service-clients';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { clientTitle } from '@/util/ClientUtil';
import { useClient } from '@/store/modules/client';

interface UiAccessRight extends AccessRight {
  id: number;
}

export default defineComponent({
  components: {
    ClientName,
    AddServiceClientServiceDialog,
    XrdElevatedViewFixedWidth,
    XrdDateTime,
    XrdBtn,
    XrdLabelWithIcon,
    XrdConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    serviceClientId: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      serviceClientAccessRights: [] as AccessRight[],
      serviceClient: {} as ServiceClient,
      accessRightToDelete: null as AccessRight | null,
      isAddServiceDialogVisible: false as boolean,
      showConfirmDeleteAll: false as boolean,
      showConfirmDeleteOne: false as boolean,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClient, ['client', 'clientLoading']),
    ...mapState(useServiceDescriptions, ['serviceDescriptions']),
    canEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_ACL_SUBJECT_OPEN_SERVICES);
    },
    headers() {
      return [
        {
          title: this.$t('serviceClients.serviceCode'),
          align: 'start',
          key: 'service_code',
        },
        {
          title: this.$t('serviceClients.title'),
          align: 'start',
          key: 'service_title',
        },
        {
          title: this.$t('serviceClients.accessRightsGiven'),
          align: 'start',
          key: 'rights_given_at',
        },
        { title: '', align: 'end', key: 'actions' },
      ] as DataTableHeader[];
    },
    breadcrumbs() {
      const breadcrumbs = [
        {
          title: this.$t('tab.main.clients'),
          to: { name: RouteName.Clients },
        },
      ] as BreadcrumbItem[];

      if (this.client) {
        breadcrumbs.push(
          {
            title: clientTitle(this.client, this.clientLoading),
            to: {
              name: RouteName.SubsystemDetails,
              params: { id: this.client.id },
            },
          },
          {
            title: this.$t('tab.client.serviceClients'),
            to: {
              name: RouteName.SubsystemServiceClients,
              params: { id: this.client.id },
            },
          },
        );
      }
      breadcrumbs.push({
        title: this.serviceClient?.name || '',
      });

      return breadcrumbs;
    },
  },
  created(): void {
    this.fetchData();
  },
  methods: {
    ...mapActions(useClient, ['fetchClient']),
    ...mapActions(useServiceDescriptions, ['fetchServiceDescriptions']),
    ...mapActions(useServiceClients, ['fetchServiceClient', 'fetchAccessRights', 'removeAccessRights', 'saveAccessRights']),
    fetchData(): void {
      this.doFetchAccessRights();
      this.fetchClient(this.id).catch((error) => this.addError(error, { navigate: true }));
      this.fetchServiceDescriptions(this.id, false).catch((error) => this.addError(error));
      this.fetchServiceClient(this.id, this.serviceClientId)
        .then((data) => (this.serviceClient = data))
        .catch((error) => this.addError(error, { navigate: true }));
    },
    doFetchAccessRights() {
      this.fetchAccessRights(this.id, this.serviceClientId)
        .then((data) => (this.serviceClientAccessRights = data))
        .catch((error) => this.addError(error));
    },
    close(): void {
      this.$router.back();
    },
    resetDeletionSettings(): void {
      this.showConfirmDeleteOne = false;
      this.accessRightToDelete = null;
    },
    remove(accessRight: AccessRight): void {
      this.showConfirmDeleteOne = true;
      this.accessRightToDelete = accessRight;
    },
    doRemoveAccessRight(): void {
      if (!this.accessRightToDelete) {
        return;
      }
      this.removeAccessRights(this.id, this.serviceClientId, [this.accessRightToDelete?.service_code])
        .then(() => {
          this.addSuccessMessage('accessRights.removeSuccess');
          if (this.serviceClientAccessRights.length === 1) {
            this.serviceClientAccessRights = [];
          } else {
            this.doFetchAccessRights();
          }
        })
        .catch((error) => this.addError(error))
        .finally(() => {
          this.showConfirmDeleteOne = false;
          this.accessRightToDelete = null;
        });
    },
    addService(accessRights: AccessRight[]): void {
      this.hideAddService();
      const accessRightsObject: AccessRights = { items: accessRights };
      this.saveAccessRights(this.id, this.serviceClientId, accessRightsObject)
        .then(() => {
          this.addSuccessMessage('serviceClients.addServiceClientAccessRightSuccess');
          this.doFetchAccessRights();
        })
        .catch((error) => this.addError(error));
    },
    hideAddService(): void {
      this.isAddServiceDialogVisible = false;
    },
    showAddServiceDialog(): void {
      this.isAddServiceDialogVisible = true;
    },
    removeAll(): void {
      this.showConfirmDeleteAll = false;

      this.removeAccessRights(
        this.id,
        this.serviceClientId,
        this.serviceClientAccessRights.map((item) => item.service_code),
      )
        .then(() => {
          this.addSuccessMessage('accessRights.removeSuccess');
          this.serviceClientAccessRights = [];
        })
        .catch((error) => this.addError(error));
    },
    serviceCandidates(): ServiceCandidate[] {
      return serviceCandidatesForServiceClient(this.serviceDescriptions, this.serviceClientAccessRights);
    },

    keyedServiceClientAccessRights(): UiAccessRight[] {
      return this.serviceClientAccessRights.map((sca: AccessRight, index: number) => {
        return { ...sca, id: index };
      }) as UiAccessRight[];
    },
  },
});
</script>

<style lang="scss" scoped></style>
