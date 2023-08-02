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
  <tr v-if="show">
    <td :colspan="colspan">
      <div v-if="loading">
        <v-progress-linear height="2" indeterminate />
        <div class="empty-text">
          {{ $t('noData.loading') }}
        </div>
      </div>
      <div v-else-if="showNoItems" class="empty-text">
        {{ noItemsText }}
      </div>
    </td>
  </tr>
</template>

<script lang="ts">
/** Component to show empty states in html tables. Contains one row.  */

import { defineComponent } from 'vue';

export default defineComponent({
  props: {
    // Text shown when there are no items at all
    noItemsText: {
      type: String,
      required: true,
    },
    // Dialog visible / hidden
    noMatchesText: {
      type: String,
      default: '-',
    },
    data: {
      type: [Array, Object],
      required: false,
      default: undefined,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    colspan: {
      type: [Number, String],
      required: true,
    },
  },
  computed: {
    showNoItems() {
      if (this.data) {
        if (Array.isArray(this.data) && this.data.length === 0) {
          // Empty array
          return true;
        }
        // Object
        return false;
      }
      return true;
    },
    show() {
      if (this.loading) {
        return true;
      }
      return this.showNoItems;
    },
  },
  methods: {}
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.empty-text {
  height: 112px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-left: -16px; /* Removes the default left padding that comes from the thead style */
}

td {
  border-bottom: $XRoad-WarmGrey30 solid 1px;
}
</style>
