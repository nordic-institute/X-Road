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
  <div class="status-wrapper">
    <v-tooltip top>
      <template #activator="{ on, attrs }">
        <div v-bind="attrs" v-on="on">
          <xrd-icon-base v-bind="attrs" v-on="on">
            <!-- Decide what icon to show -->
            <XrdIconChangeOwner v-if="status === 'change_owner'" />
            <XrdIconAddUser v-if="status === 'register_client'" />
            <XrdIconRemoveUser v-if="status === 'delete_client'" />
            <XrdIconRemoveCertificate v-if="status === 'delete_certificate'" />
            <XrdIconAddCertificate v-if="status === 'register_certificate'" />
          </xrd-icon-base>
        </div>
      </template>
      <span>{{ getStatusText() }}</span>
    </v-tooltip>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    status: {
      type: String,
      default: undefined,
    },
  },
  methods: {
    getStatusText() {
      if (!this.status) {
        return '';
      }
      switch (this.status) {
        case 'change_owner':
          return this.$t('managementRequests.changeOwner') as string;
        case 'delete_certificate':
          return this.$t('managementRequests.removeCertificate') as string;
        case 'delete_client':
          return this.$t('managementRequests.removeClient') as string;
        case 'register_certificate':
          return this.$t('managementRequests.addCertificate') as string;
        case 'register_client':
          return this.$t('managementRequests.addClient') as string;
        default:
          return '';
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
