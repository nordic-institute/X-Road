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
  <div class="pt-6">
    <h2 class="xrd-view-title pb-6">{{ $t('systemParameters.title') }}</h2>

    <v-card flat class="xrd-card">
      <v-container>
        <v-row
          no-gutters
          class="mt-2"
          v-if="hasPermission(permissions.VIEW_ANCHOR)"
        >
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
              <v-icon>mdi-arrow-down</v-icon>
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
      </v-container>
    </v-card>
    <v-card flat class="xrd-card">
      <v-container>
        <v-row
          no-gutters
          class="mt-2"
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
      </v-container>
    </v-card>
    <v-card flat class="xrd-card">
      <v-container>
        <v-row
          no-gutters
          class="mt-2"
          v-if="
            hasPermission(permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)
          "
        >
          <v-col
            ><h3>
              {{ $t('systemParameters.approvedCertificateAuthorities.title') }}
            </h3></v-col
          >
        </v-row>
        <v-row
          no-gutters
          v-if="
            hasPermission(permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)
          "
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
      const temp = this.certificateAuthorities;

      return temp.sort((authorityA, authorityB) =>
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
        .get<Anchor>('/system/anchor')
        .then((resp) => (this.configuratonAnchor = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    async fetchConfiguredTimestampingServiced() {
      return api
        .get<TimestampingService[]>('/system/timestamping-services')
        .then((resp) => (this.configuredTimestampingServices = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    async fetchApprovedCertificateAuthorities() {
      return api
        .get<CertificateAuthority[]>(
          '/certificate-authorities?include_intermediate_cas=true',
        )
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

    if (this.hasPermission(Permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)) {
      this.fetchApprovedCertificateAuthorities();
    }
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';

h3 {
  color: #211e1e;
  font-size: 18px;
  font-weight: bold;
  letter-spacing: 0;
  line-height: 24px;
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

.xrd-card {
  margin-bottom: 24px;
}
</style>
