<template>
  <div class="content">
    <v-flex mb-4>
      <h1 class="display-1 mb-3">Name (Member)</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="details">Details</v-tab>
      <v-tab key="internalServers">Internal Servers</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="details">
        <v-card flat>
          <table class="detail-table">
            <tr>
              <td>Member Name</td>
              <td>{{member.name}}</td>
            </tr>
            <tr>
              <td>Member Class</td>
              <td>{{member.class}}</td>
            </tr>
            <tr>
              <td>Member Code</td>
              <td>{{member.code}}</td>
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
      <v-tab-item key="internalServers">
        <v-card flat>
          <v-card-text>Internal servers placeholder</v-card-text>
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
      member: {
        name: 'NIIS',
        class: 'Org',
        code: '1111',
      },
      certificate: {
        name: 'XRoad Test CA CN',
        serialNumber: '4',
        state: 'in use',
        expires: '2033-06-02',
      },
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

