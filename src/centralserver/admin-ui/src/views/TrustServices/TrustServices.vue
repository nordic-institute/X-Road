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
  <xrd-sub-view-container>
    <!-- Title and button -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('trustServices.certificationServices') }}
      </div>

      <xrd-button v-if="showAddCSButton" data-test="add-certification-service" @click="showAddCSDialog = true">
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('trustServices.addCertificationService') }}</xrd-button
      >
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="certificationServices"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.ca_certificate.subject_common_name`]="{ item }">
        <div class="xrd-clickable">{{ item.ca_certificate.subject_common_name }}</div>
      </template>
      <template #[`item.ca_certificate.not_before`]="{ item }">
        <div>{{ item.ca_certificate.not_before | formatDateTime }}</div>
      </template>
      <template #[`item.ca_certificate.not_after`]="{ item }">
        <div>{{ item.ca_certificate.not_after | formatDateTime }}</div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Title and button -->
    <div class="table-toolbar align-fix mt-8 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('trustServices.timestampingServices') }}
      </div>

      <xrd-button data-test="add-timestamping-service" @click="() => {}">
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('trustServices.addTimestampingService') }}</xrd-button
      >
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="certificationServices"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.server`]="{ item }">
        <div class="server-code">
          <xrd-icon-base class="mr-4"><XrdIconCertificate /></xrd-icon-base
          >{{ item.server }}
        </div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Dialogs -->
    <xrd-simple-dialog
      v-if="showAddCSDialog"
      :dialog="showAddCSDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.upload"
      title="trustServices.addCertificationService"
      :disable-save="uploadedNewCS === null"
      @save="onUploadButtonClick"
      @cancel="clearDialog"
    >
      <template #content>
        <div class="dlg-input-width">
          <xrd-file-upload
            v-if="showAddCSDialog"
            v-slot="{ upload }"
            accepts=".crt"
            @file-changed="onFileUploaded"
          >
            <v-text-field
              v-model="uploadedFileTitle"
              outlined
              :label="$t('trustServices.uploadCertificate')"
              append-icon="icon-Upload"
              @click="upload"
            ></v-text-field>
          </xrd-file-upload>
        </div>
      </template>
    </xrd-simple-dialog>

    <xrd-simple-dialog
      v-if="showCASettingsDialog"
      :dialog="showCASettingsDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.save"
      title="trustServices.caSettings"
      @save="onDialogSave"
      @cancel="clearDialog"
    >
      <template #content>
        <div class="dlg-input-width">
          <v-checkbox
            v-model="newCAProfileForTLSOnly"
            :label="$t('trustServices.addCASettingsCheckbox')"
          />
          <v-text-field
            v-model="newCAProfile"
            outlined
            :label="$t('trustServices.cartProfileInput')"
            :hint="$t('trustServices.certProfileInputExplanation')"
            persistent-hint
          ></v-text-field>
        </div>
      </template>
    </xrd-simple-dialog>
  </xrd-sub-view-container>
</template>

<script lang="ts">
/**
 * View for 'trust services' tab
 */
import Vue from 'vue';
import * as api from '@/util/api';
import { DataTableHeader } from 'vuetify';
import { mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { useCertificationServiceStore } from '@/store/modules/trust-services';
import { mapState } from 'pinia';
import { mapActions } from 'pinia';
import { userStore } from '@/store/modules/user';
import { Permissions } from '@/global';
import {FileUploadResult} from "@niis/shared-ui";

export default Vue.extend({
  data() {
    return {
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      showAddCSDialog: false,
      showCASettingsDialog: false,
      uploadedNewCS: null as File | null,
      uploadedFileTitle: '',
      newCAProfile: '',
      newCAProfileForTLSOnly: false,
      permissions: Permissions,
    };
  },
  computed: {
    ...mapStores(useCertificationServiceStore, notificationsStore),
    certificationServices() {
      return this.certificationServiceStore.certificationSevices;
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('trustServices.approvedCertificationService') as string,
          align: 'start',
          value: 'ca_certificate.subject_common_name',
          class: 'xrd-table-header ts-table-header-server-code',
        },
        {
          text: this.$t('trustServices.validFrom') as string,
          align: 'start',
          value: 'ca_certificate.not_before',
          class: 'xrd-table-header ts-table-header-valid-from',
        },
        {
          text: this.$t('trustServices.validTo') as string,
          align: 'start',
          value: 'ca_certificate.not_after',
          class: 'xrd-table-header ts-table-header-valid-to',
        },
      ];
    },
    ...mapState(userStore, ['hasPermission']),
    showAddCSButton(): boolean {
      return this.hasPermission(Permissions.ADD_APPROVED_CA);
    },
  },
  created() {
    this.certificationServiceStore.fetchAll();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    onFileUploaded(result: FileUploadResult): void {
      this.uploadedNewCS = result.file;
      this.uploadedFileTitle = result.file.name;

      const formData = new FormData();
      formData.set('certificate', this.uploadedNewCS, this.uploadedFileTitle);
    },
    onUploadButtonClick(): void {
      this.showAddCSDialog = false;
      this.showCASettingsDialog = true;
    },
    onDialogSave(): void {
      if (this.uploadedNewCS !== null) {
        const formData = new FormData();
        formData.append('certificate_profile_info', this.newCAProfile);
        formData.append('tls_auth', this.newCAProfileForTLSOnly.toString());
        formData.append('certificate', this.uploadedNewCS, this.uploadedNewCS.name);

        api
        .post(
          `/certification-services`, formData, { headers: { 'Content-Type': 'multipart/form-data'} },
        )
        .then(() => {
          this.showSuccess(this.$t('trustServices.certImportedSuccessfully'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => this.clearDialog());
      }
    },

    clearDialog(): void {
      this.showAddCSDialog = false;
      this.showCASettingsDialog = false;
      this.uploadedNewCS = null as File | null;
      this.newCAProfile = '';
      this.newCAProfileForTLSOnly = false;
      this.uploadedFileTitle = '';
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
