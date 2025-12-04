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
  <div>
    <XrdBtn
      data-test="system-parameters-timestamping-services-add-button"
      variant="text"
      text="systemParameters.timestampingServices.action.add.button"
      prepend-icon="add_circle"
      color="tertiary"
      :disabled="selectableTimestampingServices.length === 0"
      @click="openDialog"
    />

    <XrdSimpleDialog
      v-if="show"
      :disable-save="selectedTimestampingService === undefined"
      :loading="loading"
      cancel-button-text="action.cancel"
      save-button-text="action.add"
      title="systemParameters.timestampingServices.action.add.dialog.title"
      data-test="system-parameters-add-timestamping-service-dialog"
      submittable
      @cancel="close"
      @save="add"
    >
      <template #content>
        <XrdFormBlock>
          <XrdFormBlockRow full-length>
            <v-radio-group
              v-model="selectedTimestampingServiceName"
              data-test="system-parameters-add-timestamping-service-dialog-radio-group"
              class="xrd"
              :label="$t('systemParameters.timestampingServices.action.add.dialog.info')"
            >
              <div
                v-for="timestampingService in selectableTimestampingServices"
                :key="timestampingService.name"
                class="d-flex align-center"
              >
                <v-radio class="xrd" :name="timestampingService.name" :label="timestampingService.name" :value="timestampingService.name" />
                <span class="mr-16">{{ $t('systemParameters.costType.' + timestampingService.cost_type) }}</span>
              </div>
            </v-radio-group>
          </XrdFormBlockRow>
        </XrdFormBlock>
      </template>
    </XrdSimpleDialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import * as api from '@/util/api';
import { Permissions } from '@/global';
import { TimestampingService } from '@/openapi-types';
import { sortTimestampingServices } from '@/util/sorting';
import { XrdSimpleDialog, XrdBtn, XrdFormBlock, XrdFormBlockRow, useNotifications } from '@niis/shared-ui';

export default defineComponent({
  components: {
    XrdSimpleDialog,
    XrdBtn,
    XrdFormBlock,
    XrdFormBlockRow,
  },
  props: {
    configuredTimestampingServices: {
      type: Array as PropType<TimestampingService[]>,
      required: true,
    },
  },
  emits: ['added'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return {
      addError,
      addSuccessMessage,
    };
  },
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
          !this.configuredTimestampingServices.some((configuredService) => approvedService.name === configuredService.name),
      );
    },
    selectedTimestampingService(): TimestampingService | undefined {
      return this.approvedTimestampingServices.find((approvedService) => approvedService.name === this.selectedTimestampingServiceName);
    },
  },
  created(): void {
    this.fetchApprovedTimestampingServices();
  },
  methods: {
    openDialog(): void {
      this.show = true;
    },
    fetchApprovedTimestampingServices(): void {
      api
        .get<TimestampingService[]>('/timestamping-services')
        .then((resp) => (this.approvedTimestampingServices = sortTimestampingServices(resp.data)))
        .catch((error) => this.addError(error));
    },
    add(): void {
      this.loading = true;
      api
        .post('/system/timestamping-services', this.selectedTimestampingService)
        .then(() => {
          this.addSuccessMessage('systemParameters.timestampingServices.action.add.dialog.success');
          this.$emit('added');
          this.close();
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },
    close(): void {
      this.show = false;
      this.selectedTimestampingServiceName = '';
    },
  },
});
</script>

<style scoped lang="scss"></style>
