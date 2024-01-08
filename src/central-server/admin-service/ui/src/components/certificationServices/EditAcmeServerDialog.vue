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
  <xrd-simple-dialog
    :dialog="true"
    title="trustServices.caSettings"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    :loading="loading"
    :disable-save="!meta.valid"
    @cancel="cancelEdit"
    @save="updateCertificationServiceSettings"
  >
    <template #content>
      <div class="dlg-input-width">
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
            variant="outlined"
            :label="
              $t('trustServices.trustService.settings.acmeServerDirectoryUrl')
            "
            :hint="$t('trustServices.acmeServerDirectoryUrlExplanation')"
            persistent-hint
            class="py-4"
            data-test="acme-server-directory-url-input"
          ></v-text-field>
          <v-text-field
            v-bind="acmeServerIpAddressRef"
            variant="outlined"
            :label="
              $t('trustServices.trustService.settings.acmeServerIpAddress')
            "
            :hint="$t('trustServices.acmeServerIpAddressExplanation')"
            persistent-hint
            data-test="acme-server-ip-address-input"
          ></v-text-field>
        </v-sheet>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { computed, defineComponent, ref } from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useCertificationService } from '@/store/modules/trust-services';
import { ApprovedCertificationService } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { PublicPathState, useField, useForm } from 'vee-validate';
import i18n from '@/plugins/i18n';

export default defineComponent({
  props: {
    certificationService: {
      type: Object as null | (() => ApprovedCertificationService),
      required: true,
    },
  },
  emits: ['cancel', 'tls-auth-changed'],
  setup(props) {
    const isAcme = ref(!!props.certificationService.acme_server_directory_url);
    const validationSchema = computed(() => {
      return isAcme.value
        ? {
            acmeServerDirectoryUrl: 'required|url',
            acmeServerIpAddress: 'ipAddresses',
          }
        : {};
    });
    const { meta, values, defineComponentBinds, resetForm } = useForm({
      validationSchema,
      initialValues: {
        acmeServerDirectoryUrl:
          props.certificationService.acme_server_directory_url,
        acmeServerIpAddress: props.certificationService.acme_server_ip_address,
      },
    });
    useField('acmeServerDirectoryUrl', undefined, {
      label: i18n.global.t(
        'trustServices.trustService.settings.acmeServerDirectoryUrl',
      ),
    });
    useField('acmeServerIpAddress', undefined, {
      label: i18n.global.t('trustServices.trustService.settings.acmeServerIpAddress'),
    });
    const componentConfig = {
      mapProps: (state: PublicPathState) => ({
        'error-messages': state.errors,
      }),
    };
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
      acmeServerDirectoryUrlRef,
      acmeServerIpAddressRef,
      resetForm,
    };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useCertificationService),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelEdit(): void {
      this.$emit('cancel');
    },
    updateCertificationServiceSettings(): void {
      this.loading = true;
      this.certificationServiceStore
        .update(this.certificationService.id, {
          acme_server_directory_url: this.isAcme
            ? this.values.acmeServerDirectoryUrl
            : '',
          acme_server_ip_address: this.isAcme
            ? this.values.acmeServerIpAddress
            : '',
        })
        .then(() => {
          this.showSuccess(
            this.$t('trustServices.trustService.settings.saveSuccess'),
          );
          this.$emit('tls-auth-changed');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => (this.loading = false));
    },
  },
});
</script>
