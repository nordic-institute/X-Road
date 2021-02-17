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
  <div class="xrd-tab-max-width xrd-view-common main-wrap">
    <xrd-sub-view-title :title="serviceClientId" @close="close" class="pa-4" />
    <v-card flat>
      <table class="xrd-table service-client-margin">
        <thead>
          <tr>
            <th>{{ $t('serviceClients.name') }}</th>
            <th>{{ $t('serviceClients.id') }}</th>
          </tr>
        </thead>
        <tr>
          <td class="identifier-wrap">{{ serviceClient.name }}</td>
          <td class="identifier-wrap">{{ serviceClient.id }}</td>
        </tr>
      </table>
    </v-card>

    <div class="group-members-row px-4">
      <div class="row-title">{{ $t('serviceClients.accessRights') }}</div>
      <div class="row-buttons">
        <xrd-large-button
          @click="showConfirmDeleteAll = true"
          outlined
          data-test="remove-all-access-rights"
          v-if="canEdit && serviceClientAccessRights.length > 0"
          >{{ $t('serviceClients.removeAll') }}
        </xrd-large-button>
        <xrd-large-button
          v-if="canEdit"
          @click="showAddServiceDialog()"
          outlined
          data-test="add-subjects-dialog"
          >{{ $t('serviceClients.addService') }}
        </xrd-large-button>
      </div>
    </div>

    <table
      class="xrd-table service-client-margin"
      v-if="serviceClientAccessRights.length > 0"
    >
      <thead>
        <tr>
          <th>{{ $t('serviceClients.serviceCode') }}</th>
          <th>{{ $t('serviceClients.title') }}</th>
          <th>{{ $t('serviceClients.accessRightsGiven') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="accessRight in keyedServiceClientAccessRights()"
          :key="accessRight.uiKey"
        >
          <td class="identifier-wrap">{{ accessRight.service_code }}</td>
          <td class="identifier-wrap">{{ accessRight.service_title }}</td>
          <td>{{ accessRight.rights_given_at }}</td>
          <td>
            <div class="button-wrap">
              <xrd-large-button
                v-if="canEdit"
                text
                :outlined="false"
                class="mr-4"
                data-test="access-right-remove"
                @click="remove(accessRight)"
                >{{ $t('action.remove') }}</xrd-large-button
              >
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else class="pa-6">
      {{ $t('serviceClients.noAccessRights') }}
    </p>

    <div class="footer-buttons-wrap">
      <xrd-large-button @click="close()" data-test="close">{{
        $t('action.close')
      }}</xrd-large-button>
    </div>

    <AddServiceClientServiceDialog
      v-if="isAddServiceDialogVisible"
      :dialog="isAddServiceDialogVisible"
      :serviceCandidates="serviceCandidates()"
      @save="addService"
      @cancel="hideAddService"
    >
    </AddServiceClientServiceDialog>

    <!-- Confirm dialog delete group -->
    <xrd-confirm-dialog
      :dialog="showConfirmDeleteAll"
      title="serviceClients.removeAllTitle"
      text="serviceClients.removeAllText"
      @cancel="showConfirmDeleteAll = false"
      @accept="removeAll()"
    />

    <xrd-confirm-dialog
      :dialog="showConfirmDeleteOne"
      title="serviceClients.removeOneTitle"
      text="serviceClients.removeOneText"
      @cancel="resetDeletionSettings()"
      @accept="doRemoveAccessRight()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import {
  AccessRight,
  AccessRights,
  ServiceClient,
  ServiceDescription,
} from '@/openapi-types';
import AddServiceClientServiceDialog from '@/views/Clients/ServiceClients/AddServiceClientServiceDialog.vue';
import { serviceCandidatesForServiceClient } from '@/util/serviceClientUtils';

import { ServiceCandidate } from '@/ui-types';
import { sortAccessRightsByServiceCode } from '@/util/sorting';
import { encodePathParameter } from '@/util/api';
import { Permissions } from '@/global';

interface UiAccessRight extends AccessRight {
  uiKey: number;
}

export default Vue.extend({
  components: {
    AddServiceClientServiceDialog,
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
  computed: {
    canEdit(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_ACL_SUBJECT_OPEN_SERVICES,
      );
    },
  },
  data() {
    return {
      serviceClientAccessRights: [] as AccessRight[],
      serviceClient: {} as ServiceClient,
      accessRightToDelete: null as AccessRight | null,
      isAddServiceDialogVisible: false as boolean,
      clientServiceDescriptions: [] as ServiceDescription[],
      showConfirmDeleteAll: false as boolean,
      showConfirmDeleteOne: false as boolean,
    };
  },
  methods: {
    fetchData(): void {
      this.fetchAccessRights();
      this.fetchServiceDescriptions();
      api
        .get<ServiceClient>(
          `/clients/${this.id}/service-clients/${this.serviceClientId}`,
        )
        .then((response) => (this.serviceClient = response.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    fetchServiceDescriptions(): void {
      api
        .get<ServiceDescription[]>(
          `/clients/${encodePathParameter(this.id)}/service-descriptions`,
        )
        .then((response) => {
          this.clientServiceDescriptions = response.data;
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
    fetchAccessRights(): void {
      api
        .get<AccessRight[]>(
          `/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights`,
        )
        .then((response) => {
          this.serviceClientAccessRights = sortAccessRightsByServiceCode(
            response.data,
          );
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
    close(): void {
      this.$router.go(-1);
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
      api
        .post(
          `/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights/delete`,
          { items: [{ service_code: this.accessRightToDelete?.service_code }] },
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'serviceClients.removeSuccess');
          if (this.serviceClientAccessRights.length === 1) {
            this.serviceClientAccessRights = [];
          } else {
            this.fetchAccessRights();
          }
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => {
          this.showConfirmDeleteOne = false;
          this.accessRightToDelete = null;
        });
    },
    addService(accessRights: AccessRight[]): void {
      this.hideAddService();
      const accessRightsObject: AccessRights = { items: accessRights };
      api
        .post(
          `/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights`,
          accessRightsObject,
        )
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'serviceClients.addServiceClientAccessRightSuccess',
          );
          this.fetchAccessRights();
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
    hideAddService(): void {
      this.isAddServiceDialogVisible = false;
    },
    showAddServiceDialog(): void {
      this.isAddServiceDialogVisible = true;
    },
    removeAll(): void {
      this.showConfirmDeleteAll = false;

      api
        .post(
          `/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights/delete`,
          {
            items: this.serviceClientAccessRights.map((item: AccessRight) => ({
              service_code: item.service_code,
            })),
          },
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'serviceClients.removeSuccess');
          this.serviceClientAccessRights = [];
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
    serviceCandidates(): ServiceCandidate[] {
      return serviceCandidatesForServiceClient(
        this.clientServiceDescriptions,
        this.serviceClientAccessRights,
      );
    },

    keyedServiceClientAccessRights(): UiAccessRight[] {
      return this.serviceClientAccessRights.map(
        (sca: AccessRight, index: number) => {
          return { ...sca, uiKey: index };
        },
      ) as UiAccessRight[];
    },
  },
  created(): void {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.main-wrap {
  background-color: white;
  margin-top: 20px;
  border-radius: 4px;
  box-shadow: $XRoad-DefaultShadow;
}

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;

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
}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.service-client-margin {
  margin-top: 40px;
}
</style>
