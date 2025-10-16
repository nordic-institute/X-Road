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
  <XrdElevatedViewFixedWidth
    closeable
    :translated-title="title"
    :breadcrumbs="breadcrumbs"
    @close="close"
  >
    <div class="d-flex flex-row align-center mt-4 mb-4">
      <div class="title-component font-weight-medium">
        {{ $t('accessRights.title') }}
      </div>
      <v-spacer />
      <XrdBtn
        v-if="canEdit"
        data-test="remove-all-access-rights"
        variant="outlined"
        text="action.removeAll"
        @click="removeAll()"
      />
      <XrdBtn
        v-if="canEdit"
        data-test="add-subjects-dialog"
        class="ml-2"
        text="accessRights.addServiceClients"
        @click="toggleAddServiceClientsDialog()"
      />
    </div>

    <v-data-table
      class="xrd border xrd-rounded-12 mb-6"
      data-test="service-client-access-rights-table"
      no-data-text="serviceClients.noAccessRights"
      items-per-page="-1"
      hide-default-footer
      :items="serviceClients"
      :headers="headers"
      item-key="uiKey"
    >
      <template #item.name="{ item }">
        <XrdLabelWithIcon icon="settings_system_daydream" semi-bold>
          <template #label>
            <ClientName :service-client="item" />
          </template>
        </XrdLabelWithIcon>
      </template>
      <template #item.rights_given_at="{ item }">
        <XrdDateTime :value="item.rights_given_at" with-seconds />
      </template>
      <template #item.actions="{ item }">
        <XrdBtn
          v-if="canEdit"
          data-test="remove-access-right"
          variant="text"
          color="tertiary"
          text="action.remove"
          @click="remove(item)"
        />
      </template>
    </v-data-table>
    <!-- Confirm dialog remove all Access Right subjects -->
    <XrdConfirmDialog
      v-if="confirmDeleteAll"
      title="accessRights.removeAllTitle"
      text="accessRights.removeAllText"
      :loading="deleting"
      @cancel="resetDeletionSettings(true)"
      @accept="doRemoveSelectedServiceClients(true)"
    />

    <!-- Confirm dialog remove Access Right subject -->
    <XrdConfirmDialog
      v-if="confirmDeleteOne"
      title="accessRights.removeTitle"
      text="accessRights.removeText"
      :loading="deleting"
      @cancel="resetDeletionSettings(false)"
      @accept="doRemoveSelectedServiceClients(false)"
    />

    <!-- Add access right subjects dialog -->
    <AccessRightsDialog
      v-if="serviceDescription && addSubjectsDialogVisible"
      title="accessRights.addServiceClientsTitle"
      :existing-service-clients="serviceClients"
      :client-id="serviceDescription.client_id"
      :adding="adding"
      @cancel="toggleAddServiceClientsDialog"
      @service-clients-added="doAddServiceClients"
    />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Endpoint, ServiceClient } from '@/openapi-types';
import AccessRightsDialog from '../../AccessRightsDialog.vue';
import { Permissions, RouteName } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';

import ClientName from '@/components/client/ClientName.vue';
import { XrdDateTime, XrdElevatedViewFixedWidth, XrdBtn, XrdLabelWithIcon, useNotifications } from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useServices } from '@/store/modules/services';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { clientTitle } from '@/util/ClientUtil';
import { useClient } from '@/store/modules/client';
import { createFullServiceId } from '@/util/helpers';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

