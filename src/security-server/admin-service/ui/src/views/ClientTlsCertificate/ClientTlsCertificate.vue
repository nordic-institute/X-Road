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
  <CertificateView v-if="certificate" data-test="certificate-details-dialog" :certificate-details="certificate">
    <template #tools>
      <xrd-button
        v-if="showDeleteButton"
        data-test="tls-certificate-delete-button"
        outlined
        @click="deleteCertificate()"
      >
        {{ $t('action.delete') }}
      </xrd-button>
    </template>
  </CertificateView>

  <!-- Confirm dialog for delete -->
  <xrd-confirm-dialog
    v-if="confirm"
    title="cert.deleteCertTitle"
    text="cert.deleteCertConfirm"
    @cancel="confirm = false"
    @accept="doDeleteCertificate()"
  />
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { Permissions } from '@/global';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { CertificateDetails } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useClient } from '@/store/modules/client';
import CertificateView from '@/components/certificate/CertificateView.vue';

export default defineComponent({
  components: {
    CertificateView,
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
    ...mapState(useClient, ['tlsCertificates']),
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
      this.$router.back();
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
</style>
