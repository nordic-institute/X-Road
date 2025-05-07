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
  <v-card variant="flat">
    <table v-if="client && !clientLoading" class="xrd-table detail-table">
      <tbody>
      <tr>
        <td>{{ $t('client.memberName') }}</td>
        <td colspan="2" class="identifier-wrap">{{ client.member_name }}</td>
      </tr>
      <tr>
        <td>{{ $t('client.memberClass') }}</td>
        <td colspan="2" class="identifier-wrap">{{ client.member_class }}</td>
      </tr>
      <tr>
        <td>{{ $t('client.memberCode') }}</td>
        <td colspan="2" class="identifier-wrap">{{ client.member_code }}</td>
      </tr>
      <tr v-if="client.subsystem_code">
        <td>{{ $t('client.subsystemCode') }}</td>
        <td colspan="2" class="identifier-wrap">{{ client.subsystem_code }}</td>
      </tr>
      <tr v-if="client.subsystem_code && doesSupportSubsystemNames">
        <td>{{ $t('client.subsystemName') }}</td>

        <td class="identifier-wrap">
          <subsystem-name :name="client.subsystem_name" />
        </td>
        <td class="pr-5">
          <client-status data-test="rename-status" class="float-right" v-if="client.rename_status" style="float: right" :status="client.rename_status" />
        </td>
      </tr>
      </tbody>
    </table>
  </v-card>

  <v-card variant="flat" class="mt-10">
    <table class="xrd-table">
      <thead>
      <tr>
        <th>{{ $t('cert.signCertificate') }}</th>
        <th>{{ $t('cert.serialNumber') }}</th>
        <th>{{ $t('cert.state') }}</th>
        <th>{{ $t('cert.expires') }}</th>
      </tr>
      </thead>
      <tbody>
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
            <span
              class="cert-name"
              data-test="cert-name"
              @click="viewCertificate(certificate)"
            >{{ certificate.certificate_details.issuer_common_name }}</span
            >
          </td>
          <td>{{ certificate.certificate_details.serial }}</td>
          <td v-if="certificate.active">{{ $t('cert.inUse') }}</td>
          <td v-else>{{ $t('cert.disabled') }}</td>
          <td>
            {{ $filters.formatDate(certificate.certificate_details.not_after) }}
          </td>
        </tr>
      </template>
      </tbody>
      <XrdEmptyPlaceholderRow
        :colspan="5"
        :loading="certificatesLoading"
        :data="signCertificates"
        :no-items-text="$t('noData.noCertificates')"
      />
    </table>
  </v-card>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { RouteName } from '@/global';
import { KeyUsageType, TokenCertificate } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useClient } from '@/store/modules/client';
import { useUser } from '@/store/modules/user';
import { XrdIconEdit } from '@niis/shared-ui';
import ClientStatus from '@/views/Clients/ClientStatus.vue';
import { useSystem } from '@/store/modules/system';
import SubsystemName from '@/components/client/SubsystemName.vue';

export default defineComponent({
  components: { SubsystemName, ClientStatus, XrdIconEdit },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      certificatesLoading: false,
      showRenameDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClient, ['client', 'signCertificates', 'clientLoading']),
    ...mapState(useSystem, ['doesSupportSubsystemNames'])
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
    ...mapActions(useClient, ['fetchSignCertificates', 'fetchClient']),
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
@use '@/assets/tables';
@use '@/assets/colors';

.detail-table {
  tr td:first-child {
    width: 20%;
  }
}

.cert-name {
  color: colors.$Link;
  cursor: pointer;
}

.actions {
  width: 15%;
}
</style>
