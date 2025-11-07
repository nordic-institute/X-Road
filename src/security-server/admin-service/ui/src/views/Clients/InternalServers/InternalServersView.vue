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
    <XrdCard
      v-if="showConnectionType"
      class="pa-6 mb-4"
      bg-color="surface-container"
      :loading="connectionTypeUpdating"
    >
      <v-select
        :key="revertHack"
        v-model="connectionTypeModel"
        :items="connectionTypes"
        class="xrd connection-type"
        :disabled="!canEditConnectionType"
        :readonly="!canEditConnectionType"
        :label="$t('internalServers.connectionType')"
      />
      <p class="body-regular font-weight-regular">
        {{ $t('internalServers.connectionInfo') }}
      </p>
    </XrdCard>

    <XrdCard title="internalServers.tlsTitle" class="mb-4">
      <template #title-actions>
        <XrdFileUpload
          v-if="canAddTlsCert"
          v-slot="{ upload }"
          accepts=".pem, .cer, .der"
          @file-changed="onFileChange"
        >
          <XrdBtn
            variant="text"
            text="action.upload"
            prepend-icon="upload"
            :loading="uploading"
            @click="upload"
          />
        </XrdFileUpload>
      </template>

      <v-data-table
        data-test="tls-certificate-table"
        class="xrd"
        item-key="id"
        no-data-text="noData.noCertificates"
        hide-default-footer
        :loading="tlsCertLoading"
        :headers="headers"
        :items="tlsCertificates"
        :must-sort="true"
        :items-per-page="-1"
        :loader-height="2"
      >
        <template #[`item.hash`]="{ item }">
          <XrdLabelWithIcon
            data-test="tls-certificate-link"
            icon="editor_choice"
            :clickable="canViewTlsCertDetails"
            @navigate="openCertificate(item)"
          >
            <template #label>
              <XrdHashValue :value="item.hash" wrap-friendly />
            </template>
          </XrdLabelWithIcon>
        </template>

        <template #[`item.not_before`]="{ item }">
          <XrdDate
            data-test="tls-certificate-not-before"
            :value="item.not_before"
          />
        </template>

        <template #[`item.not_after`]="{ item }">
          <XrdDate
            data-test="tls-certificate-not-after"
            :value="item.not_after"
          />
        </template>
      </v-data-table>
    </XrdCard>

    <XrdCard v-if="canViewSSCert" title="internalServers.ssCertTitle">
      <v-table class="xrd">
        <thead>
          <tr>
            <th>{{ $t('internalServers.certHash') }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="ssCertificate && !ssCertLoading">
            <td>
              <XrdLabelWithIcon
                label-color="on-surface"
                icon-color="on-surface"
                icon="editor_choice"
              >
                <template #label>
                  <XrdHashValue :value="ssCertificate.hash" wrap-friendly />
                </template>
              </XrdLabelWithIcon>
            </td>
            <td>
              <XrdBtn
                v-if="canExportSSCert"
                data-test="export-button"
                class="float-right"
                variant="text"
                text="action.export"
                color="tertiary"
                :loading="exporting"
                @click="doExportSSCertificate"
              />
            </td>
          </tr>
          <XrdEmptyPlaceholderRow
            :colspan="3"
            :loading="ssCertLoading"
            :data="ssCertificate"
            :no-items-text="$t('noData.noCertificate')"
          />
        </tbody>
      </v-table>
    </XrdCard>
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { Permissions, RouteName } from '@/global';
import { CertificateDetails, ConnectionType } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import {
  FileUploadResult,
  XrdDate,
  XrdHashValue,
  XrdSubView,
  XrdCard,
  XrdBtn,
  XrdLabelWithIcon,
  XrdEmptyPlaceholderRow,
  useNotifications,
  XrdFileUpload,
} from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useTlsCertificate } from '@/store/modules/tls-certificate';

