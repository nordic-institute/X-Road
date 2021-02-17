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
  <div class="xrd-tab-max-width main-wrap">
    <div class="pa-4">
      <xrd-sub-view-title
        :title="`${endpoint.method}${endpoint.path}`"
        @close="close"
      />
    </div>

    <div class="group-members-row px-4">
      <div class="row-title">{{ $t('accessRights.title') }}</div>
      <div class="row-buttons">
        <xrd-large-button
          v-if="canEdit"
          @click="removeAll()"
          outlined
          data-test="remove-all-access-rights"
          >{{ $t('action.removeAll') }}
        </xrd-large-button>
        <xrd-large-button
          v-if="canEdit"
          @click="toggleAddServiceClientsDialog()"
          outlined
          data-test="add-subjects-dialog"
          >{{ $t('accessRights.addServiceClients') }}
        </xrd-large-button>
      </div>
    </div>

    <table class="xrd-table mb-4">
      <thead>
        <tr>
          <th>{{ $t('accessRights.memberName') }}</th>
          <th>{{ $t('accessRights.id') }}</th>
          <th>{{ $t('accessRights.rightsGiven') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <template>
          <tr v-for="sc in serviceClients" :key="sc.id">
            <td class="identifier-wrap">{{ sc.name }}</td>
            <td class="identifier-wrap">{{ sc.id }}</td>
            <td>{{ sc.rights_given_at | formatDateTime }}</td>
            <td>
              <xrd-large-button
                v-if="canEdit"
                text
                :outlined="false"
                @click="remove(sc)"
                data-test="remove-access-right"
                >{{ $t('action.remove') }}
              </xrd-large-button>
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <!-- Confirm dialog remove all Access Right subjects -->
    <xrd-confirm-dialog
      :dialog="confirmDeleteAll"
      title="accessRights.removeAllTitle"
      text="accessRights.removeAllText"
      @cancel="resetDeletionSettings(true)"
      @accept="doRemoveSelectedServiceClients(true)"
    />

    <!-- Confirm dialog remove Access Right subject -->
    <xrd-confirm-dialog
      :dialog="confirmDeleteOne"
      title="accessRights.removeTitle"
      text="accessRights.removeText"
      @cancel="resetDeletionSettings(false)"
      @accept="doRemoveSelectedServiceClients(false)"
    />

    <!-- Add access right subjects dialog -->
    <accessRightsDialog
      :dialog="addSubjectsDialogVisible"
      :existingServiceClients="serviceClients"
      :clientId="clientId"
      title="accessRights.addServiceClientsTitle"
      @cancel="toggleAddServiceClientsDialog"
      @service-clients-added="doAddServiceClients"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { Endpoint, ServiceClient } from '@/openapi-types';
import AccessRightsDialog from '@/views/Service/AccessRightsDialog.vue';
import { encodePathParameter } from '@/util/api';
import { Permissions } from '@/global';

export default Vue.extend({
  name: 'EndpointAccessRights',
  components: {
    AccessRightsDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    clientId: {
      type: String,
      required: true,
    },
  },
  data: () => {
    return {
      endpoint: {} as Endpoint | Record<string, unknown>,
      serviceClients: [] as ServiceClient[],
      confirmDeleteAll: false as boolean,
      confirmDeleteOne: false as boolean,
      serviceClientsToDelete: [] as ServiceClient[],
      addSubjectsDialogVisible: false as boolean,
      serviceClientsToAdd: [] as ServiceClient[],
    };
  },
  computed: {
    canEdit(): boolean {
      return this.$store.getters.hasPermission(Permissions.EDIT_ENDPOINT_ACL);
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
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
      this.serviceClientsToDelete = [];
    },
    toggleAddServiceClientsDialog(): void {
      this.addSubjectsDialogVisible = !this.addSubjectsDialogVisible;
    },
    fetchData(): void {
      api
        .get<Endpoint>(`/endpoints/${encodePathParameter(this.id)}`)
        .then((endpoint) => {
          this.endpoint = endpoint.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        });
      api
        .get<ServiceClient[]>(
          `/endpoints/${encodePathParameter(this.id)}/service-clients`,
        )
        .then((accessRights) => {
          this.serviceClients = accessRights.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        });
    },
    doRemoveSelectedServiceClients(isDeleteAll: boolean): void {
      api
        .post(
          `/endpoints/${encodePathParameter(this.id)}/service-clients/delete`,
          {
            items: this.serviceClientsToDelete,
          },
        )
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'accessRights.removeSubjectsSuccess',
          );
          this.fetchData();
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        })
        .finally(() => {
          this.resetDeletionSettings(isDeleteAll);
        });
    },
    doAddServiceClients(serviceClients: ServiceClient[]): void {
      api
        .post<ServiceClient[]>(
          `/endpoints/${encodePathParameter(this.id)}/service-clients`,
          {
            items: serviceClients,
          },
        )
        .then((accessRights) => {
          this.$store.dispatch(
            'showSuccess',
            'accessRights.addSubjectsSuccess',
          );
          this.serviceClients = accessRights.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        })
        .finally(() => {
          this.toggleAddServiceClientsDialog();
        });
    },
  },
  created(): void {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;
}

.row-buttons {
  display: flex;
  * {
    margin-left: 20px;
  }
}

.row-title {
  width: 100%;
  justify-content: space-between;
  color: $XRoad-Black100;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
}
</style>
