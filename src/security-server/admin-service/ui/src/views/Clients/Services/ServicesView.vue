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
  <XrdSubView>
    <template #header>
      <XrdRoundedSearchField v-model="search" data-test="search-service" autofocus :label="$t('services.service')" />
      <v-spacer />
      <XrdBtn
        v-if="showAddRestButton"
        data-test="add-rest-button"
        text="services.addRest"
        prepend-icon="add_circle"
        @click="showAddRestDialog"
      />

      <XrdBtn
        v-if="showAddWSDLButton"
        data-test="add-wsdl-button"
        class="ml-2"
        text="services.addWsdl"
        prepend-icon="add_circle"
        @click="showAddWsdlDialog"
      />
    </template>

    <div class="wrapper">
      <XrdEmptyPlaceholder
        :data="filtered"
        :filtered="search.length > 0"
        :loading="loading"
        :no-items-text="$t('noData.noServices')"
        skeleton-type="table-heading"
      />

      <template v-if="filtered">
        <XrdExpandable
          v-for="(serviceDesc, index) in filtered"
          :id="`service-description-${serviceDesc.id}`"
          :key="serviceDesc.id"
          data-test="service-description-accordion"
          class="mt-6 border"
          :is-open="isExpanded(serviceDesc.id)"
          @open="$event ? descOpen(serviceDesc.id) : descClose(serviceDesc.id)"
        >
          <template #action>
            <ServiceStatusChip :enabled="!serviceDesc.disabled" />
            <XrdBtn
              v-if="canDisable"
              data-test="service-description-enable-disable"
              class="ml-2"
              variant="text"
              :prepend-icon="serviceDesc.disabled ? 'check_circle' : 'cancel'"
              :text="serviceDesc.disabled ? 'action.enable' : 'action.disable'"
              :loading="enabling[serviceDesc.id]"
              @click="switchChanged(serviceDesc, index)"
            />
          </template>

          <template #link="{ toggle, opened }">
            <span
              data-test="service-description-header-url"
              class="font-weight-medium"
              :class="{ 'on-surface': opened, 'on-surface-variant': !opened }"
              @click="toggle"
            >
              {{ serviceDesc.type }}
              <span class="font-weight-regular">
                {{ $t('common.inParenthesis', [serviceDesc.url]) }}
              </span>
            </span>
            <v-icon
              v-if="canEditServiceDesc(serviceDesc)"
              data-test="service-description-header-edit"
              class="ml-2"
              icon="edit"
              size="16"
              color="primary"
              @click="descriptionClick(serviceDesc)"
            />
          </template>

          <template #content>
            <div class="refresh-row d-flex flex-row align-center">
              <v-spacer />
              <div class="refresh-time body-regular">
                {{ $t('services.lastRefreshed') }}
                <XrdDateTime :value="serviceDesc.refreshed_at" />
              </div>
              <XrdBtn
                v-if="showRefreshButton(serviceDesc.type)"
                data-test="refresh-button"
                class="ml-2"
                variant="text"
                color="tertiary"
                text="action.refresh"
                prepend-icon="cached"
                :loading="refreshBusy[serviceDesc.id]"
                @click="refresh(serviceDesc)"
              />
            </div>

            <v-data-table
              data-test="services-table"
              class="xrd"
              hide-default-footer
              :items="serviceDesc.services"
              :headers="headers"
              :items-per-page="-1"
            >
              <template #item.full_service_code="{ value, item }">
                <XrdLabelWithIcon icon="settings_system_daydream" clickable semi-bold :label="value" @navigate="serviceClick(item)" />
              </template>
              <template #item.url="{ value, item }">
                <ServiceIcon :service="item" />
                {{ value }}
              </template>
            </v-data-table>
          </template>
        </XrdExpandable>
      </template>

      <AddWsdlDialog v-if="addWsdlDialog" :client-id="id" @save="wsdlSave" @cancel="cancelAddWsdl" />
      <AddRestDialog v-if="addRestDialog" :client-id="id" @save="restSave" @cancel="cancelAddRest" />
      <DisableServiceDescDialog
        v-if="disableDescDialog"
        :subject="selectedServiceDesc"
        :subject-index="selectedIndex"
        @cancel="disableDescCancel"
        @save="disableDescSave"
      />
      <!-- Accept "refresh" warnings. -->
      <!-- Covers WSDL, OPENAPI3 and REST. -->
      <ServiceWarningDialog
        v-if="refreshWarningDialog"
        :warnings="warningInfo"
        :loading="refreshLoading"
        @cancel="cancelRefresh()"
        @accept="acceptRefreshWarning()"
      />
    </div>
  </XrdSubView>
