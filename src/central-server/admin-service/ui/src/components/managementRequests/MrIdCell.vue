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
  <div
    class="request-id"
    :class="{ 'xrd-clickable': canSeeDetails }"
    @click="navigateToDetails"
  >
    {{ managementRequest.id }}
  </div>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { ManagementRequestListView } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';
import { mapState } from 'pinia';
import { userStore } from '@/store/modules/user';

export default Vue.extend({
  props: {
    managementRequest: {
      type: Object as PropType<ManagementRequestListView>,
      required: true,
    },
  },

  computed: {
    ...mapState(userStore, ['hasPermission']),
    canSeeDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS);
    },
  },

  methods: {
    navigateToDetails(): void {
      if (!this.canSeeDetails) {
        return;
      }
      this.$router.push({
        name: RouteName.ManagementRequestDetails,
        params: { requestId: String(this.managementRequest.id) },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.request-id {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}
</style>
