<template>
  <div class="pt-2">
    <v-card flat class="xrd-card">
      <v-container>
        <v-row>
          <v-col><h3>{{$t('systemParameters.configurationAnchor.title')}}</h3></v-col>
          <v-col class="text-right">
            <large-button outlined>{{$t('systemParameters.configurationAnchor.action.download')}}</large-button>
            <large-button outlined class="ml-5">{{$t('systemParameters.configurationAnchor.action.upload')}}</large-button>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <table class="xrd-table">
              <thead>
              <tr>
                <th>{{$t('systemParameters.configurationAnchor.table.header.distinguishedName')}}</th>
                <th>{{$t('systemParameters.configurationAnchor.table.header.generated')}}</th>
              </tr>
              </thead>
              <tbody>
              <tr>
                <td>hash</td>
                <td>UTC 2019-01-01 11:11:11</td>
              </tr>
              </tbody>
            </table>
          </v-col>
        </v-row>
        <v-row class="mt-5">
          <v-col><h3>{{$t('systemParameters.timestampingServices.title')}}</h3></v-col>
          <v-col class="text-right">
            <large-button outlined>{{$t('systemParameters.timestampingServices.action.add')}}</large-button>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <table class="xrd-table">
              <thead>
              <tr>
                <th>{{$t('systemParameters.timestampingServices.table.header.certificateHash')}}</th>
                <th>{{$t('systemParameters.timestampingServices.table.header.serviceURL')}}</th>
                <th>&nbsp;</th>
              </tr>
              </thead>
              <tbody>
              <tr>
                <td>X-Road Test TSA CN</td>
                <td>http://some-xroad-url.com</td>
                <td><small-button outlined>{{$t('systemParameters.timestampingServices.table.action.delete')}}</small-button></td>
              </tr>
              </tbody>
            </table>
          </v-col>
        </v-row>
        <v-row class="mt-5">
          <v-col><h3>{{$t('systemParameters.approvedCertificateAuthorities.title')}}</h3></v-col>
        </v-row>
        <v-row>
          <v-col>
            <table class="xrd-table">
              <thead>
              <tr>
                <th>{{$t('systemParameters.approvedCertificateAuthorities.table.header.distinguishedName')}}</th>
                <th>{{$t('systemParameters.approvedCertificateAuthorities.table.header.ocspResponse')}}</th>
                <th>{{$t('systemParameters.approvedCertificateAuthorities.table.header.expires')}}</th>
              </tr>
              </thead>
              <tbody>
              <tr>
                <td>/C=FI/O=X-Road Test/OU=X-Road Test CA OU/CN=X-Road Test CA CN</td>
                <td>N/A</td>
                <td>2020-03-11</td>
              </tr>
              </tbody>
            </table>
          </v-col>
        </v-row>
      </v-container>
    </v-card>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue';
  import LargeButton from '@/components/ui/LargeButton.vue';
  import SmallButton from '@/components/ui/SmallButton.vue';

  export default Vue.extend({
    components: {
      LargeButton,
      SmallButton,
    },
  });
</script>

<style lang="scss" scoped>
  @import "../../assets/colors";
  @import '../../assets/tables';

  h3 {
    color: $XRoad-Grey60;
  }

  tr th {
    font-weight: 500;
  }

  tr td {
    color: $XRoad-Black;
  }

  tr td:last-child {
    width: 1%;
    white-space: nowrap;
  }

</style>
