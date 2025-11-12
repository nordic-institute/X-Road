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
  <XrdCard class="mb-4">
    <v-container fluid>
      <v-row dense>
        <v-col cols="2" align-self="end">
          <v-date-input
            v-model="filters.startDate"
            class="xrd date-input"
            prepend-inner-icon="$calendar"
            prepend-icon=""
            hide-details
            data-test="period-start-date"
            :label="$t('diagnostics.traffic.date')"
          />
        </v-col>
        <v-col cols="1" align-self="end">
          <v-text-field
            v-model="filters.startTime"
            v-maska="'##:##'"
            class="xrd time-input"
            hide-details
            :label="$t('diagnostics.traffic.time')"
            data-test="period-start-time"
          />
        </v-col>
        <v-col cols="2"><span class="range-separator"></span></v-col>
        <v-col cols="2" align-self="end">
          <v-date-input
            v-model="filters.endDate"
            class="xrd date-input"
            :label="$t('diagnostics.traffic.date')"
            prepend-inner-icon="$calendar"
            prepend-icon=""
            hide-details
            data-test="period-end-date"
          />
        </v-col>
        <v-col cols="1" align-self="end">
          <v-text-field
            v-model="filters.endTime"
            v-maska="'##:##'"
            class="xrd time-input"
            hide-details
            :label="$t('diagnostics.traffic.time')"
            data-test="period-end-time"
          />
        </v-col>
        <v-col cols="4">
          <v-sheet
            class="pa-3 rounded-lg body-regular font-weight-regular"
            color="surface-container-high"
          >
            <XrdFormLabel
              :label-text="$t('diagnostics.traffic.period')"
              :help-text="$t('diagnostics.traffic.periodInfo')"
            />
          </v-sheet>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col cols="4" align-self="end">
          <v-select
            v-model="filters.client"
            data-test="select-client"
            class="xrd"
            item-title="id"
            item-value="id"
            hide-details
            clearable
            :items="clientsStore.clients"
            :label="$t('diagnostics.traffic.client')"
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
        <v-col cols="4" align-self="end">
          <v-select
            v-model="filters.service"
            data-test="select-service"
            class="xrd"
            item-title="full_service_code"
            item-value="id"
            clearable
            hide-details
            :disabled="!services.length"
            :items="services"
            :label="$t('diagnostics.traffic.service')"
            :loading="servicesLoading"
          >
            <template #item="{ props: itemProps, item }">
              <v-list-item
                v-bind="itemProps"
                :subtitle="item.raw.title"
              ></v-list-item>
            </template>
          </v-select>
        </v-col>
        <v-col cols="4">
          <v-sheet
            class="pa-3 rounded-lg body-regular font-weight-regular"
            color="surface-container-high"
          >
            <XrdFormLabel
              :label-text="$t('diagnostics.traffic.party')"
              :help-text="$t('diagnostics.traffic.partyInfo')"
            />
          </v-sheet>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col align-self="end">
          <v-select
            v-model="filters.exchangeRole"
            data-test="select-exchangeRole"
            class="xrd"
            clearable
            hide-details
            :items="[
              $t('diagnostics.traffic.producer'),
              $t('diagnostics.traffic.client'),
            ]"
            :label="$t('diagnostics.traffic.exchangeRole')"
          >
          </v-select>
        </v-col>
        <v-col cols="4">
          <v-sheet
            class="pa-3 rounded-lg body-regular font-weight-regular"
            color="surface-container-high"
          >
            <XrdFormLabel
              :label-text="$t('diagnostics.traffic.exchangeRole')"
              :help-text="$t('diagnostics.traffic.exchangeRoleInfo')"
            />
          </v-sheet>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col align-self="end">
          <v-select
            v-model="filters.status"
            data-test="select-status"
            class="xrd"
            clearable
            hide-details
            :items="[
              { title: $t('diagnostics.traffic.success'), value: true },
              { title: $t('diagnostics.traffic.failure'), value: false },
            ]"
            :label="$t('diagnostics.traffic.status')"
          >
          </v-select>
        </v-col>
        <v-col cols="4">
          <v-sheet
            class="pa-3 rounded-lg body-regular font-weight-regular"
            color="surface-container-high"
          >
            <XrdFormLabel
              :label-text="$t('diagnostics.traffic.status')"
              :help-text="$t('diagnostics.traffic.statusInfo')"
            />
          </v-sheet>
        </v-col>
      </v-row>
    </v-container>
  </XrdCard>
  <XrdCard>
    <v-container height="20rem" fluid>
      <TrafficChart
        :series="series"
        :loading="seriesLoading && seriesLoadingDebounced"
      />
    </v-container>
  </XrdCard>
