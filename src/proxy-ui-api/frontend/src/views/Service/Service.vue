<template>
    <div class="xrd-tab-max-width">
        <subViewTitle :title="service.service_code" @close="close" class="sub-view-title-spacing" />

        <v-tabs v-if="$route.query.descriptionType !== serviceTypeEnum.WSDL" v-model="tab" class="xrd-tabs" color="secondary" grow slider-size="4" >
            <v-tabs-slider color="secondary"></v-tabs-slider>
            <v-tab v-for="tab in tabs" v-bind:key="tab.key"
                   :to="tab.to" data-test="service-tab">{{ $t(tab.name) }}</v-tab>
        </v-tabs>

        <router-view v-on:updateService="fetchData" service="service" class="sub-view-spacing" />

    </div>
</template>


<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import {RouteName} from '@/global';
import {ServiceTypeEnum} from '@/domain';

export default Vue.extend({
  components: {
    SubViewTitle,
  },
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
      tab: null,
      service: {},
      serviceTypeEnum: ServiceTypeEnum,
    };
  },
  computed: {
    tabs(): any[] {
      const tabs = [
        {
          key: 'parameters',
          name: 'tab.services.parameters',
          to: {
            name: RouteName.ServiceParameters,
          },
        },
        {
          key: 'endpoints',
          name: 'tab.services.endpoints',
          to: {
            name: RouteName.Endpoints,
          },
        },
      ];
      return tabs;
    },
  },

  methods: {

    fetchData(serviceId: string): void {
      api
        .get(`/services/${serviceId}`)
        .then((res) => {
          this.service = res.data;
          this.$store.dispatch('setService', res.data);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });

      api
        .get(`/services/${serviceId}/service-clients`)
        .then((res) => {
          this.$store.dispatch('setServiceClients', res.data);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    close(): void {
      this.$router.push({
        name: RouteName.SubsystemServices,
        params: { id: this.clientId },
      });
    },
  },

  created() {
    this.fetchData(this.serviceId);
  },
});
</script>

<style lang="scss" scoped>
    @import '../../assets/colors';
    @import '../../assets/tables';

    .sub-view-title-spacing {
        margin-bottom: 30px;
    }

    .sub-view-spacing {
        margin-top: 20px;
    }

</style>

