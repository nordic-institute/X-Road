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
  <div class="wrapper">
    <div class="search-row">
      <v-text-field
        v-model="search"
        :label="$t('services.service')"
        single-line
        hide-details
        autofocus
        data-test="search-service"
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>

      <div>
        <large-button
          v-if="showAddButton"
          color="primary"
          :loading="addRestBusy"
          @click="showAddRestDialog"
          outlined
          data-test="add-rest-button"
          class="rest-button"
          ><v-icon class="mr-1">mdi-plus-circle</v-icon
          >{{ $t('services.addRest') }}</large-button
        >

        <large-button
          v-if="showAddButton"
          :loading="addWsdlBusy"
          @click="showAddWsdlDialog"
          data-test="add-wsdl-button"
          class="ma-0"
          ><v-icon class="mr-1">mdi-plus-circle</v-icon
          >{{ $t('services.addWsdl') }}</large-button
        >
      </div>
    </div>

    <div v-if="filtered && filtered.length < 1">
      {{ $t('services.noMatches') }}
    </div>

    <template v-if="filtered">
      <expandable
        v-for="(serviceDesc, index) in filtered"
        v-bind:key="serviceDesc.id"
        class="expandable"
        @open="descOpen(serviceDesc.id)"
        @close="descClose(serviceDesc.id)"
        :isOpen="isExpanded(serviceDesc.id)"
        data-test="service-description-accordion"
      >
        <template v-slot:action>
          <v-switch
            class="switch"
            :input-value="!serviceDesc.disabled"
            @change="switchChanged($event, serviceDesc, index)"
            :key="componentKey"
            :disabled="!canDisable"
            data-test="service-description-enable-disable"
          ></v-switch>
        </template>

        <template v-slot:link>
          <div
            class="clickable-link service-description-header"
            v-if="canEditServiceDesc"
            @click="descriptionClick(serviceDesc)"
            data-test="service-description-header"
          >
            {{ serviceDesc.type }} ({{ serviceDesc.url }})
          </div>
          <div v-else>{{ serviceDesc.type }} ({{ serviceDesc.url }})</div>
        </template>

        <template v-slot:content>
          <div>
            <div class="refresh-row">
              <div class="refresh-time">
                {{ $t('services.lastRefreshed') }}
                {{ serviceDesc.refreshed_at | formatDateTime }}
              </div>
              <large-button
                v-if="showRefreshButton(serviceDesc.type)"
                :key="refreshButtonComponentKey"
                :outlined="false"
                text
                :loading="refreshBusy[serviceDesc.id]"
                color="primary"
                class="xrd-table-button"
                @click="refresh(serviceDesc)"
                data-test="refresh-button"
                >{{ $t('action.refresh') }}</large-button
              >
            </div>

            <table class="xrd-table">
              <thead>
                <tr>
                  <th>{{ $t('services.serviceCode') }}</th>
                  <th>{{ $t('services.url') }}</th>
                  <th>{{ $t('services.timeout') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="service in serviceDesc.services"
                  v-bind:key="service.id"
                >
                  <td
                    class="clickable-link"
                    @click="serviceClick(serviceDesc, service)"
                    data-test="service-link"
                  >
                    {{ service.full_service_code }}
                  </td>
                  <td class="service-url" data-test="service-url">
                    <serviceIcon :service="service" />
                    {{ service.url }}
                  </td>
                  <td>{{ service.timeout }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </expandable>
    </template>

    <addWsdlDialog
      :dialog="addWsdlDialog"
      @save="wsdlSave"
      @cancel="cancelAddWsdl"
    />
    <addRestDialog
      :dialog="addRestDialog"
      @save="restSave"
      @cancel="cancelAddRest"
    />
    <disableServiceDescDialog
      :dialog="disableDescDialog"
      @cancel="disableDescCancel"
      @save="disableDescSave"
      :subject="selectedServiceDesc"
      :subjectIndex="selectedIndex"
    />
    <!-- Accept "save WSDL" warnings -->
    <warningDialog
      :dialog="saveWsdlWarningDialog"
      :warnings="warningInfo"
      :loading="saveWsdlLoading"
      @cancel="cancelSaveWsdlWarning()"
      @accept="acceptSaveWsdlWarning()"
    />
    <!-- Accept "save REST/OPENAPI3" warnings -->
    <warningDialog
      :dialog="saveRestWarningDialog"
      :warnings="warningInfo"
      :loading="saveRestLoading"
      @cancel="cancelSaveRestWarning()"
      @accept="acceptSaveRestWarning()"
    />
    <!-- Accept "refresh" warnings. -->
    <!-- Covers WSDL, OPENAPI3 and REST. -->
    <warningDialog
      :dialog="refreshWarningDialog"
      :warnings="warningInfo"
      :loading="refreshLoading"
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
import AddWsdlDialog from './AddWsdlDialog.vue';
import AddRestDialog from './AddRestDialog.vue';
import DisableServiceDescDialog from './DisableServiceDescDialog.vue';
import WarningDialog from '@/components/service/WarningDialog.vue';
import ServiceIcon from '@/components/ui/ServiceIcon.vue';

import { Service, ServiceDescription } from '@/openapi-types';
import { ServiceTypeEnum } from '@/domain';
import { Prop } from 'vue/types/options';
import { sortServiceDescriptionServices } from '@/util/sorting';
import { deepClone } from '@/util/helpers';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    AddWsdlDialog,
    AddRestDialog,
    DisableServiceDescDialog,
    WarningDialog,
    ServiceIcon,
  },
  props: {
    id: {
      type: String as Prop<string>,
      required: true,
    },
  },
  data() {
    return {
      search: '' as string,
      addWsdlDialog: false as boolean,
      addRestDialog: false as boolean,
      disableDescDialog: false as boolean,
      selectedServiceDesc: undefined as undefined | ServiceDescription,
      selectedIndex: -1 as number,
      componentKey: 0 as number,
      expanded: [] as string[],
      serviceDescriptions: [] as ServiceDescription[],
      warningInfo: [] as string[],
      saveWsdlWarningDialog: false as boolean,
      saveRestWarningDialog: false as boolean,
      refreshWarningDialog: false as boolean,
      url: '' as string,
      serviceType: '' as string,
      serviceCode: '' as string,
      refreshId: '' as string,
      addWsdlBusy: false as boolean,
      addRestBusy: false as boolean,
      refreshBusy: {} as { [key: string]: boolean },
      refreshButtonComponentKey: 0 as number,
      serviceTypeEnum: ServiceTypeEnum,
      saveWsdlLoading: false as boolean,
      saveRestLoading: false as boolean,
      refreshLoading: false as boolean,
    };
  },
  computed: {
    showAddButton(): boolean {
      return this.$store.getters.hasPermission(Permissions.ADD_WSDL);
    },
    canEditServiceDesc(): boolean {
      return this.$store.getters.hasPermission(Permissions.EDIT_WSDL);
    },
    canDisable(): boolean {
      return this.$store.getters.hasPermission(Permissions.ENABLE_DISABLE_WSDL);
    },
    filtered(): ServiceDescription[] {
      if (!this.serviceDescriptions || this.serviceDescriptions.length === 0) {
        return [];
      }

      // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
      const arr = deepClone(this.serviceDescriptions).sort((a, b) => {
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
      const filtered = arr.filter((element) => {
        return element.services.find((service) => {
          return service.service_code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });
      });

      // Filter out services that don't include search term
      filtered.forEach((element) => {
        element.services = element.services.filter((service) => {
          return service.service_code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });
      });

      return filtered;
    },
  },
  methods: {
    showRefreshButton(serviceDescriptionType: string): boolean {
      if (serviceDescriptionType === this.serviceTypeEnum.WSDL) {
        return this.$store.getters.hasPermission(Permissions.REFRESH_WSDL);
      } else if (serviceDescriptionType === this.serviceTypeEnum.OPENAPI3) {
        return this.$store.getters.hasPermission(Permissions.REFRESH_OPENAPI3);
      }
      return false;
    },
    descriptionClick(desc: ServiceDescription): void {
      this.$router.push({
        name: RouteName.ServiceDescriptionDetails,
        params: { id: desc.id },
      });
    },
    serviceClick(
      serviceDescription: ServiceDescription,
      service: Service,
    ): void {
      this.$router.push({
        name: RouteName.Service,
        params: { serviceId: service.id, clientId: this.id },
        query: { descriptionType: serviceDescription.type },
      });
    },
    switchChanged(
      event: unknown,
      serviceDesc: ServiceDescription,
      index: number,
    ): void {
      if (!serviceDesc.disabled) {
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
        .put(
          `/service-descriptions/${encodePathParameter(serviceDesc.id)}/enable`,
          {},
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'services.enableSuccess');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          // Whatever happens, refresh the data
          this.fetchData();
        });
    },

    disableDescCancel(
      subject: ServiceDescription | undefined,
      index: number,
    ): void {
      // User cancels the change from dialog. Switch must be returned to original position.
      this.disableDescDialog = false;
      this.forceUpdateSwitch(index, false);
    },

    disableDescSave(
      subject: ServiceDescription | undefined,
      index: number,
      notice: string,
    ): void {
      this.disableDescDialog = false;
      this.forceUpdateSwitch(index, true);
      if (subject) {
        api
          .put(
            `/service-descriptions/${encodePathParameter(subject.id)}/disable`,
            {
              disabled_notice: notice,
            },
          )
          .then(() => {
            this.$store.dispatch('showSuccess', 'services.disableSuccess');
          })
          .catch((error) => {
            this.$store.dispatch('showError', error);
          })
          .finally(() => {
            this.fetchData();
          });
      }
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
      this.addWsdlBusy = true;
      api
        .post(`/clients/${encodePathParameter(this.id)}/service-descriptions`, {
          url,
          type: this.serviceTypeEnum.WSDL,
        })
        .then(() => {
          this.$store.dispatch('showSuccess', 'services.wsdlAdded');
          this.addWsdlBusy = false;
          this.fetchData();
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.saveWsdlWarningDialog = true;
          } else {
            this.$store.dispatch('showError', error);
            this.addWsdlBusy = false;
          }
        });

      this.addWsdlDialog = false;
    },

    acceptSaveWsdlWarning(): void {
      this.saveWsdlLoading = true;
      api
        .post(`/clients/${encodePathParameter(this.id)}/service-descriptions`, {
          url: this.url,
          type: this.serviceTypeEnum.WSDL,
          ignore_warnings: true,
        })
        .then(() => {
          this.$store.dispatch('showSuccess', 'services.wsdlAdded');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.fetchData();
          this.addWsdlBusy = false;
          this.saveWsdlLoading = false;
          this.saveWsdlWarningDialog = false;
        });
    },

    cancelSaveWsdlWarning(): void {
      this.addWsdlBusy = false;
      this.saveWsdlLoading = false;
      this.saveWsdlWarningDialog = false;
    },

    cancelAddWsdl(): void {
      this.addWsdlDialog = false;
    },

    restSave(serviceType: string, url: string, serviceCode: string): void {
      this.serviceType = serviceType;
      this.url = url;
      this.serviceCode = serviceCode;
      this.addRestBusy = true;
      api
        .post(`/clients/${encodePathParameter(this.id)}/service-descriptions`, {
          url: this.url,
          rest_service_code: this.serviceCode,
          type: this.serviceType,
        })
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            this.serviceType === 'OPENAPI3'
              ? 'services.openApi3Added'
              : 'services.restAdded',
          );
          this.addRestBusy = false;
          this.fetchData();
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.saveRestWarningDialog = true;
          } else {
            this.$store.dispatch('showError', error);
            this.addRestBusy = false;
          }
        });

      this.addRestDialog = false;
    },

    acceptSaveRestWarning(): void {
      this.saveRestLoading = true;
      api
        .post(`/clients/${encodePathParameter(this.id)}/service-descriptions`, {
          url: this.url,
          rest_service_code: this.serviceCode,
          type: this.serviceType,
          ignore_warnings: true,
        })
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            this.serviceType === 'OPENAPI3'
              ? 'services.openApi3Added'
              : 'services.restAdded',
          );
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.fetchData();
          this.addRestBusy = false;
          this.saveRestLoading = false;
          this.saveRestWarningDialog = false;
        });
    },

    cancelSaveRestWarning(): void {
      this.addRestBusy = false;
      this.saveRestLoading = false;
      this.saveRestWarningDialog = false;
    },

    cancelAddRest(): void {
      this.addRestDialog = false;
    },

    refresh(serviceDescription: ServiceDescription): void {
      this.refreshBusy[serviceDescription.id] = true;
      this.refreshButtonComponentKey += 1; // update component key to make spinner work

      api
        .put(
          `/service-descriptions/${encodePathParameter(
            serviceDescription.id,
          )}/refresh`,
          {
            ignore_warnings: false,
          },
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'services.refreshed');
          this.fetchData();
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.refreshWarningDialog = true;
            this.refreshId = serviceDescription.id;
          } else {
            this.$store.dispatch('showError', error);
            this.fetchData();
          }
        })
        .finally(() => {
          this.refreshBusy[serviceDescription.id] = false;
        });
    },

    acceptRefreshWarning(): void {
      this.refreshLoading = true;
      api
        .put(
          `/service-descriptions/${encodePathParameter(
            this.refreshId,
          )}/refresh`,
          {
            ignore_warnings: true,
          },
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'services.refreshed');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.fetchData();
          this.refreshLoading = false;
          this.refreshWarningDialog = false;
        });
    },

    cancelRefresh(): void {
      this.refreshLoading = false;
      this.refreshWarningDialog = false;
      this.refreshLoading = false;
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
        .get<ServiceDescription[]>(
          `/clients/${encodePathParameter(this.id)}/service-descriptions`,
        )
        .then((res) => {
          const serviceDescriptions: ServiceDescription[] = res.data;
          this.serviceDescriptions = serviceDescriptions.map(
            sortServiceDescriptionServices,
          );
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
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
  color: $XRoad-Link;
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
