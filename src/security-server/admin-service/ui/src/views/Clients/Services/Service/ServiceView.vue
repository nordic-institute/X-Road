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
    :translated-title="service?.full_service_code"
    :breadcrumbs="breadcrumbs"
    @close="close"
  >
    <template v-if="nonWsdl" #tabs>
      <XrdViewNavigation bordered :allowed-tabs="tabs" />
    </template>
    <router-view @update-service="fetchData" />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { RouteName } from '@/global';
import { ServiceTypeEnum } from '@/domain';
import { Tab, XrdElevatedViewFixedWidth, XrdViewNavigation, useNotifications } from '@niis/shared-ui';
import { mapActions, mapState } from 'pinia';
import { useServices } from '@/store/modules/services';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { clientTitle } from '@/util/ClientUtil';
import { useClient } from '@/store/modules/client';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';
import { ServiceType } from '@/openapi-types';

export default defineComponent({
  components: { XrdElevatedViewFixedWidth, XrdViewNavigation },
  props: {
    serviceId: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      serviceTypeEnum: ServiceTypeEnum,
    };
  },
  computed: {
    ...mapState(useServices, ['service']),
    ...mapState(useServiceDescriptions, ['serviceDescription']),
    ...mapState(useClient, ['client', 'clientLoading']),
    tabs(): Tab[] {
      return [
        {
          key: 'parameters',
          name: 'tab.services.parameters',
          icon: 'page_info',
          to: {
            name: RouteName.ServiceParameters,
            params: {
              serviceId: this.serviceId,
            },
          },
        },
        {
          key: 'endpoints',
          name: 'tab.services.endpoints',
          icon: 'graph_2 filled',
          to: {
            name: RouteName.Endpoints,
            params: {
              serviceId: this.serviceId,
            },
          },
        },
      ];
    },
    serviceType() {
      return this.serviceDescription?.type;
    },
    nonWsdl() {
      return (
        this.serviceType &&
        [ServiceType.REST, ServiceType.OPENAPI3].includes(this.serviceType)
      );
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
      if (this.client && this.serviceType) {
        breadcrumbs.push({
          title: this.serviceType,
          to: {
            name: RouteName.SubsystemServices,
            params: { id: this.client.id },
            query: { expand: this.serviceDescription?.id },
          },
        });
      }
      if (this.service) {
        breadcrumbs.push({
          title: this.service?.full_service_code || '',
        });
      }
      return breadcrumbs;
    },
  },
  watch: {
    serviceId: {
      immediate: true,
      handler() {
        this.fetchData(this.serviceId);
      },
    },
  },
  methods: {
    ...mapActions(useServices, ['fetchService']),
    ...mapActions(useClient, ['fetchClient']),
    ...mapActions(useServiceDescriptions, ['fetchServiceDescription']),
    fetchData(serviceId: string): void {
      this.fetchService(serviceId)
        .then((service) =>
          this.fetchServiceDescription(service.service_description_id),
        )
        .then((description) => this.fetchClient(description.client_id))
        .catch((error) => this.addError(error, true));
    },

    close(): void {
      if (this.client) {
        this.$router.push({
          name: RouteName.SubsystemServices,
          params: { id: this.client.id },
        });
      }
    },
  },
});
</script>

<style lang="scss" scoped></style>
