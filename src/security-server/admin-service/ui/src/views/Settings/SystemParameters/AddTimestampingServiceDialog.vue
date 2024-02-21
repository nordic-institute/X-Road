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
  <v-dialog
    v-model="show"
    max-width="550"
    persistent
    data-test="system-parameters-add-timestamping-service-dialog"
  >
    <template #activator="{ props }">
      <xrd-button
        data-test="system-parameters-timestamping-services-add-button"
        outlined
        :disabled="selectableTimestampingServices.length === 0"
        v-bind="props"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <xrd-icon-add />
        </xrd-icon-base>
        {{ $t('systemParameters.timestampingServices.action.add.button') }}
      </xrd-button>
    </template>
    <v-card class="xrd-card">
      <v-card-title>
        <span data-test="dialog-title" class="text-h5">{{
          $t('systemParameters.timestampingServices.action.add.dialog.title')
        }}</span>
      </v-card-title>
      <v-card-text class="content-wrapper">
        <v-container>
          <v-row>
            <v-col>
              {{
                $t(
                  'systemParameters.timestampingServices.action.add.dialog.info',
                )
              }}
            </v-col>
          </v-row>
          <v-radio-group
            v-model="selectedTimestampingServiceName"
            data-test="system-parameters-add-timestamping-service-dialog-radio-group"
          >
            <v-row
              v-for="timestampingService in selectableTimestampingServices"
              :key="timestampingService.name"
              class="option-row"
            >
              <v-col>
                <v-radio
                  :name="timestampingService.name"
                  :label="timestampingService.name"
                  :value="timestampingService.name"
                />
              </v-col>
            </v-row>
          </v-radio-group>
        </v-container>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>
        <xrd-button
          data-test="system-parameters-add-timestamping-service-dialog-cancel-button"
          outlined
          @click="close"
        >
          {{ $t('action.cancel') }}</xrd-button
        >
        <xrd-button
          data-test="system-parameters-add-timestamping-service-dialog-add-button"
          :loading="loading"
          :disabled="selectedTimestampingService === undefined"
          @click="add"
        >
          <xrd-icon-base class="xrd-large-button-icon">
            <xrd-icon-add />
          </xrd-icon-base>
          {{ $t('action.add') }}
        </xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import * as api from '@/util/api';
import { Permissions } from '@/global';
import { TimestampingService } from '@/openapi-types';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  name: 'AddTimestampingServiceDialog',
  props: {
    configuredTimestampingServices: {
      type: Array as PropType<TimestampingService[]>,
      required: true,
    },
  },
  emits: ['added'],
  data() {
    return {
      loading: false,
      show: false,
      approvedTimestampingServices: [] as TimestampingService[],
      selectedTimestampingServiceName: '',
      permissions: Permissions,
    };
  },
  computed: {
    selectableTimestampingServices(): TimestampingService[] {
      return [...this.approvedTimestampingServices].filter(
        (approvedService) =>
          !this.configuredTimestampingServices.some(
            (configuredService) =>
              approvedService.name === configuredService.name,
          ),
      );
    },
    selectedTimestampingService(): TimestampingService | undefined {
      return this.approvedTimestampingServices.find(
        (approvedService) =>
          approvedService.name === this.selectedTimestampingServiceName,
      );
    },
  },
  created(): void {
    this.fetchApprovedTimestampingServices();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    fetchApprovedTimestampingServices(): void {
      api
        .get<TimestampingService[]>('/timestamping-services')
        .then((resp) => (this.approvedTimestampingServices = resp.data))
        .catch((error) => this.showError(error));
    },
    add(): void {
      this.loading = true;
      api
        .post('/system/timestamping-services', this.selectedTimestampingService)
        .then(() => {
          this.$emit('added');
          this.loading = false;
          this.close();
          this.showSuccess(
            this.$t(
              'systemParameters.timestampingServices.action.add.dialog.success',
            ),
          );
        })
        .catch((error) => this.showError(error));
    },
    close(): void {
      this.show = false;
      this.selectedTimestampingServiceName = '';
    },
  },
});
</script>

<style scoped lang="scss">
@import '@/assets/colors';
.option-row {
  border-bottom: solid 1px $XRoad-WarmGrey30;
}

.content-wrapper {
  color: $XRoad-Black100 !important;
}
</style>
