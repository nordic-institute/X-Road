<template>
  <div class="wrapper">
    <div class="search-row">
      <v-text-field
        v-model="search"
        :label="$t('services.service')"
        single-line
        hide-details
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>

      <div>
        <v-btn
          v-if="showAddButton"
          color="primary"
          @click="showAddRestDialog"
          outlined
          rounded
          class="rounded-button elevation-0 rest-button"
        >{{$t('services.addRest')}}</v-btn>

        <v-btn
          v-if="showAddButton"
          color="primary"
          :loading="addBusy"
          @click="showAddWsdlDialog"
          outlined
          rounded
          class="ma-0 rounded-button elevation-0"
        >{{$t('services.addWsdl')}}</v-btn>
      </div>
    </div>

    <div v-if="filtered && filtered.length < 1">{{$t('services.noMatches')}}</div>

    <template v-if="filtered">
      <expandable
        v-for="(serviceDesc, index) in filtered"
        v-bind:key="serviceDesc.id"
        class="expandable"
        @open="descOpen(serviceDesc.id)"
        @close="descClose(serviceDesc.id)"
        :isOpen="isExpanded(serviceDesc.id)"
      >
        <template v-slot:action>
          <v-switch
            class="switch"
            :input-value="!serviceDesc.disabled"
            @change="switchChanged($event, serviceDesc, index)"
            :key="componentKey"
            :disabled="!canDisable"
          ></v-switch>
        </template>

        <template v-slot:link>
          <div
            class="clickable-link service-description-header"
            v-if="canEditServiceDesc"
            @click="descriptionClick(serviceDesc)"
            data-test="service-description-header"
          >{{serviceDesc.type}} ({{serviceDesc.url}})</div>
          <div v-else>{{serviceDesc.type}} ({{serviceDesc.url}})</div>
        </template>

        <template v-slot:content>
          <div>
            <div class="refresh-row">
              <div
                class="refresh-time"
              >{{$t('services.lastRefreshed')}} {{serviceDesc.refreshed_at | formatDateTime}}</div>
              <v-btn
                v-if="showRefreshButton"
                small
                outlined
                rounded
                :loading="refreshBusy"
                color="primary"
                class="xrd-small-button xrd-table-button refresh-button"
                @click="refresh(serviceDesc)"
              >{{$t('action.refresh')}}</v-btn>
            </div>

            <table class="xrd-table">
              <thead>
                <tr>
                  <th>{{$t('services.serviceCode')}}</th>
                  <th>{{$t('services.url')}}</th>
                  <th>{{$t('services.timeout')}}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="service in serviceDesc.services" v-bind:key="service.id">
                  <td class="service-code" @click="serviceClick(service)" data-test="service-link">{{service.service_code}}</td>
                  <td class="service-url" data-test="service-url">
                    <serviceIcon :service="service" />
                    {{service.url}}
                  </td>
                  <td>{{service.timeout}}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </expandable>
    </template>

    <addWsdlDialog :dialog="addWsdlDialog" @save="wsdlSave" @cancel="cancelAddWsdl" />
    <addRestDialog :dialog="addRestDialog" @save="restSave" :clientId="this.id" @cancel="cancelAddRest" />
    <disableServiceDescDialog
      :dialog="disableDescDialog"
      @cancel="disableDescCancel"
      @save="disableDescSave"
      :subject="selectedServiceDesc"
      :subjectIndex="selectedIndex"
    />
    <!-- Accept "save WSDL" warnings -->
    <warningDialog
      :dialog="saveWarningDialog"
      :warnings="warningInfo"
      @cancel="cancelSaveWarning()"
      @accept="acceptSaveWarning()"
    />
    <!-- Accept "refresh WSDL" warnings -->
    <warningDialog
      :dialog="refreshWarningDialog"
      :warnings="warningInfo"
      @cancel="cancelRefresh()"
      @accept="acceptRefreshWarning()"
    />
  </div>
</template>

