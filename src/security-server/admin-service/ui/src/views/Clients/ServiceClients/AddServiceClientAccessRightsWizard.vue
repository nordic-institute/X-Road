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
  <XrdElevatedViewSimple data-test="add-subject-view" title="serviceClients.addServiceClientTitle" go-back-on-close>
    <!-- eslint-disable-next-line vuetify/no-deprecated-components -->
    <XrdWizard v-model="step">
      <template #header-items>
        <v-stepper-item :complete="step > 1" :value="1">
          {{ $t('serviceClients.memberGroupStep') }}
        </v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="step > 2" :value="2">
          {{ $t('serviceClients.servicesStep') }}
        </v-stepper-item>
      </template>

      <v-stepper-window-item :value="1">
        <MemberOrGroupSelectionStep :id="id" :service-clients="serviceClients" @set-step="nextStep" />
      </v-stepper-window-item>
      <v-stepper-window-item :value="2">
        <ServiceSelectionStep
          v-if="serviceClientCandidateSelection"
          :id="id"
          :service-candidates="serviceCandidates"
          :service-client-candidate-selection="serviceClientCandidateSelection"
          @set-step="previousStep"
        />
      </v-stepper-window-item>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import MemberOrGroupSelectionStep from '@/views/Clients/ServiceClients/MemberOrGroupSelectionStep.vue';
import ServiceSelectionStep from '@/views/Clients/ServiceClients/ServiceSelectionStep.vue';
import { AccessRight, Service, ServiceClient } from '@/openapi-types';
import { ServiceCandidate } from '@/ui-types';
import { compareByServiceCode } from '@/util/sorting';
import { XrdElevatedViewSimple, useNotifications, XrdWizard } from '@niis/shared-ui';
import { mapActions } from 'pinia';
import { useServiceClients } from '@/store/modules/service-clients';
import { useServices } from '@/store/modules/services';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

export default defineComponent({
  components: {
    MemberOrGroupSelectionStep,
    ServiceSelectionStep,
    XrdElevatedViewSimple,
    XrdWizard,
  },
  props: {
    id: {
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
    ...mapActions(useServiceClients, ['fetchServiceClients']),
    ...mapActions(useServiceDescriptions, ['fetchServiceDescriptions']),
    fetchData: function (): void {
      this.fetchServiceClients(this.id)
        .then((data) => (this.serviceClients = data))
        .catch((error) => this.addError(error));

      this.fetchServiceDescriptions(this.id, false)
        .then((data) => {
          // Parse all services for the current client and map them to ServiceCandidates (manually added type for
          // objects that are used to add and list services that can be granted access rights to).
          this.serviceCandidates = data
            .flatMap((serviceDescription) => serviceDescription.services)
            .sort(compareByServiceCode)
            .map((service: Service) => ({
              service_code: service.service_code,
              service_title: service.title,
              id: service.id,
            }));
        })
        .catch((error) => this.addError(error));
    },
    previousStep(): void {
      this.step -= 1;
    },
    nextStep(candidate: ServiceClient): void {
      this.serviceClientCandidateSelection = candidate;
      this.step += 1;
    },
  },
});
</script>

<style lang="scss" scoped></style>
