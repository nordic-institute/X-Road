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
  <v-card class="mt-8">
    <div class="card-corner-button pt-4 pr-4">
      <xrd-button outlined data-test="add-member-to-group">
        <v-icon class="xrd-large-button-icon">mdi-plus-circle</v-icon
        >{{ $t('members.member.subsystems.addClient') }}
      </xrd-button>
    </div>

    <v-card-text>
      <xrd-table>
        <thead>
          <tr>
            <th>
              {{ $t('members.member.subsystems.subsystemcode') }}
              <v-icon class="sorting">icon-Sorting-arrow</v-icon>
            </th>
            <th>{{ $t('members.member.subsystems.servercode') }}</th>
            <th>{{ $t('members.member.subsystems.serverOwner') }}</th>
            <th>{{ $t('members.member.subsystems.status') }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Management</td>
            <td class="xrd-clickable">NIIS-SS1</td>
            <td></td>
            <td class="status">
              <v-icon :small="true" :color="colors.Success100"
                >icon-Checked</v-icon
              >
              Registered
            </td>
            <td class="subsystem-actions">
              <span class="xrd-clickable">Delete</span>
            </td>
          </tr>
          <tr class="unregistered-subsystem">
            <td>RR</td>
            <td></td>
            <td></td>
            <td class="status"><v-icon>icon-Error</v-icon> Unregistered</td>
            <td class="subsystem-actions">
              <span class="xrd-clickable">Register</span>
              <span class="xrd-clickable">Delete</span>
            </td>
          </tr>
          <tr>
            <td rowspan="2">DHX</td>
            <td class="xrd-clickable">NIIS-SS1</td>
            <td class="xrd-clickable">NIIS</td>
            <td class="status">
              <v-icon :small="true" :color="colors.Success100"
                >icon-Checked</v-icon
              >
              Registered
            </td>
            <td class="subsystem-actions">
              <span class="xrd-clickable">Delete</span>
            </td>
          </tr>
          <tr>
            <td class="xrd-clickable">NIIS-FUTU</td>
            <td class="xrd-clickable">FUTU</td>
            <td class="status">
              <v-icon :small="true" :color="colors.Success100"
                >icon-In-progress</v-icon
              >
              Pending approval
            </td>
            <td class="subsystem-actions">
              <span class="xrd-clickable">Approve</span>
              <span class="xrd-clickable">Delete</span>
            </td>
          </tr>
        </tbody>
      </xrd-table>
    </v-card-text>
  </v-card>
</template>

<script lang="ts">
import Vue from 'vue';
import { Colors } from '@/global';

/**
 * Component for Member Subsystem
 */
export default Vue.extend({
  name: 'MemberSubsystems',
  data() {
    return {
      colors: Colors,
    };
  },
});
</script>

<style scoped lang="scss">
@import '../../../../assets/colors';

.card-corner-button {
  display: flex;
  justify-content: flex-end;
}

.status {
  text-transform: uppercase;
  font-weight: bold;
}

.subsystem-actions {
  text-align: right;

  .xrd-clickable {
    margin-left: 10px;
  }
}

.sorting {
  margin-bottom: 5px;
}

.unregistered-subsystem {
  background-color: $XRoad-WarmGrey30;
}
</style>
