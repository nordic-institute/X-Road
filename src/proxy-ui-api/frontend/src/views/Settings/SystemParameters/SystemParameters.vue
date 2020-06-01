<template>
  <div class="pt-10">
    <v-card flat class="xrd-card">
      <v-container>
        <v-row no-gutters v-if="hasPermission(permissions.VIEW_ANCHOR)">
          <v-col
            ><h3>
              {{ $t('systemParameters.configurationAnchor.title') }}
            </h3></v-col
          >
          <v-col class="text-right">
            <large-button
              data-test="system-parameters-configuration-anchor-download-button"
              @click="downloadAnchor"
              :loading="downloadingAnchor"
              outlined
              :requires-permission="permissions.DOWNLOAD_ANCHOR"
            >
              {{ $t('systemParameters.configurationAnchor.action.download') }}
            </large-button>
            <upload-configuration-anchor-dialog
              @uploaded="fetchConfigurationAnchor"
            />
          </v-col>
        </v-row>
        <v-row no-gutters v-if="hasPermission(permissions.VIEW_ANCHOR)">
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>
                    {{
                      $t(
                        'systemParameters.configurationAnchor.table.header.distinguishedName',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.configurationAnchor.table.header.generated',
                      )
                    }}
                  </th>
                </tr>
              </thead>
              <tbody
                data-test="system-parameters-configuration-anchor-table-body"
              >
                <tr>
                  <td>{{ this.configuratonAnchor.hash | colonize }}</td>
                  <td>
                    {{ this.configuratonAnchor.created_at | formatDateTime }}
                  </td>
                </tr>
              </tbody>
            </table>
          </v-col>
        </v-row>
        <v-row
          no-gutters
          class="mt-10"
          v-if="hasPermission(permissions.VIEW_TSPS)"
        >
          <v-col
            ><h3>
              {{ $t('systemParameters.timestampingServices.title') }}
            </h3></v-col
          >
          <v-col class="text-right">
            <add-timestamping-service-dialog
              :configured-timestamping-services="configuredTimestampingServices"
              @added="fetchConfiguredTimestampingServiced"
            />
          </v-col>
        </v-row>
        <v-row no-gutters v-if="hasPermission(permissions.VIEW_TSPS)">
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>
                    {{
                      $t(
                        'systemParameters.timestampingServices.table.header.timestampingService',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.timestampingServices.table.header.serviceURL',
                      )
                    }}
                  </th>
                  <th>&nbsp;</th>
                </tr>
              </thead>
              <tbody
                data-test="system-parameters-timestamping-services-table-body"
              >
                <timestamping-service-row
                  v-for="timestampingService in this
                    .configuredTimestampingServices"
                  :key="timestampingService.url"
                  :timestamping-service="timestampingService"
                  @deleted="fetchConfiguredTimestampingServiced"
                />
              </tbody>
            </table>
          </v-col>
        </v-row>
        <v-row
          no-gutters
          class="mt-10"
          v-if="hasPermission(permissions.GENERATE_AUTH_CERT_REQ)"
        >
          <v-col
            ><h3>
              {{ $t('systemParameters.approvedCertificateAuthorities.title') }}
            </h3></v-col
          >
        </v-row>
        <v-row
          no-gutters
          v-if="hasPermission(permissions.GENERATE_AUTH_CERT_REQ)"
        >
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.header.distinguishedName',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.header.ocspResponse',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.header.expires',
                      )
                    }}
                  </th>
                </tr>
              </thead>
              <tbody data-test="system-parameters-approved-ca-table-body">
                <tr
                  v-for="approvedCA in orderedCertificateAuthorities"
                  :key="approvedCA.path"
                >
                  <td
                    :class="{
                      'interm-ca': !approvedCA.top_ca,
                      'root-ca': approvedCA.top_ca,
                    }"
                  >
                    {{ approvedCA.subject_distinguished_name }}
                  </td>
                  <td v-if="approvedCA.top_ca">
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.ocspResponse.NOT_AVAILABLE',
                      )
                    }}
                  </td>
                  <td v-if="!approvedCA.top_ca">
                    {{
                      $t(
                        `systemParameters.approvedCertificateAuthorities.table.ocspResponse.${approvedCA.ocsp_response}`,
                      )
                    }}
                  </td>
                  <td>{{ approvedCA.not_after | formatDate }}</td>
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
import {
  Anchor,
  CertificateAuthority,
  TimestampingService,
} from '@/openapi-types';
import * as api from '@/util/api';
import { Permissions } from '@/global';
import TimestampingServiceRow from '@/views/Settings/SystemParameters/TimestampingServiceRow.vue';
import UploadConfigurationAnchorDialog from '@/views/Settings/SystemParameters/UploadConfigurationAnchorDialog.vue';
import { saveResponseAsFile } from '@/util/helpers';
import AddTimestampingServiceDialog from '@/views/Settings/SystemParameters/AddTimestampingServiceDialog.vue';

export default Vue.extend({
  components: {
    LargeButton,
    TimestampingServiceRow,
    UploadConfigurationAnchorDialog,
    AddTimestampingServiceDialog,
  },
  data() {
    return {
      configuratonAnchor: {} as Anchor,
      downloadingAnchor: false,
      configuredTimestampingServices: [] as TimestampingService[],
      certificateAuthorities: [] as CertificateAuthority[],
      permissions: Permissions,
    };
  },
  computed: {
    orderedCertificateAuthorities(): CertificateAuthority[] {
      return this.certificateAuthorities.sort((authorityA, authorityB) =>
        authorityA.path.localeCompare(authorityB.path),
      );
    },
  },
  methods: {
    hasPermission(permission: Permissions): boolean {
      return this.$store.getters.hasPermission(permission);
    },
    async fetchConfigurationAnchor() {
      return api
        .get('/system/anchor')
        .then((resp) => (this.configuratonAnchor = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    async fetchConfiguredTimestampingServiced() {
      return api
        .get('/system/timestamping-services')
        .then((resp) => (this.configuredTimestampingServices = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    async fetchApprovedCertificateAuthorities() {
      return api
        .get('/certificate-authorities?include_intermediate_cas=true')
        .then((resp) => (this.certificateAuthorities = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    downloadAnchor(): void {
      this.downloadingAnchor = true;
      api
        .get('/system/anchor/download', { responseType: 'blob' })
        .then((res) => saveResponseAsFile(res, 'configuration-anchor.xml'))
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => (this.downloadingAnchor = false));
    },
  },
  created(): void {
    if (this.hasPermission(Permissions.VIEW_ANCHOR)) {
      this.fetchConfigurationAnchor();
    }

    if (this.hasPermission(Permissions.VIEW_TSPS)) {
      this.fetchConfiguredTimestampingServiced();
    }

    if (this.hasPermission(Permissions.GENERATE_AUTH_CERT_REQ)) {
      this.fetchApprovedCertificateAuthorities();
    }
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';

h3 {
  color: lighten($XRoad-Black, 10);
  font-weight: 500;
}

table {
  font-size: 0.9rem;
}

tr th {
  font-weight: 500;
  color: lighten($XRoad-Black, 20);
}

tr td {
  color: $XRoad-Black;
  font-weight: normal !important;
}

tr td:last-child {
  width: 1%;
  white-space: nowrap;
}

.root-ca {
  font-weight: bold !important;
}

.interm-ca {
  font-weight: normal !important;
  padding-left: 2rem !important;
}
</style>
