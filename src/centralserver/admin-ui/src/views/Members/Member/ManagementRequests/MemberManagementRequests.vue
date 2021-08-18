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
  <main id="member-management-requests">
    <div id="management-request-filters">
      <xrd-search class="search" :label="$t('global.search')"></xrd-search>
      <v-checkbox
        class="show-only-waiting"
        :label="$t('members.member.managementRequests.showOnlyWaiting')"
        :background-color="colors.Purple100100"
        :input-value="showOnlyWaitingRequests"
      />
    </div>

    <v-card class="mt-4" flat>
      <v-card-text>
        <xrd-table>
          <thead>
            <tr>
              <th>
                {{ $t('members.member.managementRequests.id') }}
                <v-icon>icon-Sorting-arrow</v-icon>
              </th>
              <th>{{ $t('members.member.managementRequests.created') }}</th>
              <th>{{ $t('members.member.managementRequests.type') }}</th>
              <th>{{ $t('members.member.managementRequests.status') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>2</td>
              <td>2021-09-04 10:15</td>
              <td>Client registration</td>
              <td class="status">
                <v-icon :color="colors.Yellow">icon-Error</v-icon> waiting
              </td>
            </tr>
            <tr>
              <td>3</td>
              <td>2021-09-04 10:15</td>
              <td>Client deletion</td>
              <td class="status">
                <v-icon :color="colors.Success100">icon-Error</v-icon> approved
              </td>
            </tr>
          </tbody>
        </xrd-table>
      </v-card-text>
    </v-card>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { Colors } from '@/global';

/**
 * Component for Member management requests
 */
export default Vue.extend({
  name: 'MemberManagementRequests',
  data() {
    return {
      colors: Colors,
      showOnlyWaitingRequests: false as boolean,
    };
  },
});
</script>

<style scoped lang="scss">
.status {
  text-transform: uppercase;
  font-weight: bold;
}

#management-request-filters {
  display: flex;
  justify-content: space-between;
}
</style>
