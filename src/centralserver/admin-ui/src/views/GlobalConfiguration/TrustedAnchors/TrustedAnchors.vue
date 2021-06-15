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
  <div class="about">
    <!-- Internal configuration -->
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">Trusted anchors</div>
      </div>
    </div>

    <!-- Anchor -->
    <div id="anchor" class="mt-4">
      <v-card class="pb-4">
        <div class="card-top">
          <div class="card-main-title">Configuration parts</div>
          <div class="card-corner-button pr-4">
            <xrd-button outlined>
              <v-icon class="xrd-large-button-icon">icon-Download</v-icon
              >Download
            </xrd-button>
          </div>
        </div>

        <table class="xrd-table mt-0 pb-3">
          <thead>
            <tr>
              <th class="title-col">Certificate HASH (SHA-224)</th>

              <th class="expiration-col">
                {{ $t('keys.created') }}
              </th>
              <th></th>
            </tr>
          </thead>

          <tbody>
            <!-- SOFTWARE token table body -->
            <tr>
              <td class="td-icon">
                <div class="icon-column-wrap">
                  <i class="icon-Certificate cert-icon" />

                  <div>
                    42:C2:6E:67:BC:07:FE:B8:0E:41:16:2A:97:EF:9F:42:C2:6E:67:BC:07:FE:B8:0E:41:16:2A:97:EF:9F:
                  </div>
                </div>
              </td>
              <td>2018-06-05 16:33:12</td>
              <td class="xrd-clickable">{{ $t('action.delete') }}</td>
            </tr>
          </tbody>
        </table>
      </v-card>
    </div>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';

export default Vue.extend({
  components: {},
  data() {
    return {};
  },
  computed: {},

  methods: {},
});
</script>

<style scoped lang="scss">
@import '~styles/colors';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.cert-icon {
  margin-right: 10px;
  color: $XRoad-Purple100;
}

.icon-column-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
