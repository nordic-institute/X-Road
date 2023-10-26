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
  <v-sheet class="view-wrap" data-test="add-subject-view">
    <xrd-sub-view-title
      :title="$t('serviceClients.addServiceClientTitle')"
      :show-close="false"
      data-test="add-subject-title"
      class="pa-4"
    />
    <!-- eslint-disable-next-line vuetify/no-deprecated-components -->
    <v-stepper
      v-model="step"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <v-stepper-header class="wizard-noshadow stepper-header">
        <v-stepper-item :complete="step > 1" :value="1">{{
          $t('serviceClients.memberGroupStep')
        }}</v-stepper-item>
        <v-divider></v-divider>
        <v-stepper-item :complete="step > 2" :value="2">{{
          $t('serviceClients.servicesStep')
        }}</v-stepper-item>
      </v-stepper-header>

      <v-stepper-window class="wizard-stepper-content">
        <v-stepper-window-item :value="1">
          <MemberOrGroupSelectionStep
            :id="id"
            :service-clients="serviceClients"
            @candidate-selection="candidateSelection"
            @set-step="nextStep"
          ></MemberOrGroupSelectionStep>
        </v-stepper-window-item>
        <v-stepper-window-item :value="2">
          <ServiceSelectionStep
            v-if="serviceClientCandidateSelection"
            :id="id"
            :service-candidates="serviceCandidates"
            :service-client-candidate-selection="
              serviceClientCandidateSelection
            "
            @set-step="previousStep"
          ></ServiceSelectionStep>
        </v-stepper-window-item>
      </v-stepper-window>
    </v-stepper>
  </v-sheet>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
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
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import {
  VStepper,
  VStepperHeader,
  VStepperItem,
  VStepperWindow,
  VStepperWindowItem,
} from 'vuetify/labs/VStepper';

export default defineComponent({
  components: {
    VStepper,
    VStepperHeader,
    VStepperItem,
    VStepperWindow,
    VStepperWindowItem,
    MemberOrGroupSelectionStep,
    ServiceSelectionStep,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
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
  created(): void {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
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
        .catch((error) => this.showError(error));

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
        .catch((error) => this.showError(error));
    },
    previousStep(): void {
      this.step -= 1;
    },
    nextStep(): void {
      this.step += 1;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/wizards';

/* Modify wizard import */
.view-wrap {
  max-width: 850px;
  width: 100%;
  margin: 10px;
}

/* Modify wizard import */
.wizard-stepper-content {
  max-width: 900px;
}

.stepper-header {
  width: 50%;
  margin: 0 auto;
}
</style>