</template>

<script lang="ts">
// View for services tab
import { defineComponent, PropType } from 'vue';
import { Permissions, RouteName } from '@/global';
import AddWsdlDialog from './AddWsdlDialog.vue';
import AddRestDialog from './AddRestDialog.vue';
import DisableServiceDescDialog from './DisableServiceDescDialog.vue';
import ServiceWarningDialog from '@/components/service/ServiceWarningDialog.vue';
import ServiceIcon from '@/components/ui/ServiceIcon.vue';
import ServiceStatusChip from './ServiceStatusChip.vue';

import { CodeWithDetails, Service, ServiceDescription, ServiceType } from '@/openapi-types';
import { ServiceTypeEnum } from '@/domain';
import { deepClone } from '@/util/helpers';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { XrdDateTime, XrdSubView, XrdBtn, XrdLabelWithIcon, useNotifications, XrdExpandable, XrdEmptyPlaceholder } from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';
import { useGoTo } from 'vuetify';

export default defineComponent({
  components: {
    XrdBtn,
    AddWsdlDialog,
    AddRestDialog,
    DisableServiceDescDialog,
    ServiceWarningDialog,
    ServiceIcon,
    XrdDateTime,
    XrdSubView,
    ServiceStatusChip,
    XrdLabelWithIcon,
    XrdExpandable,
    XrdEmptyPlaceholder,
  },
  props: {
    id: {
      type: String as PropType<string>,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const goTo = useGoTo();
    return { addError, addSuccessMessage, goTo };
  },
  data() {
    return {
      search: '' as string,
      loading: false,
      addWsdlDialog: false as boolean,
      addRestDialog: false as boolean,
      disableDescDialog: false as boolean,
      selectedServiceDesc: {} as ServiceDescription,
      selectedIndex: -1 as number,
      componentKey: 0 as number,
      expanded: [] as string[],
      warningInfo: [] as CodeWithDetails[],
      refreshWarningDialog: false as boolean,
      url: '' as string,
      serviceType: undefined as ServiceTypeEnum | undefined,
      serviceCode: '' as string,
      refreshId: '' as string,
      refreshBusy: {} as { [key: string]: boolean },
      enabling: {} as { [key: string]: boolean },
      refreshButtonComponentKey: 0 as number,
      serviceTypeEnum: ServiceTypeEnum,
      refreshLoading: false as boolean,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useServiceDescriptions, ['descExpanded', 'serviceDescriptions']),

    headers() {
      return [
        { title: this.$t('services.serviceCode'), key: 'full_service_code' },
        { title: this.$t('services.url'), key: 'url' },
        { title: this.$t('services.timeout'), key: 'timeout' },
      ] as DataTableHeader[];
    },

    showAddWSDLButton(): boolean {
      return this.hasPermission(Permissions.ADD_WSDL);
    },
    showAddRestButton(): boolean {
      return this.hasPermission(Permissions.ADD_OPENAPI3);
    },
    canDisable(): boolean {
      return this.hasPermission(Permissions.ENABLE_DISABLE_WSDL);
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

      // Filter out service descriptions that don't include search term
      const filtered = arr.filter((element: ServiceDescription) => {
        return element.services.find((service: Service) => {
          return (
            service.service_code.toString().toLowerCase().includes(mysearch) ||
            service.url.toString().toLowerCase().includes(mysearch) ||
            service.timeout.toString().toLowerCase().includes(mysearch)
          );
        });
      });

      // Filter out services that don't include search term
      filtered.forEach((element: ServiceDescription) => {
        element.services = element.services.filter((service: Service) => {
          return (
            service.service_code.toString().toLowerCase().includes(mysearch) ||
            service.url.toString().toLowerCase().includes(mysearch) ||
            service.timeout.toString().toLowerCase().includes(mysearch)
          );
        });
      });

      return filtered;
    },
  },

  created() {
    this.fetchData().then(() => {
      if (this.$route.query.expand) {
        this.goTo(`#service-description-${this.$route.query.expand}`);
        this.descOpen(this.$route.query.expand as string);
      }
    });
  },
  methods: {
    ...mapActions(useServiceDescriptions, [
      'hideDesc',
      'expandDesc',
      'fetchServiceDescriptions',
      'enableServiceDescription',
      'disableServiceDescription',
      'refreshServiceDescription',
    ]),

    showRefreshButton(serviceDescriptionType: string): boolean {
      if (serviceDescriptionType === this.serviceTypeEnum.WSDL) {
        return this.hasPermission(Permissions.REFRESH_WSDL);
      } else if (serviceDescriptionType === this.serviceTypeEnum.OPENAPI3) {
        return this.hasPermission(Permissions.REFRESH_OPENAPI3);
      }
      return false;
    },
    descriptionClick(desc: ServiceDescription): void {
      this.$router.push({
        name: RouteName.ServiceDescriptionDetails,
        params: { id: desc.id },
      });
    },
    serviceClick(service: Service): void {
      this.$router.push({
        name: RouteName.ServiceParameters,
        params: { serviceId: service.id },
      });
    },
    canEditServiceDesc(servicedescription: ServiceDescription): boolean {
      let permission: Permissions;
      if (servicedescription.type === ServiceType.REST) {
        permission = Permissions.EDIT_REST;
      } else if (servicedescription.type === ServiceType.WSDL) {
        permission = Permissions.EDIT_WSDL;
      } else if (servicedescription.type === ServiceType.OPENAPI3) {
        permission = Permissions.EDIT_OPENAPI3;
      } else {
        return false;
      }

      return this.hasPermission(permission);
    },
    switchChanged(serviceDesc: ServiceDescription, index: number): void {
      if (!serviceDesc.disabled) {
        // If user wants to disable service description:
        // - cancel the switch change
        // - show confirmation dialog instead
        this.selectedServiceDesc = serviceDesc;
        this.selectedIndex = index;
        this.disableDescDialog = true;
        return;
      }

      this.enabling[serviceDesc.id] = true;
      this.enableServiceDescription(serviceDesc.id)
        .then(() => {
          this.addSuccessMessage('services.enableSuccess');
        })
        .catch((error) => this.addError(error))
        .finally(() => {
          // Whatever happens, refresh the data
          this.fetchData();
        })
        .finally(() => (this.enabling[serviceDesc.id] = false));
    },

    disableDescCancel(): void {
      // User cancels the change from dialog. Switch must be returned to original position.
      this.disableDescDialog = false;
    },

    disableDescSave(): void {
      this.disableDescDialog = false;
      this.fetchData();
    },

    showAddRestDialog(): void {
      this.addRestDialog = true;
    },

    showAddWsdlDialog(): void {
      this.addWsdlDialog = true;
    },

    wsdlSave(): void {
      this.fetchData();
      this.addWsdlDialog = false;
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

    refresh(serviceDescription: ServiceDescription): void {
      this.refreshBusy[serviceDescription.id] = true;
      this.refreshButtonComponentKey += 1; // update component key to make spinner work

      this.refreshServiceDescription(serviceDescription.id)
        .then(() => {
          this.addSuccessMessage('services.refreshed');
          this.fetchData();
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.refreshWarningDialog = true;
            this.refreshId = serviceDescription.id;
          } else {
            this.addError(error);
            this.fetchData();
          }
        })
        .finally(() => {
          this.refreshBusy[serviceDescription.id] = false;
        });
    },

    acceptRefreshWarning(): void {
      this.refreshLoading = true;
      this.refreshServiceDescription(this.refreshId, true)
        .then(() => {
          this.addSuccessMessage('services.refreshed');
        })
        .catch((error) => this.addError(error))
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
      this.hideDesc(tokenId);
    },
    descOpen(tokenId: string) {
      this.expandDesc(tokenId);
    },
    isExpanded(tokenId: string) {
      return this.descExpanded(tokenId);
    },

    async fetchData() {
      this.loading = true;
      return this.fetchServiceDescriptions(this.id)
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