</template>

<script lang="ts" setup>
import { reactive, Reactive, Ref, ref, watch } from 'vue';
import { VDateInput } from 'vuetify/labs/VDateInput';
import axios from 'axios';
import dayjs, { Dayjs } from 'dayjs';
import { vMaska } from 'maska/vue';
import {
  useNotifications,
  XrdCard,
  XrdFormLabel,
  useThemeHelper,
} from '@niis/shared-ui';
import { OperationalDataInterval, Service } from '@/openapi-types';
import { useClients } from '@/store/modules/clients';
import TrafficChart, {
  TrafficSeries,
} from '@/views/Diagnostics/Traffic/TrafficChart.vue';
import { useI18n } from 'vue-i18n';
import { debounce } from '@/util/helpers';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

const { addError } = useNotifications();
const { t } = useI18n();
const { colorSuccess, colorError } = useThemeHelper();

const clientsStore = useClients();
const clientsLoading = ref(true);
clientsStore
  .fetchClients()
  .catch(addError)
  .finally(() => {
    clientsLoading.value = false;
  });

const lastFetchedTrafficData: Ref<Array<OperationalDataInterval>> = ref([]);
const series: Ref<Array<TrafficSeries>> = ref([]);
const seriesLoading = ref(false);
const seriesLoadingDebounced = ref(false);

const services: Ref<Array<Service>> = ref([]);
const servicesLoading = ref(false);

const serviceDescriptionsStore = useServiceDescriptions();

const filters: Reactive<TrafficFilter> = reactive({
  startDate: dayjs().subtract(7, 'day').toDate(),
  startTime: '00:00',
  endDate: dayjs().toDate(),
  endTime: '23:59',
});

const debounceLoading = debounce(() => {
  seriesLoadingDebounced.value = true;
}, 300);

function startSeriesLoading() {
  seriesLoadingDebounced.value = false;
  seriesLoading.value = true;
  debounceLoading();
}

function endSeriesLoading() {
  seriesLoadingDebounced.value = false;
  seriesLoading.value = false;
}

function onFiltersChange(filter: TrafficFilter) {
  startSeriesLoading();
  fetchTrafficData(toQueryParams(filter))
    .then((data) => {
      lastFetchedTrafficData.value = data;
      series.value = toChartSeries(filter, data);
    })
    .catch(addError)
    .finally(() => {
      endSeriesLoading();
    });
}

watch(
  filters,
  (newVal, oldVal) => {
    if (!!oldVal && newVal.status !== oldVal.status) {
      onStatusFilterChange(newVal);
    } else {
      onFiltersChange(newVal);
    }
  },
  { deep: true, immediate: true },
);
watch(() => filters.client, fetchServices);

function fetchServices(client?: string) {
  services.value = [];
  filters.service = undefined;

  if (client) {
    servicesLoading.value = true;
    serviceDescriptionsStore
      .fetchServiceDescriptions(client)
      .then(() => {
        services.value = serviceDescriptionsStore.serviceDescriptions.flatMap(
          (sd) => sd.services,
        );
      })
      .catch(addError)
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

function onStatusFilterChange(filter: TrafficFilter) {
  series.value = toChartSeries(filter, lastFetchedTrafficData.value);
}

function toChartSeries(
  filter: TrafficFilter,
  data: OperationalDataInterval[],
): TrafficSeries[] {
  const value: TrafficSeries[] = [];

  if (filter.status ?? true) {
    value.push({
      name: t('diagnostics.traffic.successfulRequests'),
      color: colorSuccess.value,
      data: data.map((item) => [
        new Date(item.interval_start_time as string).getTime(),
        item.success_count ?? 0,
      ]),
    });
  }
  if (!(filter.status ?? false)) {
    value.push({
      name: t('diagnostics.traffic.failedRequests'),
      color: colorError.value,
      data: data.map((item) => [
        new Date(item.interval_start_time as string).getTime(),
        item.failure_count ?? 0,
      ]),
    });
  }
  return value;
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
</style>
