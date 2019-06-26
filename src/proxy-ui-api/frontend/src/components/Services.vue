<template>
  <div class="wrapper">
    <div class="search-row">
      <v-text-field
        v-model="search"
        :label="$t('services.service')"
        single-line
        hide-details
        class="search-input"
      >
        <v-icon slot="append" small>fas fa-search</v-icon>
      </v-text-field>

      <div>
        <v-btn
          v-if="showAdd"
          color="primary"
          @click="addRest"
          outline
          round
          class="rounded-button elevation-0 rest-button"
        >{{$t('services.addRest')}}</v-btn>

        <v-btn
          v-if="showAdd"
          color="primary"
          @click="addWsdl"
          outline
          round
          class="ma-0 rounded-button elevation-0"
        >{{$t('services.addWsdl')}}</v-btn>
      </div>
    </div>

    <div v-if="filtered.length < 1">No matching records</div>

    <transition-group name="fade">
      <expandable
        v-for="serviceDesc in filtered"
        v-bind:key="serviceDesc.id"
        class="expandable"
        @open="descOpen(serviceDesc.id)"
        @close="descClose(serviceDesc.id)"
        :isOpen="isExpanded(serviceDesc.id)"
      >
        <template v-slot:action>
          <v-switch class="switch" @change="switchChanged($event, serviceDesc)"></v-switch>
        </template>

        <template v-slot:link>
          <div @click="descriptionClick(serviceDesc)">{{serviceDesc.type}} ({{serviceDesc.url}})</div>
        </template>

        <template v-slot:content>
          <div>
            <div v-if="serviceDesc.type.toLowerCase() === 'wsdl'" class="refresh-row">
              <div class="refresh-time">{{serviceDesc.refreshed_date | formatDateTime}}</div>
              <v-btn
                small
                outline
                round
                color="primary"
                class="xr-small-button xr-table-button refresh-button"
                @click="refreshWsdl(serviceDesc)"
              >{{$t('action.refresh')}}</v-btn>
            </div>

            <table class="xrd-table members-table">
              <thead>
                <tr>
                  <th>{{$t('services.serviceCode')}}</th>
                  <th>{{$t('services.url')}}</th>
                  <th>{{$t('services.timeout')}}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="service in serviceDesc.services" v-bind:key="service.id">
                  <td class="service-code" @click="serviceClick(service)">{{service.code}}</td>
                  <td>
                    <v-icon small :color="getServiceIconClass(service)">{{getServiceIcon(service)}}</v-icon>
                    {{service.url}}
                  </td>
                  <td>{{service.timeout}}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </expandable>
    </transition-group>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import Expandable from '@/components/Expandable.vue';

import _ from 'lodash';

