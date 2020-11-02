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
    <LargeButton
      data-test="make-owner-button"
      outlined
      @click="confirmMakeOwner = true"
      >{{ $t('client.action.makeOwner.button') }}</LargeButton
    >

    <!-- Confirm dialog for make owner -->
    <simpleDialog
      :dialog="confirmMakeOwner"
      :loading="makeOwnerLoading"
      saveButtonText="client.action.makeOwner.button"
      title="client.action.makeOwner.confirmTitle"
      @cancel="confirmMakeOwner = false"
      @save="makeOwner()"
    >
      <div slot="content">
        {{ $t('client.action.makeOwner.confirmText1') }}
        <br />
        <br />
        <b>{{ id }}</b>
        <br />
        <br />
        {{ $t('client.action.makeOwner.confirmText2') }}
      </div>
    </simpleDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    SimpleDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmMakeOwner: false as boolean,
      makeOwnerLoading: false as boolean,
    };
  },

  methods: {
    makeOwner(): void {
      this.makeOwnerLoading = true;

      api
        .put(`/clients/${encodePathParameter(this.id)}/make-owner`, {})
        .then(
          () => {
            this.$store.dispatch(
              'showSuccess',
              'client.action.makeOwner.success',
            );
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmMakeOwner = false;
          this.makeOwnerLoading = false;
        });
    },
  },
});
</script>
