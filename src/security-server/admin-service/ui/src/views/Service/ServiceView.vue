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
  <div class="xrd-tab-max-width xrd-main-wrap">
    <xrd-sub-view-title
      :title="service.full_service_code ?? ''"
      class="sub-view-title-spacing"
      @close="close"
    />

    <v-tabs
      v-if="$route.query.descriptionType !== serviceTypeEnum.WSDL"
      v-model="currentTab"
      bg-color="#F4F3F6"
      class="xrd-tabs"
      color="primary"
      slider-size="2"
    >
      <v-tabs-slider
        color="primary"
        class="xrd-sub-tabs-slider"
      ></v-tabs-slider>
      <v-tab
        v-for="tab in tabs"
        :key="tab.key"
        :to="tab.to"
        :data-test="tab.key"
        >{{ $t(tab.name) }}</v-tab
      >
    </v-tabs>

    <router-view
      service="service"
      class="sub-view-spacing"
      @update-service="fetchData"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import { RouteName } from '@/global';
import { ServiceTypeEnum } from '@/domain';
import { Tab } from '@/ui-types';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useServices } from '@/store/modules/services';
import { ServiceClient, Service } from '@/openapi-types';

export default defineComponent({
  props: {
    serviceId: {
      type: String,
      required: true,
    },
    clientId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      currentTab: undefined as undefined | Tab,
      serviceTypeEnum: ServiceTypeEnum,
    };
  },
  computed: {
    ...mapState(useServices, ['service']),
    tabs(): Tab[] {
      return [
        {
          key: 'parameters',
          name: 'tab.services.parameters',
          to: {
            name: RouteName.ServiceParameters,
            query: { descriptionType: this.$route.query.descriptionType },
            params: {
              clientId: this.clientId,
              serviceId: this.serviceId,
            },
          },
        },
        {
          key: 'endpoints',
          name: 'tab.services.endpoints',
          to: {
            name: RouteName.Endpoints,
            query: { descriptionType: this.$route.query.descriptionType },
            params: {
              clientId: this.clientId,
              serviceId: this.serviceId,
            },
          },
        },
      ];
    },
  },

  created() {
    this.fetchData(this.serviceId);
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useServices, ['setService', 'setServiceClients']),
    fetchData(serviceId: string): void {
      api
        .get<Service>(`/services/${encodePathParameter(serviceId)}`)
        .then((res) => {
          // Set ssl_auth to true if it is returned as null from backend
          this.setService(res.data);
        })
        .catch((error) => {
          this.showError(error);
        });

      api
        .get<ServiceClient[]>(
          `/services/${encodePathParameter(serviceId)}/service-clients`,
        )
        .then((res) => {
          this.setServiceClients(res.data);
        })
        .catch((error) => {
          this.showError(error);
        });
    },

    close(): void {
      this.$router.push({
        name: RouteName.SubsystemServices,
        params: { id: this.clientId },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.sub-view-title-spacing {
  margin-bottom: 30px;
  padding: 16px;
}

.sub-view-spacing {
  margin-top: 20px;
}
</style>
