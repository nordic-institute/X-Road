<template>
  <div class="pt-2">
    <v-card flat class="xrd-card">
      <v-container>
        <v-row v-if="hasPermission(permissions.VIEW_ANCHOR)">
          <v-col><h3>{{$t('systemParameters.configurationAnchor.title')}}</h3></v-col>
          <v-col class="text-right">
            <large-button data-test="system-parameters-configuration-anchor-download-button" outlined :requires-permission="permissions.DOWNLOAD_ANCHOR">
              {{$t('systemParameters.configurationAnchor.action.download')}}
            </large-button>
            <large-button data-test="system-parameters-configuration-anchor-upload-button" outlined :requires-permission="permissions.UPLOAD_ANCHOR" class="ml-5">
              {{$t('systemParameters.configurationAnchor.action.upload')}}
            </large-button>
          </v-col>
        </v-row>
        <v-row v-if="hasPermission(permissions.VIEW_ANCHOR)">
          <v-col>
            <table class="xrd-table">
              <thead>
              <tr>
                <th>{{$t('systemParameters.configurationAnchor.table.header.distinguishedName')}}</th>
                <th>{{$t('systemParameters.configurationAnchor.table.header.generated')}}</th>
              </tr>
              </thead>
              <tbody data-test="system-parameters-configuration-anchor-table-body">
              <tr>
                <td>{{this.configuratonAnchor.hash}}</td>
                <td>{{this.configuratonAnchor.created_at | formatDateTime}}</td>
              </tr>
              </tbody>
            </table>
          </v-col>
        </v-row>
        <v-row class="mt-5" v-if="hasPermission(permissions.VIEW_TSPS)">
          <v-col><h3>{{$t('systemParameters.timestampingServices.title')}}</h3></v-col>
          <v-col class="text-right">
            <large-button data-test="system-parameters-timestamping-services-add-button" outlined :requires-permission="permissions.ADD_TSP">
              {{$t('systemParameters.timestampingServices.action.add')}}
            </large-button>
          </v-col>
        </v-row>
        <v-row v-if="hasPermission(permissions.VIEW_TSPS)">
          <v-col>
            <table class="xrd-table">
              <thead>
              <tr>
                <th>{{$t('systemParameters.timestampingServices.table.header.certificateHash')}}</th>
                <th>{{$t('systemParameters.timestampingServices.table.header.serviceURL')}}</th>
                <th>&nbsp;</th>
              </tr>
              </thead>
              <tbody data-test="system-parameters-timestamping-services-table-body">
              <tr v-for="timestampingService in this.configuredTimestampingServices" :key="timestampingService.url">
                <td>{{timestampingService.name}}</td>
                <td>{{timestampingService.url}}</td>
                <td>
                  <small-button data-test="system-parameters-timestamping-services-delete-button" outlined :requires-permission="permissions.DELETE_TSP">
                    {{$t('systemParameters.timestampingServices.table.action.delete')}}
                  </small-button>
                </td>
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
              <tbody data-test="system-parameters-approved-ca-table-body">
              <tr v-for="approvedCA in approvedCertificateAuthorities" :key="approvedCA.path">
                <td>{{approvedCA.subject_distinguished_name}}</td>
                <td>{{approvedCA.ocsp_response}}</td>
                <td>{{approvedCA.not_after | formatDate}}</td>
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
import { Anchor, CertificateAuthority, TimestampingService } from '@/types';
import * as api from '@/util/api';
import { Permissions } from '@/global';

export default Vue.extend({
  components: {
    LargeButton,
    SmallButton,
  },
  data() {
    return {
      configuratonAnchor: {} as Anchor,
      configuredTimestampingServices: [] as TimestampingService[],
      approvedCertificateAuthorities: [] as CertificateAuthority[],
      permissions: Permissions,
    };
  },
  methods: {
    hasPermission(permission: Permissions): boolean {
      return this.$store.getters.hasPermission(permission);
    },
    async fetchConfigurationAnchor() {
      return api.get('/system/anchor')
        .then((resp) => this.configuratonAnchor = resp.data)
        .catch((error) => this.$bus.$emit('show-error', error.message));
    },
    async fetchConfiguredTimestampingServiced() {
      return api.get('/system/timestamping-services')
        .then((resp) => this.configuredTimestampingServices = resp.data)
        .catch((error) => this.$bus.$emit('show-error', error.message));
    },
    async fetchApprovedCertificateAuthorities() {
      return api.get('/certificate-authorities?include_intermediate_cas=true')
        .then((resp) => this.approvedCertificateAuthorities = resp.data)
        .catch((error) => this.$bus.$emit('show-error', error.message));
    },
  },
  created(): void {
    this.fetchConfigurationAnchor();
    this.fetchConfiguredTimestampingServiced();
    this.fetchApprovedCertificateAuthorities();
  },
});
</script>

<style lang="scss" scoped>
  @import "../../../assets/colors";
  @import '../../../assets/tables';

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
