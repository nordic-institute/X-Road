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
  <v-container fluid>
    <p class="body-regular font-weight-bold">{{ $t('cert.hashInfo') }}</p>
    <p class="body-regular">
      <XrdHashValue :value="certificate.hash" />
    </p>
    <v-divider class="mt-6 mb-6" />

    <XrdCertificateBlock>
      <XrdCertificateLine label="cert.version" :value="certificate.version" />
      <XrdCertificateLine label="cert.serial" :value="certificate.serial" />
      <XrdCertificateLine label="cert.signatureAlgorithm" :value="certificate.signature_algorithm" />
      <XrdCertificateLine label="cert.issuerDistinguishedName" :value="certificate.issuer_distinguished_name" />
      <XrdCertificateLine label="cert.notBefore" :value="certificate.not_before" date />
      <XrdCertificateLine label="cert.notAfter" :value="certificate.not_after" date />
      <XrdCertificateLine label="cert.subjectDistinguishedName" :value="certificate.subject_distinguished_name" />
      <XrdCertificateLine label="cert.publicKeyAlgorithm" :value="certificate.public_key_algorithm" />
    </XrdCertificateBlock>
    <XrdCertificateBlock v-if="certificate.rsa_public_key_modulus || certificate.rsa_public_key_exponent" class="mt-6">
      <XrdCertificateLine
        v-if="certificate.rsa_public_key_modulus"
        label="cert.rsaModulus"
        :value="certificate.rsa_public_key_modulus"
        colonize
        can-copy
      />
      <XrdCertificateLine v-if="certificate.rsa_public_key_exponent" label="cert.rsaExp" :value="certificate.rsa_public_key_exponent" />
    </XrdCertificateBlock>
    <XrdCertificateBlock v-if="certificate.ec_public_key_point || certificate.ec_public_parameters" class="mt-6">
      <XrdCertificateLine v-if="certificate.ec_public_key_point" label="cert.ecPoint" :value="certificate.ec_public_key_point" />
      <XrdCertificateLine v-if="certificate.ec_public_parameters" label="cert.ecParameters" :value="certificate.ec_public_parameters" />
    </XrdCertificateBlock>
    <XrdCertificateBlock class="mt-6">
      <XrdCertificateLine label="cert.keyUsages" :values="translatedKeyUsages" />
      <XrdCertificateLine label="cert.subjectAlternativeNames" :value="certificate.subject_alternative_names" />
    </XrdCertificateBlock>
    <XrdCertificateBlock class="mt-6">
      <XrdCertificateLine label="cert.signature" :value="certificate.signature" colonize can-copy />
    </XrdCertificateBlock>
  </v-container>
</template>

<script lang="ts" setup>
import { PropType, computed } from 'vue';
import { XrdHashValue } from '../../components';
import XrdCertificateLine from './XrdCertificateLine.vue';
import XrdCertificateBlock from './XrdCertificateBlock.vue';
import { useI18n } from 'vue-i18n';

type Certificate = {
  hash: string;
  signature: string;
  version: number;
  serial: string;
  public_key_algorithm: string;
  signature_algorithm: string;
  rsa_public_key_modulus: string;
  rsa_public_key_exponent: number;
  ec_public_key_point?: string;
  ec_public_parameters?: string;
  issuer_distinguished_name: string;
  subject_distinguished_name: string;
  subject_alternative_names: string;
  not_after: string;
  not_before: string;
  key_usages: string[];
};

const props = defineProps({
  certificate: {
    type: Object as PropType<Certificate>,
    required: true,
  },
});

const { t } = useI18n();

const translatedKeyUsages = computed(() => props.certificate?.key_usages.map((usage) => t('cert.keyUsage.' + usage)));
</script>

<style lang="scss" scoped></style>