<script lang="ts">
// View for services tab
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import * as api from '@/util/api';
import Expandable from '@/components/ui/Expandable.vue';
import AddWsdlDialog from './AddWsdlDialog.vue';
import AddRestDialog from './AddRestDialog.vue';
import DisableServiceDescDialog from './DisableServiceDescDialog.vue';
import WarningDialog from '@/components/service/WarningDialog.vue';
import ServiceIcon from '@/components/ui/ServiceIcon.vue';

import _ from 'lodash';
import {ServiceDescription} from '@/types';

export default Vue.extend({
  components: {
    Expandable,
    AddWsdlDialog,
    AddRestDialog,
    DisableServiceDescDialog,
    WarningDialog,
    ServiceIcon,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      search: '',
      addWsdlDialog: false,
      addRestDialog: false,
      disableDescDialog: false,
      selectedServiceDesc: undefined,
      selectedIndex: -1,
      componentKey: 0,
      expanded: [] as string[],
      serviceDescriptions: [] as any[],
      warningInfo: undefined,
      saveWarningDialog: false,
      refreshWarningDialog: false,
      url: '',
      refreshId: '',
      addBusy: false,
      refreshBusy: false,
    };
  },
  computed: {
    showAddButton(): boolean {
      return this.$store.getters.hasPermission(Permissions.ADD_WSDL);
    },
    showRefreshButton(): boolean {
      return this.$store.getters.hasPermission(Permissions.REFRESH_WSDL) ||
          this.$store.getters.hasPermission(Permissions.REFRESH_OPENAPI3) ||
          this.$store.getters.hasPermission(Permissions.REFRESH_REST);
    },
    canEditServiceDesc(): boolean {
      return this.$store.getters.hasPermission(Permissions.EDIT_WSDL);
    },
    canDisable(): boolean {
      return this.$store.getters.hasPermission(Permissions.ENABLE_DISABLE_WSDL);
    },
    filtered(): any {
      if (!this.serviceDescriptions || this.serviceDescriptions.length === 0) {
        return [];
      }

      // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
      const arr = _.cloneDeep(this.serviceDescriptions).sort((a, b) => {
        if (a.id < b.id) {
          return -1;
        }
        if (a.id > b.id) {
          return 1;
        }

        // equal id:s. (should not happen)
        return 0;
      });

      if (!this.search) {
        return arr;
      }

      // Clean the search string
      const mysearch = this.search.toString().toLowerCase();
      if (mysearch.trim() === '') {
        return arr;
      }

      // Filter out service deascriptions that don't include search term
      const filtered = arr.filter((element: any) => {
        return element.services.find((service: any) => {
          return service.service_code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });
      });

      // Filter out services that don't include search term
      filtered.forEach((element) => {
        const filteredServices = element.services.filter((service: any) => {
          return service.service_code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });

        element.services = filteredServices;
      });

      return filtered;
    },
  },
  methods: {
    descriptionClick(desc: any): void {
      this.$router.push({
        name: RouteName.ServiceDescriptionDetails,
        params: { id: desc.id },
      });
    },
    serviceClick(service: any): void {
      this.$router.push({
        name: RouteName.Service,
        params: { serviceId: service.id, clientId: this.id },
      });
    },
    switchChanged(event: any, serviceDesc: any, index: number): void {
      if (serviceDesc.disabled === false) {
        // If user wants to disable service description:
        // - cancel the switch change
        // - show confirmation dialog instead
        this.selectedServiceDesc = serviceDesc;
        this.selectedIndex = index;
        this.disableDescDialog = true;
        this.forceUpdateSwitch(index, false);
        return;
      }

      api
        .put(`/service-descriptions/${serviceDesc.id}/enable`, {})
        .then((res) => {
          this.$bus.$emit('show-success', 'services.enableSuccess');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          // Whatever happens, refresh the data
          this.fetchData();
        });
    },

    disableDescCancel(subject: any, index: number): void {
      // User cancels the change from dialog. Switch must be returned to original position.
      this.disableDescDialog = false;
      this.forceUpdateSwitch(index, false);
    },

    disableDescSave(subject: any, index: number, notice: string): void {
      this.disableDescDialog = false;
      this.forceUpdateSwitch(index, true);

      api
        .put(`/service-descriptions/${subject.id}/disable`, {
          disabled_notice: notice,
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'services.disableSuccess');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          this.fetchData();
        });
    },

    forceUpdateSwitch(index: number, value: boolean): void {
      // "force updating" the switch is needed for smooth
      this.filtered[index].disabled = value;
      this.componentKey += 1;
    },

    showAddRestDialog(): void {
      this.addRestDialog = true;
    },

    showAddWsdlDialog(): void {
      this.addWsdlDialog = true;
    },

    wsdlSave(url: string): void {
      this.url = url;
      this.addBusy = true;
      api
        .post(`/clients/${this.id}/service-descriptions`, {
          url,
          type: 'WSDL',
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'services.wsdlAdded');
          this.addBusy = false;
          this.fetchData();
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.saveWarningDialog = true;
          } else if (
            error.response.data.error.code === 'service_already_exists'
          ) {
            this.$bus.$emit('show-error', 'service already exists');
            this.addBusy = false;
          } else {
            this.$bus.$emit('show-error', error.message);
            this.addBusy = false;
          }
        });

      this.addWsdlDialog = false;
    },

    acceptSaveWarning(): void {
      api
        .post(`/clients/${this.id}/service-descriptions`, {
          url: this.url,
          type: 'WSDL',
          ignore_warnings: true,
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'services.wsdlAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          this.fetchData();
          this.addBusy = false;
        });

      this.saveWarningDialog = false;
    },

    cancelSaveWarning(): void {
      this.addBusy = false;
      this.saveWarningDialog = false;
    },

    cancelAddWsdl(): void {
      this.addWsdlDialog = false;
    },

    restSave(): void {
      this.fetchData();
      this.addRestDialog = false;
    },

    cancelAddRest(): void {
      this.addRestDialog = false;
    },

    refresh(service: ServiceDescription): void {
      this.refreshBusy = true;
      api
        .put(`/service-descriptions/${service.id}/refresh`, {ignoreWarnings: true})
        .then((res) => {
          this.$bus.$emit('show-success', 'services.refreshed');
          this.fetchData();
          this.refreshBusy = false;
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.refreshWarningDialog = true;
            this.refreshId = service.id;
          } else {
            this.$bus.$emit('show-error', error.message);
            this.refreshBusy = false;
            this.fetchData();
          }
        });
    },

    acceptRefreshWarning(): void {
      api
        .put(`/service-descriptions/${this.refreshId}/refresh`, {
          ignore_warnings: true,
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'services.refreshed');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          this.refreshBusy = false;
          this.fetchData();
        });

      this.refreshWarningDialog = false;
    },

    cancelRefresh(): void {
      this.refreshBusy = false;
      this.refreshWarningDialog = false;
    },

    descClose(tokenId: string) {
      this.$store.dispatch('hideDesc', tokenId);
    },
    descOpen(tokenId: string) {
      this.$store.dispatch('expandDesc', tokenId);
    },
    isExpanded(tokenId: string) {
      return this.$store.getters.descExpanded(tokenId);
    },

    fetchData(): void {
      api
        .get(`/clients/${this.id}/service-descriptions`)
        .then((res) => {
          this.serviceDescriptions = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },

  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.wrapper {
  margin-top: 20px;
}

.search-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-top: 40px;
  margin-bottom: 24px;
}

.rest-button {
  margin: 0;
  margin-right: 20px;
}

.search-input {
  max-width: 300px;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.refresh-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: flex-end;
  width: 100%;
  margin-top: 24px;
}

.refresh-time {
  margin-right: 28px;
}

.expandable {
  margin-bottom: 10px;
}

.service-code {
  cursor: pointer;
  text-decoration: underline;
}

.service-url {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 400px;
}

.service-description-header {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 700px;
}

</style>