export default defineComponent({
  components: {
    XrdCard,
    XrdSubView,
    XrdHashValue,
    XrdDate,
    XrdBtn,
    XrdLabelWithIcon,
    XrdEmptyPlaceholderRow,
    XrdFileUpload,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      connectionTypes: [
        { title: 'HTTP', value: ConnectionType.HTTP },
        { title: 'HTTPS', value: ConnectionType.HTTPS },
        { title: 'HTTPS NO AUTH', value: ConnectionType.HTTPS_NO_AUTH },
      ],
      dialog: false,
      selectedCertificate: null,
      revertHack: 0,
      tlsCertLoading: false,
      ssCertLoading: false,
      connectionTypeUpdating: false,
      uploading: false,
      exporting: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClient, [
      'tlsCertificates',
      'ssCertificate',
      'connectionType',
    ]),
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('certificate.hash') as string,
          align: 'start',
          key: 'hash',
        },
        {
          title: this.$t('certificate.subjectDistinguishedName') as string,
          align: 'start',
          key: 'subject_distinguished_name',
          cellProps: {
            'data-test': 'tls-certificate-subject-distinguished-name',
          },
        },
        {
          title: this.$t('certificate.notBefore') as string,
          align: 'start',
          key: 'not_before',
        },
        {
          title: this.$t('certificate.notAfter') as string,
          align: 'start',
          key: 'not_after',
        },
      ];
    },

    connectionTypeModel: {
      get(): string | undefined | null {
        return this.connectionType;
      },
      set(value: string) {
        this.connectionTypeUpdating = true;
        this.saveConnectionType(this.id, value)
          .then(() => {
            this.addSuccessMessage(this.$t('internalServers.connTypeUpdated'));
          })
          .catch((error) => {
            this.revertHack += 1;
            this.addError(error);
          })
          .finally(() => (this.connectionTypeUpdating = false));
      },
    },

    showConnectionType(): boolean {
      return this.hasPermission(
        Permissions.VIEW_CLIENT_INTERNAL_CONNECTION_TYPE,
      );
    },
    canEditConnectionType(): boolean {
      return this.hasPermission(
        Permissions.EDIT_CLIENT_INTERNAL_CONNECTION_TYPE,
      );
    },
    canViewTlsCertDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_CLIENT_INTERNAL_CERT_DETAILS);
    },
    canAddTlsCert(): boolean {
      return this.hasPermission(Permissions.ADD_CLIENT_INTERNAL_CERT);
    },
    canViewSSCert(): boolean {
      return this.hasPermission(Permissions.VIEW_INTERNAL_TLS_CERT);
    },
    canExportSSCert(): boolean {
      return this.hasPermission(Permissions.EXPORT_INTERNAL_TLS_CERT);
    },
  },
  created() {
    this.fetchSSCert(this.id);
    this.fetchTlsCerts(this.id);
  },
  methods: {
    ...mapActions(useClient, [
      'saveConnectionType',
      'fetchTlsCertificates',
      'fetchSSCertificate',
      'uploadTlsCertificate',
    ]),
    ...mapActions(useTlsCertificate, ['downloadCertificate']),
    async onFileChange(event: FileUploadResult) {
      this.uploading = true;
      return this.uploadTlsCertificate(this.id, event.buffer)
        .then(() => {
          this.uploading = false;
          this.fetchTlsCerts(this.id);
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.uploading = false));
    },

    async fetchTlsCerts(id: string) {
      this.tlsCertLoading = true;
      return this.fetchTlsCertificates(id)
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => (this.tlsCertLoading = false));
    },

    async doExportSSCertificate() {
      this.exporting = true;
      return this.downloadCertificate()
        .catch((error) => this.addError(error))
        .finally(() => (this.exporting = false));
    },

    async fetchSSCert(id: string) {
      this.ssCertLoading = true;
      return this.fetchSSCertificate(id)
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => (this.ssCertLoading = false));
    },

    openCertificate(cert: CertificateDetails): void {
      this.$router.push({
        name: RouteName.ClientTlsCertificate,
        params: {
          id: this.id,
          hash: cert.hash,
        },
      });
    },
    closeDialog(): void {
      this.dialog = false;
    },
  },
});
</script>

<style lang="scss" scoped>
.connection-type {
  width: 356px;
}
</style>