export default defineComponent({
  name: 'EndpointAccessRights',
  components: {
    XrdLabelWithIcon,
    XrdBtn,
    XrdElevatedViewFixedWidth,
    ClientName,
    AccessRightsDialog,
    XrdDateTime,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data: () => {
    return {
      endpoint: {} as Endpoint | Record<string, unknown>,
      serviceClients: [] as ServiceClient[],
      adding: false as boolean,
      deleting: false as boolean,
      confirmDeleteAll: false as boolean,
      confirmDeleteOne: false as boolean,
      serviceClientsToDelete: [] as ServiceClient[],
      addSubjectsDialogVisible: false as boolean,
      serviceClientsToAdd: [] as ServiceClient[],
    };
  },
  computed: {
    ...mapState(useClient, ['client', 'clientLoading']),
    ...mapState(useServices, ['service']),
    ...mapState(useServiceDescriptions, ['serviceDescription']),
    ...mapState(useUser, ['hasPermission']),
    canEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_ENDPOINT_ACL);
    },
    title() {
      return `${this.endpoint.method}${this.endpoint.path}`;
    },
    headers() {
      return [
        {
          title: this.$t('accessRights.memberName'),
          align: 'start',
          key: 'name',
        },
        {
          title: this.$t('accessRights.id'),
          align: 'start',
          key: 'id',
        },
        {
          title: this.$t('accessRights.rightsGiven'),
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
            title: this.$t('tab.client.services'),
            to: {
              name: RouteName.SubsystemServices,
              params: { id: this.client.id },
            },
          },
        );
      }
      if (this.client && this.serviceDescription?.type) {
        breadcrumbs.push({
          title: this.serviceDescription?.type,
          to: {
            name: RouteName.SubsystemServices,
            params: { id: this.client.id },
            query: { expand: this.serviceDescription?.id },
          },
        });
      }
      if (this.service) {
        breadcrumbs.push(
          {
            title: this.service?.full_service_code || '',
            to: {
              name: RouteName.ServiceParameters,
              params: { serviceId: this.service?.id },
            },
          },
          {
            title: this.$t('tab.services.endpoints'),
            to: {
              name: RouteName.Endpoints,
              params: { serviceId: this.service?.id },
            },
          },
        );
      }
      breadcrumbs.push({
        title: this.title,
      });
      return breadcrumbs;
    },
  },
  watch: {
    id: {
      immediate: true,
      handler() {
        this.fetchData(true);
      },
    },
  },
  methods: {
    ...mapActions(useClient, ['fetchClient']),
    ...mapActions(useServices, ['fetchService']),
    ...mapActions(useServiceDescriptions, ['fetchServiceDescription']),
    ...mapActions(useServices, [
      'removeEndpointServiceClients',
      'addEndpointServiceClients',
      'fetchEndpointServiceClients',
      'fetchEndpoint',
    ]),
    close(): void {
      this.$router.back();
    },
    removeAll(): void {
      this.confirmDeleteAll = true;
      this.serviceClientsToDelete = this.serviceClients;
    },
    remove(serviceClient: ServiceClient): void {
      this.confirmDeleteOne = true;
      this.serviceClientsToDelete = [serviceClient];
    },
    resetDeletionSettings(isDeleteAll: boolean): void {
      if (isDeleteAll) {
        this.confirmDeleteAll = false;
      } else {
        this.confirmDeleteOne = false;
      }
      this.deleting = false;
      this.serviceClientsToDelete = [];
    },
    toggleAddServiceClientsDialog(): void {
      this.adding = false;
      this.addSubjectsDialogVisible = !this.addSubjectsDialogVisible;
    },
    fetchData(everything = false): void {
      if (everything) {
        this.fetchEndpoint(this.id)
          .then((endpoint) => (this.endpoint = endpoint))
          .then((endpoint) =>
            this.fetchService(
              createFullServiceId(
                endpoint.client_id || '',
                endpoint.service_code,
              ),
            ),
          )
          .then((service) =>
            this.fetchServiceDescription(service.service_description_id),
          )
          .then((description) => this.fetchClient(description.client_id))
          .catch((error) => this.addError(error, true));
      }
      this.fetchEndpointServiceClients(this.id)
        .then((accessRights) => (this.serviceClients = accessRights))
        .catch((error) => this.addError(error));
    },
    doRemoveSelectedServiceClients(isDeleteAll: boolean): void {
      this.deleting = true;
      this.removeEndpointServiceClients(this.id, {
        items: this.serviceClientsToDelete,
      })
        .then(() => {
          this.addSuccessMessage('accessRights.removeSuccess');
          this.fetchData();
        })
        .catch((error) => this.addError(error))
        .finally(() => this.resetDeletionSettings(isDeleteAll));
    },
    doAddServiceClients(serviceClients: ServiceClient[]): void {
      this.adding = true;
      this.addEndpointServiceClients(this.id, {
        items: serviceClients,
      })
        .then((accessRights) => {
          this.addSuccessMessage('accessRights.addSubjectsSuccess');
          this.serviceClients = accessRights;
        })
        .catch((error) => this.addError(error))
        .finally(() => this.toggleAddServiceClientsDialog());
    },
  },
});
</script>

<style lang="scss" scoped></style>
