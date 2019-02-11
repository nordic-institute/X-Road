<template>
  <div class="content">
    <v-flex mb-4>
      <h1 class="display-1 mb-3">Name (subsystem)</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="details">Details</v-tab>
      <v-tab key="serviceClients">Service Clients</v-tab>
      <v-tab key="services">Services</v-tab>
      <v-tab key="internalServers">Internal Servers</v-tab>
      <v-tab key="localGroups">Local Groups</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="details">
        <v-card flat>
          <table class="detail-table">
            <tr>
              <td>Member Name</td>
              <td>{{subsystem.name}}</td>
            </tr>
            <tr>
              <td>Member Class</td>
              <td>{{subsystem.class}}</td>
            </tr>
            <tr>
              <td>Member Code</td>
              <td>{{subsystem.memberCode}}</td>
            </tr>
            <tr>
              <td>Subsystem Code</td>
              <td>{{subsystem.subsystemCode}}</td>
            </tr>
          </table>
        </v-card>

        <v-card flat>
          <table class="certificate-table">
            <tr>
              <th>Certificate</th>
              <th>Serial Number</th>
              <th>State</th>
              <th>Expires</th>
            </tr>
            <tr>
              <td>{{certificate.name}}</td>
              <td>{{certificate.serialNumber}}</td>
              <td>{{certificate.state}}</td>
              <td>{{certificate.expires}}</td>
            </tr>
          </table>
        </v-card>
      </v-tab-item>
      <v-tab-item key="serviceClients">
        <v-card flat>
          <v-card
            class="mt-5 text-xs-center"
            color="grey lighten-2"
            height="250px"
          >Service Clients placeholder</v-card>
        </v-card>
      </v-tab-item>
      <v-tab-item key="services">
        <v-card flat>
          <v-card-text>Services placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="internalServers">
        <v-card flat>
          <v-card-text>Internal servers placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="localGroups">
        <v-card flat>
          <v-card-text>Local Groups placeholder</v-card-text>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  data() {
    return {
      tab: null,
      subsystem: {
        name: 'NIIS',
        class: 'Org',
        memberCode: '1111',
        subsystemCode: 'Library',
      },
      certificate: {
        name: 'XRoad Test CA CN',
        serialNumber: '4',
        state: 'in use',
        expires: '2033-06-02',
      },
      items: ['boo', 'lorem', 'things'],
      text:
        'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.',
    };
  },
});
</script>

<style lang="scss" >
@import '../assets/tables';

.xr-tabs {
  border-bottom: #9b9b9b solid 1px;
}

.content {
  width: 100%;
}
</style>

