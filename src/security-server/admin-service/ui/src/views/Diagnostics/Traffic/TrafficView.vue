<template>
  <v-card variant="flat">
    <v-card-text class="xrd-card-text">
      <v-row dense>
        <v-col cols="3">
          <xrd-form-label
            :label-text="$t('diagnostics.traffic.period')"
            :help-text="$t('diagnostics.traffic.periodInfo')"
          />
        </v-col>
        <v-col cols="auto">
          <v-date-input
            v-model="filters.startDate"
            class="date-input"
            :label="$t('diagnostics.traffic.date')"
            prepend-inner-icon="$calendar"
            prepend-icon=""
          ></v-date-input>
        </v-col>
        <v-col cols="auto">
          <v-text-field
            v-model="filters.startTime"
            v-maska="'##:##'"
            class="time-input"
            :label="$t('diagnostics.traffic.time')"
          ></v-text-field>
        </v-col>
        <v-col cols="auto"><span class="range-separator"></span></v-col>
        <v-col cols="auto">
          <v-date-input
            v-model="filters.endDate"
            class="date-input"
            :label="$t('diagnostics.traffic.date')"
            prepend-inner-icon="$calendar"
            prepend-icon=""
          ></v-date-input>
        </v-col>
        <v-col cols="auto">
          <v-text-field
            v-model="filters.endTime"
            v-maska="'##:##'"
            class="time-input"
            :label="$t('diagnostics.traffic.time')"
          ></v-text-field>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col cols="3">
          <xrd-form-label
            :label-text="$t('diagnostics.traffic.party')"
            :help-text="$t('diagnostics.traffic.partyInfo')"
          />
        </v-col>
        <v-col cols="5">
          <v-select
            v-model="filters.client"
            :label="$t('diagnostics.traffic.client')"
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
            :label="$t('diagnostics.traffic.service')"
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
            :label-text="$t('diagnostics.traffic.exchangeRole')"
            :help-text="$t('diagnostics.traffic.exchangeRoleInfo')"
          />
        </v-col>
        <v-col>
          <v-select
            v-model="filters.exchangeRole"
            :label="$t('diagnostics.traffic.exchangeRole')"
            clearable
            :items="[
              $t('diagnostics.traffic.producer'),
              $t('diagnostics.traffic.client'),
            ]"
          >
          </v-select>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col cols="3">
          <xrd-form-label
            :label-text="$t('diagnostics.traffic.status')"
            :help-text="$t('diagnostics.traffic.statusInfo')"
          />
        </v-col>
        <v-col>
          <v-select
            v-model="filters.status"
            :label="$t('diagnostics.traffic.status')"
            clearable
            :items="[
              { title: $t('diagnostics.traffic.success'), value: true },
              { title: $t('diagnostics.traffic.failure'), value: false },
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
</template>

<script lang="ts" setup>
import { reactive, Reactive, Ref, ref, watch } from 'vue';
import { VDateInput } from 'vuetify/labs/VDateInput';
import { useNotifications } from '@/store/modules/notifications';
import axios from 'axios';
import dayjs, { Dayjs } from 'dayjs';
import { vMaska } from 'maska/vue';
import { Colors } from '@niis/shared-ui';
import { OperationalDataInterval, Service } from '@/openapi-types';
import { useServices } from '@/store/modules/services';
import { useClients } from '@/store/modules/clients';
import TrafficChart, {
  TrafficSeries,
} from '@/views/Diagnostics/Traffic/TrafficChart.vue';
import { useI18n } from 'vue-i18n';

const { showError } = useNotifications();
const { t } = useI18n();

const clientsStore = useClients();
const clientsLoading = ref(true);
clientsStore
  .fetchClients()
  .catch(showError)
  .finally(() => {
    clientsLoading.value = false;
  });

const series: Ref<Array<TrafficSeries>> = ref([]);
const services: Ref<Array<Service>> = ref([]);
const servicesLoading = ref(false);

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
      name: t('diagnostics.traffic.successfulRequests'),
      color: Colors.Success100,
      data: data.map((item) => [
        new Date(item.interval_start_time as string).getTime(),
        item.success_count ?? 0,
      ]),
    });
  }
  if (!(filter.status ?? false)) {
    value.push({
      name: t('diagnostics.traffic.failedRequests'),
      color: Colors.Error,
      data: data.map((item) => [
        new Date(item.interval_start_time as string).getTime(),
        item.failure_count ?? 0,
      ]),
    });
  }
  series.value = value;
}

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
  const start = dateWithTime(filter.startDate, filter.startTime);
  const end = dateWithTime(filter.endDate, filter.endTime)
    .second(59)
    .millisecond(999);
  return {
    records_from: start.toISOString(),
    records_to: end.toISOString(),
    interval: calculateInterval(start, end),
    security_server_type: getSecurityServerType(filter.exchangeRole),
    member_id: filter.service ? undefined : filter.client,
    service_id: filter.service,
  };
}

function dateWithTime(date: Date, time: string): Dayjs {
  const [hours, minutes] = time.split(':').map(Number);
  return dayjs(date).hour(hours).minute(minutes).second(0).millisecond(0);
}

function calculateInterval(start: Dayjs, end: Dayjs): number {
  const startTime = dayjs(start);
  const endTime = dayjs(end);
  const diffMinutes = endTime.diff(startTime, 'minute');

  const intervals = [1, 5, 15, 60, 1440];

  for (const i of intervals) {
    if (diffMinutes / i < 500) {
      return i;
    }
  }
  return intervals[intervals.length - 1];
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
