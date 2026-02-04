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
  <XrdConfirmDialog
    title="tlsCertificates.generateKey.title"
    save-button-text="action.confirm"
    :loading="loading"
    focus-on-accept
    @accept="generate"
    @cancel="cancel"
  >
    <template #text>
      <p data-test="generate-tls-and-certificate-dialog-explanation-text">
        {{ $t('tlsCertificates.generateKey.explanation') }}
      </p>
      <p data-test="generate-tls-and-certificate-dialog-confirmation-text">
        {{ $t('tlsCertificates.generateKey.confirmation') }}
      </p>
    </template>
  </XrdConfirmDialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';

import { XrdConfirmDialog } from '../../../components';
import { useNotifications } from '../../../composables';

import { TlsCertificatesHandler } from '../../../types';

export default defineComponent({
  components: { XrdConfirmDialog },
  props: {
    handler: {
      type: Object as PropType<TlsCertificatesHandler>,
      required: true,
    },
  },
  emits: ['cancel', 'accept'],
  setup() {
    const { addSuccessMessage, addError } = useNotifications();
    return { addSuccessMessage, addError };
  },
  data() {
    return {
      loading: false,
    };
  },
  methods: {
    generate() {
      this.handler
        .generateKey()
        .then(() => {
          this.addSuccessMessage('tlsCertificates.generateKey.success');
          this.$emit('accept');
        })
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => (this.loading = false));
    },
    cancel(): void {
      this.$emit('cancel');
    },
  },
});
</script>
