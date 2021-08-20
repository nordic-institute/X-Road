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
    <div class="header-row">
      <div class="xrd-title-search">
        <div class="xrd-view-title">{{ $t('members.header') }}</div>
        <xrd-search />
      </div>
      <xrd-button data-test="add-member-button" @click="() => {}">
        <xrd-icon-base class="xrd-large-button-icon"
          ><xrd-icon-add
        /></xrd-icon-base>

        {{ $t('members.addMember') }}</xrd-button
      >
    </div>

    <v-card flat>
      <v-card-text>
        <xrd-table>
          <thead>
            <tr>
              <th class="icon-column"></th>
              <th>{{ $t('global.memberName') }} (2)</th>
              <th>
                {{ $t('global.memberClass') }}
              </th>
              <th>
                {{ $t('global.memberCode') }}
                <v-icon>mdi-arrow-up</v-icon>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr data-test="niis">
              <!-- Set member identifier etc. as data-text attribute for real data so that individual member row can be found in e2e-test -->
              <td data-test="member-icon">
                <xrd-icon-base class="xrd-clickable"
                  ><xrd-icon-folder-outline
                /></xrd-icon-base>
              </td>
              <td data-test="member-name" class="xrd-clickable">
                Nordic Institue for Interoperability Solutions
              </td>
              <td data-test="member-class">ORG</td>
              <td data-test="member-code">555</td>
            </tr>
            <tr data-test="netum">
              <td data-test="member-icon">
                <xrd-icon-base class="xrd-clickable"
                  ><xrd-icon-folder-outline
                /></xrd-icon-base>
              </td>
              <td class="xrd-clickable" @click="toDetails('netum')">
                Netum Oy
              </td>
              <td>COM</td>
              <td>IMAMEMBERCODE</td>
            </tr>
          </tbody>
        </xrd-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';

export default Vue.extend({
  name: 'MemberList',
  methods: {
    toDetails(): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: { memberid: 'netum' },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';

.icon-column {
  width: 40px;
}
</style>
