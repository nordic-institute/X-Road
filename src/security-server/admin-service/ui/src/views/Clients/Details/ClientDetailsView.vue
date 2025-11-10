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
  <XrdSubView>
    <XrdCard class="mb-4" :loading="clientLoading">
      <XrdCardTable v-if="client">
        <XrdCardTableRow
          label="client.memberName"
          :value="client.member_name"
        />
        <XrdCardTableRow
          label="client.memberClass"
          :value="client.member_class"
        />
        <XrdCardTableRow
          label="client.memberCode"
          :value="client.member_code"
        />
        <XrdCardTableRow
          v-if="client.subsystem_code"
          label="client.subsystemCode"
          :value="client.subsystem_code"
        />
        <XrdCardTableRow
          v-if="client.subsystem_code && doesSupportSubsystemNames"
          label="client.subsystemName"
        >
          <template #value>
            <subsystem-name :name="client.subsystem_name" />
          </template>
          <client-status
            v-if="client.rename_status"
            data-test="rename-status"
            class="float-right"
            style="float: right"
            :status="client.rename_status"
          />
        </XrdCardTableRow>
      </XrdCardTable>
    </XrdCard>

    <v-table class="xrd border">
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
              <XrdLabelWithIcon
                data-test="cert-name"
                :label="certificate.certificate_details.issuer_common_name"
                icon="editor_choice"
                bold
                clickable
                @navigate="viewCertificate(certificate)"
              />
            </td>
            <td>{{ certificate.certificate_details.serial }}</td>
            <td>
              <CertificateStatusChip :active="certificate.active" />
            </td>
            <td>
              <XrdDate :value="certificate.certificate_details.not_after" />
            </td>
          </tr>
        </template>
        <XrdEmptyPlaceholderRow
          :colspan="5"
          :loading="certificatesLoading"
          :data="signCertificates"
          :no-items-text="$t('noData.noCertificates')"
        />
      </tbody>
    </v-table>
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { RouteName } from '@/global';
import { KeyUsageType, TokenCertificate } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useClient } from '@/store/modules/client';
import { useUser } from '@/store/modules/user';
import ClientStatus from '@/views/Clients/ClientStatus.vue';
import { useSystem } from '@/store/modules/system';
import SubsystemName from '@/components/client/SubsystemName.vue';
import {
  XrdDate,
  XrdSubView,
  XrdCard,
  XrdCardTable,
  XrdCardTableRow,
  XrdLabelWithIcon,
  XrdEmptyPlaceholderRow,
  useNotifications,
} from '@niis/shared-ui';
import CertificateStatusChip from '@/components/certificate/CertificateStatusChip.vue';

export default defineComponent({
  components: {
    CertificateStatusChip,
    SubsystemName,
    ClientStatus,
    XrdDate,
    XrdSubView,
    XrdCard,
    XrdCardTableRow,
    XrdCardTable,
    XrdLabelWithIcon,
    XrdEmptyPlaceholderRow,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
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
    ...mapState(useSystem, ['doesSupportSubsystemNames']),
  },
  created() {
    this.certificatesLoading = true;
    this.fetchSignCertificates(this.id)
      .catch((error) => {
        this.addError(error);
      })
      .finally(() => (this.certificatesLoading = false));
  },
  methods: {
    ...mapActions(useClient, ['fetchSignCertificates', 'fetchClient']),
    viewCertificate(cert: TokenCertificate) {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          hash: cert.certificate_details.hash,
          usage: KeyUsageType.SIGNING,
        },
        state: {
          clientId: this.client?.id,
          subsystem: !!this.client?.subsystem_code,
        },
      });
    },
  },
});
</script>

<style lang="scss" scoped></style>
