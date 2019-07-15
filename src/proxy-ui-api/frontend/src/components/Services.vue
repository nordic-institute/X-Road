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
        <v-icon slot="append" small>fas fa-search</v-icon>
      </v-text-field>

      <div>
        <v-btn
          v-if="showAddButton"
          color="primary"
          @click="showAddRestDialog"
          outline
          round
          class="rounded-button elevation-0 rest-button"
        >{{$t('services.addRest')}}</v-btn>

        <v-btn
          v-if="showAddButton"
          color="primary"
          @click="showAddWsdlDialog"
          outline
          round
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
            class="clickable-link"
            v-if="canEditServiceDesc"
            @click="descriptionClick(serviceDesc)"
          >{{serviceDesc.type}} ({{serviceDesc.url}})</div>
          <div v-else>{{serviceDesc.type}} ({{serviceDesc.url}})</div>
        </template>

        <template v-slot:content>
          <div>
            <div v-if="serviceDesc.type.toLowerCase() === 'wsdl'" class="refresh-row">
              <div class="refresh-time">{{serviceDesc.refreshed_date | formatDateTime}}</div>
              <v-btn
                v-if="showRefreshButton"
                small
                outline
                round
                color="primary"
                class="xr-small-button xr-table-button refresh-button"
                @click="refreshWsdl(serviceDesc)"
              >{{$t('action.refresh')}}</v-btn>
            </div>

            <table class="xrd-table">
              <thead>
                <tr>
                  <th>{{$t('services.serviceCode')}}</th>
                  <th>{{$t('services.url')}}</th>
                  <th>{{$t('services.timeout')}}</th>
                  <th style="text-align:center">{{$t('services.accessRights')}}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="service in serviceDesc.services" v-bind:key="service.id">
                  <td class="service-code" @click="serviceClick(service)">{{service.code}}</td>
                  <td>
                    <v-icon small :color="getServiceIconColor(service)">{{getServiceIcon(service)}}</v-icon>
                    {{service.url}}
                  </td>
                  <td>{{service.timeout}}</td>
                  <td style="text-align:center">-</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </expandable>
    </template>

    <addWsdlDialog :dialog="addWsdlDialog" @save="wsdlSave" @cancel="wsdlCancel" />
    <addRestDialog :dialog="addRestDialog" @save="restSave" @cancel="restCancel" />
    <disableServiceDescDialog
      :dialog="disableDescDialog"
      @cancel="disableDescCancel"
      @save="disableDescSave"
      :subject="selectedServiceDesc"
      :subjectIndex="selectedIndex"
    />
  </div>
</template>

<script lang="ts">
// View for services tab
import Vue from 'vue';
import axios from 'axios';
import { Permissions, RouteName } from '@/global';
import Expandable from '@/components/Expandable.vue';
import AddWsdlDialog from '@/components/AddWsdlDialog.vue';
import AddRestDialog from '@/components/AddRestDialog.vue';
import DisableServiceDescDialog from '@/components/DisableServiceDescDialog.vue';

import _ from 'lodash';

export default Vue.extend({
  components: {
    Expandable,
    AddWsdlDialog,
    AddRestDialog,
    DisableServiceDescDialog,
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
    };
  },
  computed: {
    showAddButton(): boolean {
      return this.$store.getters.hasPermission(Permissions.ADD_WSDL);
    },
    showRefreshButton(): boolean {
      return this.$store.getters.hasPermission(Permissions.REFRESH_WSDL);
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
          return service.code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });
      });

      // Filter out services that don't include search term
      filtered.forEach((element) => {
        const filteredServices = element.services.filter((service: any) => {
          return service.code
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
      // TODO: will be implemented on later task
      /*
      this.$router.push({
        name: RouteName.Service,
        params: { serviceId: service.id },
      }); */
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

      axios
        .put(`/service-descriptions/${serviceDesc.id}/enable`)
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

      axios
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
      axios
        .post(`/clients/${this.id}/service-descriptions`, {
          url,
          type: 'WSDL',
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'services.wsdlAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });

      this.addWsdlDialog = false;
    },

    wsdlCancel(): void {
      this.addWsdlDialog = false;
    },

    restSave(rest: any): void {
      axios
        .post(`/clients/${this.id}/service-descriptions`, {
          url: rest.url,
          rest_service_code: rest.serviceCode,
          type: 'REST',
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'services.restAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });

      this.addRestDialog = false;
    },

    restCancel(): void {
      this.addRestDialog = false;
    },

    refreshWsdl(wsdl: any): void {
      // TODO: will be implemented on later task
      console.log('refresh wsdl');
    },

    getServiceIcon(service: any): string {
      switch (service.ssl_auth) {
        case undefined:
          return 'lock_open';
        case true:
          return 'lock';
        case false:
          return 'lock';
        default:
          return '';
      }
    },

    getServiceIconColor(service: any): string {
      switch (service.ssl_auth) {
        case undefined:
          return '';
        case true:
          return '#00e500';
        case false:
          return '#ffd200';
        default:
          return '';
      }
    },

    descClose(descId: string) {
      const index = this.expanded.findIndex((element: any) => {
        return element === descId;
      });

      if (index >= 0) {
        this.expanded.splice(index, 1);
      }
    },
    descOpen(descId: string) {
      const index = this.expanded.findIndex((element: any) => {
        return element === descId;
      });

      if (index === -1) {
        this.expanded.push(descId);
      }
    },
    isExpanded(descId: string) {
      return this.expanded.includes(descId);
    },

    fetchData(): void {
      axios
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
@import '../assets/tables';

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
</style>

