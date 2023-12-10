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
  <main>
    <xrd-simple-dialog
      v-if="showUploadCertificateDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.upload"
      title="trustServices.addCertificationService"
      :disable-save="certFile === null"
      @save="onUpload"
      @cancel="cancel"
    >
      <template #content>
        <div class="dlg-input-width">
          <xrd-file-upload
            v-slot="{ upload }"
            accepts=".der, .crt, .pem, .cer"
            @file-changed="onFileUploaded"
          >
            <v-text-field
              v-model="certFileTitle"
              variant="outlined"
              autofocus
              :label="$t('trustServices.uploadCertificate')"
              append-inner-icon="icon-Upload"
              @click="upload"
            />
          </xrd-file-upload>
        </div>
      </template>
    </xrd-simple-dialog>

    <xrd-simple-dialog
      v-if="showCASettingsDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.save"
      title="trustServices.caSettings"
      :disable-save="!meta.valid"
      :loading="loading"
      @save="onSave"
      @cancel="cancel"
    >
      <template #content>
        <div class="dlg-input-width">
          <v-checkbox
            v-model="tlsAuthOnly"
            :label="$t('trustServices.addCASettingsCheckbox')"
          />
          <v-text-field
            v-bind="certProfileRef"
            variant="outlined"
            :label="$t('trustServices.certProfileInput')"
            :hint="$t('trustServices.certProfileInputExplanation')"
            persistent-hint
            data-test="cert-profile-input"
          />
          <v-checkbox
            v-model="isAcme"
            :label="$t('trustServices.trustService.settings.acmeCapable')"
            hide-details
            class="mt-4"
            data-test="acme-checkbox"
          />
          <v-sheet v-show="isAcme">
            <v-text-field
              v-bind="acmeServerDirectoryUrlRef"
              :label="
                $t('trustServices.trustService.settings.acmeServerDirectoryUrl')
              "
              :hint="$t('trustServices.acmeServerDirectoryUrlExplanation')"
              persistent-hint
              variant="outlined"
              data-test="acme-server-directory-url-input"
              class="py-4"
            />
            <v-text-field
              v-bind="acmeServerIpAddressRef"
              :label="
                $t('trustServices.trustService.settings.acmeServerIpAddress')
              "
              :hint="$t('trustServices.acmeServerIpAddressExplanation')"
              persistent-hint
              variant="outlined"
              data-test="acme-server-ip-address-input"
            />
          </v-sheet>
        </div>
      </template>
    </xrd-simple-dialog>
  </main>
</template>

<script lang="ts">
import { computed, defineComponent, ref } from 'vue';
import { FileUploadResult, XrdFileUpload } from '@niis/shared-ui';
import { Event } from '@/ui-types';
import { PublicPathState, useField, useForm } from 'vee-validate';
import i18n from '@/plugins/i18n';

export default defineComponent({
  components: { XrdFileUpload },
  emits: [Event.Cancel, Event.Add],
  setup() {
    const isAcme = ref(false);
    const validationSchema = computed(() => {
      return isAcme.value
        ? {
            certProfile: 'required',
            acmeServerDirectoryUrl: 'required|url',
            acmeServerIpAddress: 'ipAddresses',
          }
        : {
            certProfile: 'required',
          };
    });
    const { meta, values, defineComponentBinds, resetForm } = useForm({
      validationSchema,
      initialValues: {
        certProfile: '',
        acmeServerDirectoryUrl: '',
        acmeServerIpAddress: '',
      },
    });
    useField('certProfile', undefined, {
      label: i18n.global.t('trustServices.certProfileInput'),
    });
    useField('acmeServerDirectoryUrl', undefined, {
      label: i18n.global.t(
        'trustServices.trustService.settings.acmeServerDirectoryUrl',
      ),
    });
    useField('acmeServerIpAddress', undefined, {
      label: i18n.global.t(
        'trustServices.trustService.settings.acmeServerIpAddress',
      ),
    });
    const componentConfig = {
      mapProps: (state: PublicPathState) => ({
        'error-messages': state.errors,
      }),
      validateOnModelUpdate: true,
    };
    const certProfileRef = defineComponentBinds('certProfile', componentConfig);
    const acmeServerDirectoryUrlRef = defineComponentBinds(
      'acmeServerDirectoryUrl',
      componentConfig,
    );
    const acmeServerIpAddressRef = defineComponentBinds(
      'acmeServerIpAddress',
      componentConfig,
    );
    return {
      meta,
      values,
      isAcme,
      certProfileRef,
      acmeServerDirectoryUrlRef,
      acmeServerIpAddressRef,
      resetForm,
    };
  },
  data() {
    return {
      showCASettingsDialog: false,
      certFile: null as File | null,
      certFileTitle: '',
      tlsAuthOnly: false,
      showUploadCertificateDialog: true,
      loading: false,
    };
  },
  methods: {
    onFileUploaded(result: FileUploadResult): void {
      this.certFile = result.file;
      this.certFileTitle = result.file.name;
    },
    onUpload(): void {
      this.showCASettingsDialog = true;
      this.showUploadCertificateDialog = false;
    },
    onSave(): void {
      if (this.certFile !== null) {
        this.loading = true;
        const certService = {
          certificate: this.certFile,
          tls_auth: this.tlsAuthOnly.toString(),
          certificate_profile_info: this.values.certProfile,
          acme_server_directory_url: this.values.acmeServerDirectoryUrl,
          acme_server_ip_address: this.values.acmeServerIpAddress,
        };
        this.$emit(Event.Add, certService, {
          done: () => {
            this.loading = false;
            this.clearForm();
          },
        });
      }
    },
    cancel(): void {
      this.$emit(Event.Cancel);
      this.clearForm();
    },
    clearForm(): void {
      this.certFile = null as File | null;
      this.resetForm();
      this.tlsAuthOnly = false;
      this.isAcme = false;
      this.certFileTitle = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
</style>
