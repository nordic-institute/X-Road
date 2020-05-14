<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <subViewTitle
      :title="$t('serviceClients.addServiceClientTitle')"
      :showClose="false"
      data-test="add-subject-title"
    />

    <v-stepper :alt-labels="true" v-model="step" class="stepper noshadow">
      <v-stepper-header class="noshadow stepper-header">
        <v-stepper-step :complete="step > 1" step="1">{{$t('serviceClients.memberGroupStep')}}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="step > 2" step="2">{{$t('serviceClients.servicesStep')}}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items class="stepper-content">
        <v-stepper-content step="1">
          <MemberOrGroupSelectionStep
            :id="this.id"
            :service-clients="serviceClients"
            v-on:candidate-selection="candidateSelection"
            v-on:set-step="nextStep"></MemberOrGroupSelectionStep>
        </v-stepper-content>
        <v-stepper-content step="2">
          <ServiceSelectionStep
            v-if="serviceClientCandidateSelection"
            :serviceCandidates="serviceCandidates"
            :serviceClientCandidateSelection="serviceClientCandidateSelection"
            :id="id"
            v-on:set-step="previousStep"></ServiceSelectionStep>
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>

  </div>
</template>

<script lang="ts">
  import Vue from 'vue';
  import SubViewTitle from '@/components/ui/SubViewTitle.vue';
  import MemberOrGroupSelectionStep from '@/views/Clients/ServiceClients/MemberOrGroupSelectionStep.vue';
  import ServiceSelectionStep from '@/views/Clients/ServiceClients/ServiceSelectionStep.vue';
  import LargeButton from '@/components/ui/LargeButton.vue';
  import {AccessRight, Service, ServiceClient, ServiceDescription} from '@/types';
  import * as api from '@/util/api';
  import {ServiceCandidate} from '@/ui-types';

  export default Vue.extend({
    props: {
      id: {
        type: String,
        required: true,
      },
    },
    components: {
      SubViewTitle,
      MemberOrGroupSelectionStep,
      ServiceSelectionStep,
      LargeButton,
    },
    data() {
      return {
        step: 1 as number,
        serviceClientAccessRights: [] as AccessRight[],
        serviceCandidates: [] as ServiceCandidate[],
        serviceClients: [] as ServiceClient[],
        serviceClientCandidateSelection: undefined as undefined | ServiceClient,
      };
    },
    methods: {
      candidateSelection(candidate: ServiceClient): void {
        this.serviceClientCandidateSelection = candidate;
      },
      fetchData(): void {

        api.get(`/clients/${this.id}/service-clients`, {})
          .then( ( response: any ): void => this.serviceClients = response.data )
          .catch( (error: any) =>
            this.$store.dispatch('showError', error));

        api
          .get(`/clients/${this.id}/service-descriptions`)
          .then( (response: any) => {
            const serviceDescriptions = response.data as ServiceDescription[];

            // Parse all services for the current client and map them to ServiceCandidates (manually added type for
            // objects that are used to add and list services that can be granted access rights to).
            this.serviceCandidates = serviceDescriptions
              .reduce( (curr: Service[], next: ServiceDescription) => curr.concat(...next.services), [])
              .map( (service: Service) => ({
                service_code: service.service_code,
                service_title: service.title,
                id: service.id,
              }));
          })
          .catch( (error: any) => this.$store.dispatch('showError', error));
      },
      previousStep(): void {
        this.step -= 1;
      },
      nextStep(): void {
        this.step += 1;
      },
    },
    created(): void {
      this.fetchData();
    },
  });

</script>

<style lang="scss" scoped>
  @import '../../../assets/global-style';
  @import '../../../assets/shared';
  @import '../../../assets/wizards';

  .stepper-content {
    width: 100%;
    max-width: 900px;
    margin-left: auto;
    margin-right: auto;
  }

  .stepper {
    width: 100%;
  }

  .stepper-header {
    width: 50%;
    margin: 0 auto;
  }

  .noshadow {
    -webkit-box-shadow: none;
    -moz-box-shadow: none;
    box-shadow: none;
  }

  .full-width {
    width: 100%;
  }

</style>
