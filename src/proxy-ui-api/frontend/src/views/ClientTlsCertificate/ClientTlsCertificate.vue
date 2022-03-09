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
  <div class="wrapper xrd-view-common" data-test="certificate-details-dialog">
    <div class="new-content">
      <xrd-sub-view-title :title="$t('cert.certificate')" @close="close" />
      <template v-if="certificate">
        <div class="dtlv-cert-hash">
          <certificateHash :hash="certificate.hash" />
          <xrd-button
            v-if="showDeleteButton"
            outlined
            @click="deleteCertificate()"
            >{{ $t('action.delete') }}</xrd-button
          >
        </div>
        <certificateInfo :certificate="certificate" />
      </template>
    </div>

    <!-- Confirm dialog for delete -->
    <xrd-confirm-dialog
      :dialog="confirm"
      title="cert.deleteCertTitle"
      text="cert.deleteCertConfirm"
      @cancel="confirm = false"
      @accept="doDeleteCertificate()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { Permissions } from '@/global';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';
import * as api from '@/util/api';
import { CertificateDetails } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useClientStore } from '@/store/modules/client';

export default Vue.extend({
  components: {
    CertificateInfo,
    CertificateHash,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    hash: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirm: false,
      certificate: null as CertificateDetails | null,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClientStore, ['tlsCertificates']),
    showDeleteButton(): boolean {
      return this.hasPermission(Permissions.DELETE_CLIENT_INTERNAL_CERT);
    },
  },
  created() {
    this.fetchData(this.id, this.hash);
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.$router.go(-1);
    },
    fetchData(clientId: string, hash: string): void {
      api
        .get<CertificateDetails>(
          `/clients/${clientId}/tls-certificates/${hash}`,
        )
        .then(
          (response) => {
            this.certificate = response.data;
          },
          (error) => {
            this.showError(error);
          },
        );
    },
    deleteCertificate(): void {
      this.confirm = true;
    },
    doDeleteCertificate(): void {
      this.confirm = false;

      api
        .remove(
          `/clients/${encodePathParameter(
            this.id,
          )}/tls-certificates/${encodePathParameter(this.hash)}`,
        )
        .then(
          () => {
            this.showSuccess(this.$t('cert.certDeleted'));
          },
          (error) => {
            this.showError(error);
          },
        )
        .finally(() => {
          this.close();
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/detail-views';

.wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  max-width: 850px;
  height: 100%;
  width: 100%;
}
</style>
