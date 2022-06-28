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
    <certificate-line child-key="version" :source-object="certificate" />
    <certificate-line child-key="serial" :source-object="certificate" />
    <certificate-line
      child-key="signature_algorithm"
      :source-object="certificate"
    />
    <certificate-line
      child-key="issuer_distinguished_name"
      :source-object="certificate"
    />
    <certificate-line
      child-key="not_before"
      :source-object="certificate"
      date
    />
    <certificate-line child-key="not_after" :source-object="certificate" date />
    <certificate-line
      child-key="subject_distinguished_name"
      :source-object="certificate"
    />

    <certificate-line
      child-key="public_key_algorithm"
      :source-object="certificate"
    />
    <certificate-line
      child-key="rsa_public_key_modulus"
      :label="$t('cert.rsaModulus')"
      :source-object="certificate"
      chunk
    />

    <certificate-line
      child-key="rsa_public_key_exponent"
      :label="$t('cert.rsaExp')"
      :source-object="certificate"
    />

    <certificate-line child-key="state" :source-object="certificate" />
    <certificate-line
      child-key="key_usages"
      array-type
      :source-object="certificate"
    />
    <certificate-line
      child-key="subject_alternative_names"
      :source-object="certificate"
    />
    <certificate-line
      child-key="signature"
      :source-object="certificate"
      chunk
    />
  </div>
</template>

<script lang="ts">
/**
 * Component for displaying detailed certificate information
 */

import Vue from 'vue';
import CertificateLine from './CertificateLine.vue';

export default Vue.extend({
  components: {
    CertificateLine,
  },
  props: {
    certificate: {
      type: Object,
      required: true,
    },
  },
});
</script>