export default Vue.extend({
  components: { Expandable },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      search: '',
      expanded: [],
      serviceDescriptions: [
        {
          url: 'http://dev.xroad.rocks/services.wsdl',

          type: 'WSDL',
          id: '12323123',
          disabled: true,
          disabled_notice: 'default_disabled_service_notice',
          refreshed_date: '2018-12-15T00:00:00.001Z',
          services: [
            {
              id: 'uniq65ueId',
              code: 'sslauth true',
              timeout: 60,
              ssl_auth: true,
              url: 'https://domain.com/service',
            },
            {
              id: 'uniq75ueId',
              code: 'sslauth false',
              timeout: 60,
              ssl_auth: false,
              url: 'https://domain.com/service',
            },
            {
              id: 'un65iqueId',
              code: 'sslauth gone',
              timeout: 60,
              url: 'https://domain.com/service',
            },
          ],
          client_id: 'FI:GOV:123:ABC',
        },

        {
          url: 'http://dev.xroad.kivittaa/services.wsdl',
          type: 'WSDL',
          id: '98723123',
          disabled: true,
          disabled_notice: 'default_disabled_service_notice',
          refreshed_date: '2018-12-15T00:00:00.001Z',
          services: [
            {
              id: 'uniqueId',
              code: 'jhgjhghjg',
              timeout: 60,
              ssl_auth: true,
              url: 'https://domain.com/service',
            },
          ],
          client_id: 'FI:GOV:123:ABC',
        },

        {
          url: 'http://dev.xroad.rocks/services.rest',
          type: 'REST',
          disabled: false,
          id: 'oi8323123',
          disabled_notice: 'default_disabled_service_notice',
          refreshed_date: '2018-12-15T00:00:00.001Z',
          services: [
            {
              id: 'uniikkio12',
              code: 'eiheiheihei',
              timeout: 60,
              ssl_auth: true,
              url: 'https://domain.com/service',
            },
            {
              id: 'uniqueId8976',
              code: 'pooopooiiuu',
              timeout: 60,
              ssl_auth: true,
              url: 'https://domain.com/service',
            },
          ],
          client_id: 'FI:GOV:123:ABC',
        },
      ],
    };
  },
  computed: {
    ...mapGetters(['client']),
    showAdd(): boolean {
      return true;
    },
    filtered(): any {
      const arr = _.cloneDeep(this.serviceDescriptions);

      if (!this.search) {
        return arr;
      }

      // Clean the search string
      const mysearch = this.search.toString().toLowerCase();
      if (mysearch.trim() === '') {
        return arr;
      }

      const filtered = arr.filter((element: any) => {
        return element.services.find((service: any) => {
          return service.code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });
      });

      filtered.forEach(function(element) {
        const filteredServices = element.services.filter((service: any) => {
          return service.code
            .toString()
            .toLowerCase()
            .includes(mysearch);
        });

        element.services = filteredServices;
      });

      return filtered;
    },
  },
  methods: {
    descriptionClick(desc: any): void {
      // TODO: will be implemented on later task
      console.log(desc);
    },
    serviceClick(service: any): void {
      // TODO: will be implemented on later task
      console.log(service);
    },
    switchChanged(event: any, serviceDesc: any): void {
      // TODO: will be implemented on later task
      console.log(event, serviceDesc.id);
    },

    addRest(): void {
      // TODO: will be implemented on later task
      console.log('add rest');
    },

    addWsdl(): void {
      // TODO: will be implemented on later task
      console.log('add wsdl');
    },

    refreshWsdl(wsdl: any): void {
      // TODO: will be implemented on later task
      console.log('refresh wsdl');
    },

    getServiceIcon(service: any): string {
      console.log(service.ssl_auth);
      switch (service.ssl_auth) {
        case undefined:
          return 'lock_open';
        case true:
          return 'lock';
        case false:
          return 'lock';
        default:
          return '';
      }
    },

    getServiceIconClass(service: any): string {
      switch (service.ssl_auth) {
        case undefined:
          return '';
        case true:
          return '#00e500';
        case false:
          return '#ffd200';
        default:
          return '';
      }
    },

    descClose(descId: string) {
      console.log('close');

      const index = this.expanded.findIndex((element: any) => {
        return element === descId;
      });

      console.log(index);

      if (index >= 0) {
        this.expanded.splice(index, 1);
      }

      console.log(this.expanded);
    },
    descOpen(descId: string) {
      console.log('open');
      console.log(this.expanded);

      const index = this.expanded.findIndex((element: any) => {
        return element === descId;
      });

      console.log(index);

      if (index === -1) {
        this.expanded.push(descId);
      }

      console.log(this.expanded);
    },
    isExpanded(descId: string) {
      return this.expanded.includes(descId);
    },
  },
});
</script>

<style lang="scss" >
.wrapper {
  margin-top: 20px;
}

.search-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-top: 40px;
  margin-bottom: 24px;
}

.rest-button {
  margin: 0;
  margin-right: 20px;
}

.search-input {
  max-width: 300px;
}

.refresh-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: flex-end;
  width: 100%;
  margin-top: 24px;
}

.refresh-time {
  margin-right: 28px;
}

.expandable {
  margin-bottom: 10px;
}

.service-code {
  cursor: pointer;
  text-decoration: underline;
}
</style>

