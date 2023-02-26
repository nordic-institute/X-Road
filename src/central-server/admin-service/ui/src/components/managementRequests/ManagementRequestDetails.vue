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
  <div id="management-request-view">
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title">{{ typeText }}</div>
      <div>
        <xrd-button outlined class="mr-4" data-test="approve-management-request-button">
          Approve//TODO
        </xrd-button>
        <xrd-button outlined class="mr-4" data-test="decline-management-request-button">
          Decline//TODO
        </xrd-button>
      </div>
    </div>

    <management-request-information
      v-if="managementRequest"
      :management-request="managementRequest"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapStores } from 'pinia';
import { managementRequestsStore } from '@/store/modules/managementRequestStore';
import { managementTypeToText } from '@/util/helpers';
import ManagementRequestInformation from './ManagementRequestInformation.vue';

/**
 * Wrapper component for a certification service view
 */
export default Vue.extend({
  name: 'ManagementRequestDetails',
  components: { ManagementRequestInformation },
  props: {
    requestId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {};
  },
  computed: {
    ...mapStores(managementRequestsStore),
    managementRequest() {
      return this.managementRequestsStore.currentManagementRequest;
    },
    typeText(){
      return managementTypeToText(
        this.managementRequestsStore.currentManagementRequest?.type,
      );
    },
  },
  created() {
    this.managementRequestsStore.loadById(this.requestId);
  },
  methods: {},
});
</script>
<style lang="scss" scoped>
@import '~@/assets/tables';


</style>
