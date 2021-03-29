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
  <main-content-area>
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">{{ $t('members.header') }}</div>
        <xrd-search />
      </div>
      <xrd-button @click="() => {}" data-test="add-member-button"
        ><v-icon class="xrd-large-button-icon">icon-Add</v-icon>
        {{ $t('members.addMember') }}</xrd-button
      >
    </div>

    <v-card>
      <v-card-text>
        <xrd-table>
          <thead>
            <tr>
              <th class="icon-column"></th>
              <th>{{ $t('members.memberName') }} (2)</th>
              <th>
                {{ $t('members.memberClass') }}
              </th>
              <th>
                {{ $t('members.memberCode') }}
                <v-icon>mdi-arrow-up</v-icon>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr data-test="niis">
              <!-- Set member identifier etc. as data-text attribute for real data so that individual member row can be found in e2e-test -->
              <td data-test="member-icon">
                <v-icon class="link">icon-Folder-outline</v-icon>
              </td>
              <td data-test="member-name" class="link">
                Nordic Institue for Interoperability Solutions
              </td>
              <td data-test="member-class">ORG</td>
              <td data-test="member-code">555</td>
            </tr>
            <tr data-test="netum">
              <td data-test="member-icon">
                <v-icon class="link">icon-Folder-outline</v-icon>
              </td>
              <td class="link">Netum Oy</td>
              <td>COM</td>
              <td>IMAMEMBERCODE</td>
            </tr>
          </tbody>
        </xrd-table>
      </v-card-text>
    </v-card>
  </main-content-area>
</template>

<script lang="ts">
import Vue from 'vue';
import MainContentArea from '@/components/layout/MainContentArea.vue';

export default Vue.extend({
  components: { MainContentArea },
  data: () => ({}),
  methods: {},
});
</script>

<style scoped lang="scss">
@import '../../assets/colors';

.header-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-bottom: 24px;
}

.title-search {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  align-items: flex-end;

  .xrd-view-title {
    margin-right: 20px;
  }
}

.icon-column {
  width: 40px;
}

.link {
  color: $XRoad-Link;
  cursor: pointer;
}
</style>
