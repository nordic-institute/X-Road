<template>
  <div class="xrd-tab-max-width">
    <subViewTitle
      :title="service.full_service_code"
      @close="close"
      class="sub-view-title-spacing"
    />

    <v-tabs
      v-if="$route.query.descriptionType !== serviceTypeEnum.WSDL"
      v-model="tab"
      class="xrd-tabs"
      color="secondary"
      grow
      slider-size="4"
    >
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab
        v-for="tab in tabs"
        v-bind:key="tab.key"
        :to="tab.to"
        data-test="service-tab"
        >{{ $t(tab.name) }}</v-tab
      >
    </v-tabs>

    <router-view
      v-on:updateService="fetchData"
      service="service"
      class="sub-view-spacing"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import { RouteName } from '@/global';
import { ServiceTypeEnum } from '@/domain';
import { mapGetters } from 'vuex';
import { Tab } from '@/ui-types';
import { encodePathParameter } from '@/util/api';

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
      serviceTypeEnum: ServiceTypeEnum,
    };
  },
  computed: {
    ...mapGetters(['service']),
    tabs(): Tab[] {
      return [
        {
          key: 'parameters',
          name: 'tab.services.parameters',
          to: {
            name: RouteName.ServiceParameters,
            query: { descriptionType: this.$route.query.descriptionType },
          },
        },
        {
          key: 'endpoints',
          name: 'tab.services.endpoints',
          to: {
            name: RouteName.Endpoints,
            query: { descriptionType: this.$route.query.descriptionType },
          },
        },
      ];
    },
  },

  methods: {
    fetchData(serviceId: string): void {
      api
        .get(`/services/${encodePathParameter(serviceId)}`)
        .then((res) => {
          // Set ssl_auth to true if it is returned as null from backend
          this.$store.dispatch('setService', res.data);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });

      api
        .get(`/services/${encodePathParameter(serviceId)}/service-clients`)
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
