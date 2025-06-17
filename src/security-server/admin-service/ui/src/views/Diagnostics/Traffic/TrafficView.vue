<template>
  <XrdTitledView
    title-key="tab.diagnostics.traffic"
    data-test="diagnostics-view"
  >
    <v-card variant="flat">
      <v-card-text class="xrd-card-text">
        <v-row dense>
          <v-col cols="3">
            <xrd-form-label
              label-text="Period"
              help-text="Select start and end"
            />
          </v-col>
          <v-col cols="auto">
            <v-date-input
              v-model="filters.startDate"
              class="date-input"
              label="Date"
              prepend-inner-icon="$calendar"
              prepend-icon=""
            ></v-date-input>
          </v-col>
          <v-col cols="auto">
            <v-text-field
              v-model="filters.startTime"
              v-maska="'##:##'"
              class="time-input"
              label="Time"
            ></v-text-field>
          </v-col>
          <v-col cols="auto"><span class="range-separator"></span></v-col>
          <v-col cols="auto">
            <v-date-input
              v-model="filters.endDate"
              class="date-input"
              label="Date"
              prepend-inner-icon="$calendar"
              prepend-icon=""
            ></v-date-input>
          </v-col>
          <v-col cols="auto">
            <v-text-field
              v-model="filters.endTime"
              v-maska="'##:##'"
              class="time-input"
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
          <v-col cols="5">
            <v-select
              v-model="filters.client"
              label="Client"
              clearable
              :items="clientsStore.clients"
              item-title="id"
              item-value="id"
              :loading="clientsLoading"
            >
              <template #item="{ props: itemProps, item }">
                <v-list-item
                  v-bind="itemProps"
                  :subtitle="item.raw.member_name"
                ></v-list-item>
              </template>
            </v-select>
          </v-col>
          <v-col cols="4">
            <v-select
              v-model="filters.service"
              label="Service"
              clearable
              :items="services"
              item-title="full_service_code"
              item-value="id"
              :loading="servicesLoading"
              :disabled="!services.length"
            >
              <template #item="{ props: itemProps, item }">
                <v-list-item
                  v-bind="itemProps"
                  :subtitle="item.raw.title"
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
              v-model="filters.exchangeRole"
              label="Exchange role"
              clearable
              :items="['Producer', 'Client']"
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
            <v-select
              v-model="filters.status"
              label="Status"
              clearable
              :items="[
                { title: 'Success', value: true },
                { title: 'Failure', value: false },
              ]"
            >
            </v-select>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
    <v-card height="20rem">
      <TrafficChart :series="series" />
    </v-card>
  </XrdTitledView>
</template>

<script lang="ts" setup>
import { reactive, Reactive, Ref, ref, watch } from 'vue';
import { Colors, XrdTitledView } from '@niis/shared-ui';
import { VDateInput } from 'vuetify/labs/VDateInput';
import { useClients } from '@/store/modules/clients';
import TrafficChart from '@/views/Diagnostics/Traffic/TrafficChart.vue';
import { useNotifications } from '@/store/modules/notifications';
import axios from 'axios';
import { OperationalDataInterval, Service } from '@/openapi-types';
import dayjs, { Dayjs } from 'dayjs';
import { vMaska } from 'maska/vue';
import { useServices } from '@/store/modules/services';

const { showError } = useNotifications();

const clientsLoading = ref(true);
const series: Ref<Array<TrafficSeries>> = ref([]);

const servicesLoading = ref(false);
const services: Ref<Array<Service>> = ref([]);

const clientsStore = useClients();
clientsStore
  .fetchClients()
  .catch(showError)
  .finally(() => {
    clientsLoading.value = false;
  });

const servicesStore = useServices();

const filters: Reactive<TrafficFilter> = reactive({
  startDate: dayjs().subtract(7, 'day').toDate(),
  startTime: '00:00',
  endDate: dayjs().toDate(),
  endTime: '23:59',
});

function onFiltersChange(filter: TrafficFilter) {
  fetchTrafficData(toQueryParams(filter))
    .then((data) => toChartSeries(filter, data))
    .catch(showError);
}

watch(filters, onFiltersChange, { deep: true, immediate: true });
watch(() => filters.client, fetchServices);

function fetchServices(client?: string) {
  services.value = [];
  filters.service = undefined;

  if (client) {
    servicesLoading.value = true;
    servicesStore
      .fetchServiceDescriptions(client)
      .then(() => {
        services.value = servicesStore.serviceDescriptions.flatMap(
          (sd) => sd.services,
        );
      })
      .catch(showError)
      .finally(() => {
        servicesLoading.value = false;
      });
  }
}

async function fetchTrafficData(
  queryParams: GetOperationalDataIntervalsParams,
): Promise<OperationalDataInterval[]> {
  return axios
    .get<OperationalDataInterval[]>('/diagnostics/operational-monitoring', {
      params: queryParams,
    })
    .then((response) => response.data);
}

function toChartSeries(filter: TrafficFilter, data: OperationalDataInterval[]) {
  const value: TrafficSeries[] = [];

  if (filter.status ?? true) {
    value.push({
      name: 'Successful requests',
      color: Colors.Success100,
      data: data.map((item) => [
        new Date(item.interval_start_time as string).getTime(),
        item.success_count ?? 0,
      ]),
    });
  }
  if (!(filter.status ?? false)) {
    value.push({
      name: 'Failed requests',
      color: Colors.Error,
      data: data.map((item) => [
        new Date(item.interval_start_time as string).getTime(),
        item.failure_count ?? 0,
      ]),
    });
  }

  series.value = value;
}

type GetOperationalDataIntervalsParams = {
  records_from: string;
  records_to: string;
  interval: number;
  security_server_type?: 'Client' | 'Producer';
  member_id?: string;
  service_id?: string;
};

type TrafficFilter = {
  startDate: Date;
  startTime: string;
  endDate: Date;
  endTime: string;
  client?: string;
  service?: string;
  exchangeRole?: string;
  status?: string;
};

type TrafficSeries = {
  name: string;
  color: string;
  data: [number, number][];
};

function getSecurityServerType(exchangeRole?: string) {
  if (exchangeRole === 'Producer') {
    return 'Producer';
  } else if (exchangeRole === 'Client') {
    return 'Client';
  }
  return undefined;
}

function toQueryParams(
  filter: TrafficFilter,
): GetOperationalDataIntervalsParams {
  return {
    records_from: dateWithTime(
      filter.startDate,
      filter.startTime,
    ).toISOString(),
    records_to: dateWithTime(filter.endDate, filter.endTime)
      .second(59)
      .millisecond(999)
      .toISOString(),
    interval: 60,
    security_server_type: getSecurityServerType(filter.exchangeRole),
    member_id: filter.service ? undefined : filter.client,
    service_id: filter.service,
  };
}

function dateWithTime(date: Date, time: string): Dayjs {
  const [hours, minutes] = time.split(':').map(Number);
  return dayjs(date).hour(hours).minute(minutes).second(0).millisecond(0);
}
</script>

<style scoped lang="scss">
.range-separator {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.range-separator::before {
  content: '-';
}

.time-input {
  width: 5rem;
}

.date-input {
  width: 10rem;
}
</style>
