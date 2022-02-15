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
  <div>
    <v-card flat>
      <table v-if="client && !clientLoading" class="xrd-table detail-table">
        <tr>
          <td>{{ $t('client.memberName') }}</td>
          <td class="identifier-wrap">{{ client.member_name }}</td>
        </tr>
        <tr>
          <td>{{ $t('client.memberClass') }}</td>
          <td class="identifier-wrap">{{ client.member_class }}</td>
        </tr>
        <tr>
          <td>{{ $t('client.memberCode') }}</td>
          <td class="identifier-wrap">{{ client.member_code }}</td>
        </tr>
        <tr v-if="client.subsystem_code">
          <td>{{ $t('client.subsystemCode') }}</td>
          <td class="identifier-wrap">{{ client.subsystem_code }}</td>
        </tr>
      </table>

      <XrdEmptyPlaceholder
        :loading="clientLoading"
        :data="client"
        :no-items-text="$t('noData.noClientData')"
      />
    </v-card>

    <v-card flat>
      <table class="xrd-table details-certificates">
        <tr>
          <th>{{ $t('cert.signCertificate') }}</th>
          <th>{{ $t('cert.serialNumber') }}</th>
          <th>{{ $t('cert.state') }}</th>
          <th>{{ $t('cert.expires') }}</th>
        </tr>
        <template
          v-if="
            signCertificates &&
            signCertificates.length > 0 &&
            !certificatesLoading
          "
        >
          <tr
            v-for="certificate in signCertificates"
            :key="certificate.certificate_details.hash"
          >
            <td>
              <span class="cert-name" @click="viewCertificate(certificate)">{{
                certificate.certificate_details.issuer_common_name
              }}</span>
            </td>
            <td>{{ certificate.certificate_details.serial }}</td>
            <td v-if="certificate.active">{{ $t('cert.inUse') }}</td>
            <td v-else>{{ $t('cert.disabled') }}</td>
            <td>
              {{ certificate.certificate_details.not_after | formatDate }}
            </td>
          </tr>
        </template>
        <XrdEmptyPlaceholderRow
          :colspan="5"
          :loading="certificatesLoading"
          :data="signCertificates"
          :no-items-text="$t('noData.noCertificates')"
        />
      </table>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { RouteName } from '@/global';
import { KeyUsageType, TokenCertificate } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useClientStore } from '@/store/modules/client';

export default Vue.extend({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      certificatesLoading: false,
    };
  },
  computed: {
    ...mapState(useClientStore, [
      'client',
      'signCertificates',
      'clientLoading',
    ]),
  },
  created() {
    this.certificatesLoading = true;
    this.fetchSignCertificates(this.id)
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => (this.certificatesLoading = false));
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useClientStore, ['fetchSignCertificates']),
    viewCertificate(cert: TokenCertificate) {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          hash: cert.certificate_details.hash,
          usage: KeyUsageType.SIGNING,
        },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.detail-table {
  margin-top: 40px;

  tr td:first-child {
    width: 20%;
  }
}

.cert-name {
  color: $XRoad-Link;
  cursor: pointer;
}

.details-certificates {
  margin-top: 40px;
}
</style>
