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
    <ValidationObserver ref="form1" v-slot="{ invalid }">
      <div class="wizard-step-form-content">
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('csr.usage')"
            :help-text="$t('csr.helpUsage')"
          />

          <ValidationProvider v-slot="{}" name="csr.usage" rules="required">
            <v-select
              v-model="usage"
              :items="usageList"
              class="wizard-form-input"
              :disabled="isUsageReadOnly || !permissionForUsage"
              data-test="csr-usage-select"
              outlined
            ></v-select>
          </ValidationProvider>
        </div>

        <div v-if="usage === usageTypes.SIGNING" class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('csr.client')"
            :help-text="$t('csr.helpClient')"
          />

          <ValidationProvider v-slot="{}" name="csr.client" rules="required">
            <v-select
              v-model="csrClient"
              :items="memberIds"
              item-text="id"
              item-value="id"
              class="wizard-form-input"
              data-test="csr-client-select"
              outlined
            ></v-select>
          </ValidationProvider>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('csr.certificationService')"
            :help-text="$t('csr.helpCertificationService')"
          />

          <ValidationProvider
            v-slot="{}"
            name="csr.certService"
            rules="required"
          >
            <v-select
              v-model="certificationService"
              :items="filteredServiceList"
              item-text="name"
              item-value="name"
              class="wizard-form-input"
              data-test="csr-certification-service-select"
              outlined
            ></v-select>
          </ValidationProvider>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('csr.csrFormat')"
            :help-text="$t('csr.helpCsrFormat')"
          />

          <ValidationProvider v-slot="{}" name="csr.csrFormat" rules="required">
            <v-select
              v-model="csrFormat"
              :items="csrFormatList"
              class="wizard-form-input"
              data-test="csr-format-select"
              outlined
            ></v-select>
          </ValidationProvider>
        </div>
      </div>
      <div class="button-footer">
        <xrd-button outlined data-test="cancel-button" @click="cancel">{{
          $t('action.cancel')
        }}</xrd-button>

        <xrd-button
          v-if="showPreviousButton"
          outlined
          class="previous-button"
          data-test="previous-button"
          @click="previous"
          >{{ $t('action.previous') }}</xrd-button
        >
        <xrd-button :disabled="invalid" data-test="save-button" @click="done">{{
          $t(saveButtonText)
        }}</xrd-button>
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { Permissions } from '@/global';
import { CsrFormat, KeyUsageType } from '@/openapi-types';
import { mapActions, mapState, mapWritableState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useCsrStore } from '@/store/modules/certificateSignRequest';

export default Vue.extend({
  components: {
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.continue',
    },
    showPreviousButton: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      usageTypes: KeyUsageType,
      usageList: Object.values(KeyUsageType),
      csrFormatList: Object.values(CsrFormat),
      permissionForUsage: true,
    };
  },
  computed: {
    ...mapState(useCsrStore, [
      'memberIds',
      'filteredServiceList',
      'isUsageReadOnly',
    ]),
    ...mapWritableState(useCsrStore, [
      'usage',
      'csrClient',
      'csrFormat',
      'certificationService',
    ]),
    ...mapState(useUser, ['hasPermission']),
  },

  watch: {
    filteredServiceList(val) {
      // Set first certification service selected as default when the list is updated
      if (val?.length === 1) {
        this.certificationService = val[0].name;
      }
    },
    memberIds(val) {
      // Set first client selected as default when the list is updated
      if (val?.length === 1) {
        this.csrClient = val[0].id;
      }
    },
  },

  created() {
    // Fetch member id:s for the client selection dropdown
    this.fetchAllMemberIds();

    // Check if the user has permission for only one type of CSR
    const signPermission = this.hasPermission(
      Permissions.GENERATE_SIGN_CERT_REQ,
    );
    const authPermission = this.hasPermission(
      Permissions.GENERATE_AUTH_CERT_REQ,
    );

    if (signPermission && !authPermission) {
      // lock usage type to sign
      this.usage = KeyUsageType.SIGNING;
      this.permissionForUsage = false;
    }

    if (!signPermission && authPermission) {
      // lock usage type to auth
      this.usage = KeyUsageType.AUTHENTICATION;
      this.permissionForUsage = false;
    }
  },
  methods: {
    ...mapActions(useCsrStore, ['fetchAllMemberIds']),
    done(): void {
      this.$emit('done');
    },
    previous(): void {
      this.$emit('previous');
    },
    cancel(): void {
      this.$emit('cancel');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
