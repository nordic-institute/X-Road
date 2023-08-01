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
    <v-tooltip location="top">
      <template v-slot:activator="{ props }">
        <div >
          <xrd-icon-base class="mr-3" v-bind="props">
            <!-- Decide what icon to show -->
            <xrd-icon-change-owner v-if="type === 'OWNER_CHANGE_REQUEST'" />
            <xrd-icon-add-user v-if="type === 'CLIENT_REGISTRATION_REQUEST'" />
            <xrd-icon-remove-user v-if="type === 'CLIENT_DELETION_REQUEST'" />
            <xrd-icon-remove-certificate
              v-if="type === 'AUTH_CERT_DELETION_REQUEST'"
            />
            <xrd-icon-add-certificate
              v-if="type === 'AUTH_CERT_REGISTRATION_REQUEST'"
            />
          </xrd-icon-base>
        </div>
      </template>
      <span>{{ typeText }}</span>
    </v-tooltip>
    <span class="status-text">{{ typeText }}</span>
  </div>
</template>

<script lang="ts">
import XrdIconChangeOwner from '@shared-ui/components/icons/XrdIconChangeOwner.vue'
import XrdIconAddUser from '@shared-ui/components/icons/XrdIconAddUser.vue'
import XrdIconRemoveUser from '@shared-ui/components/icons/XrdIconRemoveUser.vue'
import XrdIconRemoveCertificate from '@shared-ui/components/icons/XrdIconRemoveCertificate.vue'
import XrdIconAddCertificate from '@shared-ui/components/icons/XrdIconAddCertificate.vue'
import { defineComponent, PropType } from 'vue';
import { ManagementRequestType } from '@/openapi-types';
import { managementTypeToText } from '@/util/helpers';

export default defineComponent({
  components: {
    XrdIconAddCertificate,
    XrdIconRemoveCertificate,
    XrdIconRemoveUser,
    XrdIconAddUser,
    XrdIconChangeOwner
  },
  props: {
    type: {
      type: String as PropType<ManagementRequestType>,
      default: undefined,
    },
  },

  computed: {
    typeText() {
      return managementTypeToText(this.type);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

@media (max-width: 1200px) {
  .status-text {
    display: none;
  }
}
</style>
