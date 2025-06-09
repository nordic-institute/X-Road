<template>
  <XrdTitledView
    title-key="tab.diagnostics.traffic"
    data-test="diagnostics-view"
  >
    <v-card variant="flat">
<!--      <v-card-title class="text-h5">-->
<!--        {{ $t('diagnostics.traffic.title') }}-->
<!--      </v-card-title>-->
      <v-card-text class="xrd-card-text">
        <v-row dense>
          <v-col cols="3">
            <xrd-form-label
              label-text="Period"
              help-text="Select start and end"
            />
          </v-col>
          <v-col cols="2">
            <v-date-input
              label="Date"
              model-value="2025-05-28"
              :display-format="formatDate"
            ></v-date-input>
          </v-col>
          <v-col cols="2">
            <v-text-field
              type="time"
              model-value="00:00"
              label="Time"
            ></v-text-field>
          </v-col>
          <v-col cols="1" class="align-center">
            <div
              class="d-flex align-center"
              style="height: 100%; width: 100%"
            ></div>
          </v-col>
          <v-col cols="2">
            <v-date-input
              label="Date"
              model-value="2025-06-04"
              :display-format="formatDate"
            ></v-date-input>
          </v-col>
          <v-col cols="2">
            <v-text-field
              type="time"
              model-value="00:00"
              label="Time"
            ></v-text-field>
          </v-col>
        </v-row>
        <v-row dense>
          <v-col cols="3">
            <xrd-form-label
              label-text="Party"
              help-text="Service, subsystem, member"
            />
          </v-col>
          <v-col>
            <v-select
              label="Client"
              clearable
              :items="clientsStore.clients"
              item-title="id"
              item-value="id"
              :loading="clientsLoading"
            >
              <template v-slot:item="{ props: itemProps, item }">
                <v-list-item
                  v-bind="itemProps"
                  :subtitle="item.raw.member_name"
                ></v-list-item>
              </template>
            </v-select>
          </v-col>
        </v-row>
        <v-row dense>
          <v-col cols="3">
            <xrd-form-label
              label-text="Exchange role"
              help-text="Producer or Consumer"
            />
          </v-col>
          <v-col>
            <v-select
              label="Exchange role"
              clearable
              :items="['Producer', 'Consumer']"
            >
            </v-select>
          </v-col>
        </v-row>
        <v-row dense>
          <v-col cols="3">
            <xrd-form-label
              label-text="Status"
              help-text="Message exchange status"
            />
          </v-col>
          <v-col>
            <v-select label="Status" clearable :items="['Success', 'Failure']">
            </v-select>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card>
      <TrafficChart></TrafficChart>
    </v-card>
  </XrdTitledView>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { XrdTitledView } from '@niis/shared-ui';
import { VDateInput } from 'vuetify/labs/VDateInput';
import { useClients } from '@/store/modules/clients';
import TrafficChart from '@/views/Diagnostics/Traffic/TrafficChart.vue';
import { useDate } from 'vuetify';

const clientsLoading = ref(true);

const clientsStore = useClients();
clientsStore
  .fetchClients()
  .catch((error) => {
    console.error('Failed to load clients:', error);
  })
  .finally(() => {
    clientsLoading.value = false;
  });

const dateAdapter = useDate();

function formatDate(date: Date): string {
  return dateAdapter.toISO(date);
}
</script>
