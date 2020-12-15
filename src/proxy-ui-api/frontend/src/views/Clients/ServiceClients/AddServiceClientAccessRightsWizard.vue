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
  <div class="view-wrap">
    <subViewTitle
      :title="$t('serviceClients.addServiceClientTitle')"
      :showClose="false"
      data-test="add-subject-title"
      class="pa-4"
    />

    <v-stepper :alt-labels="true" v-model="step" class="stepper noshadow">
      <v-stepper-header class="noshadow stepper-header">
        <v-stepper-step :complete="step > 1" step="1">{{
          $t('serviceClients.memberGroupStep')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="step > 2" step="2">{{
          $t('serviceClients.servicesStep')
        }}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items class="stepper-content">
        <v-stepper-content step="1">
          <MemberOrGroupSelectionStep
            :id="this.id"
            :service-clients="serviceClients"
            v-on:candidate-selection="candidateSelection"
            v-on:set-step="nextStep"
          ></MemberOrGroupSelectionStep>
        </v-stepper-content>
        <v-stepper-content step="2">
          <ServiceSelectionStep
            v-if="serviceClientCandidateSelection"
            :serviceCandidates="serviceCandidates"
            :serviceClientCandidateSelection="serviceClientCandidateSelection"
            :id="id"
            v-on:set-step="previousStep"
          ></ServiceSelectionStep>
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import MemberOrGroupSelectionStep from '@/views/Clients/ServiceClients/MemberOrGroupSelectionStep.vue';
import ServiceSelectionStep from '@/views/Clients/ServiceClients/ServiceSelectionStep.vue';
import {
  AccessRight,
  Service,
  ServiceClient,
  ServiceDescription,
} from '@/openapi-types';
import * as api from '@/util/api';
import { ServiceCandidate } from '@/ui-types';
import { compareByServiceCode } from '@/util/sorting';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  components: {
    MemberOrGroupSelectionStep,
    ServiceSelectionStep,
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
    fetchData: function (): void {
      api
        .get<ServiceClient[]>(
          `/clients/${encodePathParameter(this.id)}/service-clients`,
          {},
        )
        .then((response) => (this.serviceClients = response.data))
        .catch((error) => this.$store.dispatch('showError', error));

      api
        .get<ServiceDescription[]>(
          `/clients/${encodePathParameter(this.id)}/service-descriptions`,
        )
        .then((response) => {
          const serviceDescriptions = response.data;

          // Parse all services for the current client and map them to ServiceCandidates (manually added type for
          // objects that are used to add and list services that can be granted access rights to).
          this.serviceCandidates = serviceDescriptions
            .flatMap((serviceDescription) => serviceDescription.services)
            .sort(compareByServiceCode)
            .map((service: Service) => ({
              service_code: service.service_code,
              service_title: service.title,
              id: service.id,
            }));
        })
        .catch((error) => this.$store.dispatch('showError', error));
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
@import '../../../assets/shared';
@import '../../../assets/wizards';

.view-wrap {
  width: 100%;
  max-width: 850px;
  margin: 10px;
}

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
