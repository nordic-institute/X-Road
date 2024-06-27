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
    v-if="dialog"
    title="keys.orderAcmeCertificate"
    save-button-text="action.order"
    :disable-save="!meta.valid || hasAcmeEabRequiredButNoCredentials"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="dlg-edit-row">
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('csr.certificationService')"
            :help-text="$t('csr.helpCertificationService')"
          />
          <v-select
            v-model="certificateService"
            :items="acmeCertificateServices"
            item-title="name"
            class="wizard-form-input"
            data-test="csr-certification-service-select"
            variant="outlined"
            :error-messages="errors"
          ></v-select>
        </div>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from "vue";
import { useField } from "vee-validate";
import { mapActions, mapState } from "pinia";
import { useCsr } from "@/store/modules/certificateSignRequest";
import { KeyUsageType, TokenCertificateSigningRequest } from "@/openapi-types";
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    csr: {
      type: Object as PropType<TokenCertificateSigningRequest>,
      required: true,
    },
    keyUsage: {
      type: Object as PropType<KeyUsageType>,
    },
  },
  emits: ['cancel', 'save'],
  setup() {
    const { meta, errors, setErrors, value, resetField } = useField(
      'certificateService',
      'required',
      { initialValue: '' },
    );
    return { meta, errors, setErrors, certificateService: value, resetField };
  },
  data() {
    return {
      hasAcmeEabRequiredButNoCredentials: false,
    }
  },
  computed: {
    ...mapState(useCsr, ['certificationServiceList']),
    acmeCertificateServices() {
      return this.certificationServiceList.filter(
        (certificationService) =>
          certificationService.certificate_profile_info == this.csr.certificate_profile
          && certificationService.acme_capable
          && (this.keyUsage == KeyUsageType.AUTHENTICATION || !certificationService.authentication_only),
      );
    }
  },
  watch: {
    certificateService(newValue: string) {
      const newCA = this.acmeCertificateServices.find(ca => ca.name == newValue);
      if (newCA?.acme_capable) {
        this.hasAcmeEabCredentials(newCA.name, this.csr.owner_id, this.keyUsage)
          .then((res) => {
            this.hasAcmeEabRequiredButNoCredentials = res.acme_eab_required && !res.has_acme_external_account_credentials;
            if (this.hasAcmeEabRequiredButNoCredentials) {
              this.setErrors(this.$t('csr.eabCredRequired'));
            }
          })
          .catch((error) => this.showError(error, true));
      } else {
        this.hasAcmeEabRequiredButNoCredentials = false;
      }
    },
  },
  methods: {
    ...mapActions(useCsr, ['hasAcmeEabCredentials']),
    ...mapActions(useNotifications, ['showError']),
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit('save', this.certificateService);
      this.clear();
    },
    clear(): void {
      this.resetField();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
