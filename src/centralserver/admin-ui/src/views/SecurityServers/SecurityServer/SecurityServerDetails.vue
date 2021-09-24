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
<!--
  Member details view
-->
<template>
  <main id="member-details-content">
    <!-- Security Server Details -->
    <div id="security-server-details">
      <info-card
        :title-text="$t('securityServers.ownerName')"
        :info-text="securityServer.ownerName"
        data-test="security-server-owner-name"
      />

      <info-card
        :title-text="$t('securityServers.ownerClass')"
        :info-text="securityServer.ownerClass"
        data-test="security-server-owner-class"
      />

      <info-card
        :title-text="$t('securityServers.ownerCode')"
        :info-text="securityServer.ownerCode"
        data-test="security-server-owner-code"
      />
    </div>

    <info-card
      class="mb-6"
      :title-text="$t('securityServers.serverCode')"
      :info-text="securityServer.serverCode"
      data-test="security-server-owner-code"
    />

    <info-card
      class="mb-6"
      :title-text="$t('securityServers.address')"
      :info-text="securityServer.address"
      data-test="security-server-owner-code"
    >
      <xrd-button text :outlined="false">{{ $t('action.edit') }}</xrd-button>
    </info-card>

    <info-card
      :title-text="$t('securityServers.registered')"
      :info-text="securityServer.registered"
      data-test="security-server-owner-code"
    />
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';

/**
 * Component for a Security server details view
 */
export default Vue.extend({
  name: 'MemberDetails',
  components: {
    InfoCard,
  },
  data() {
    return {
      searchServers: '',
      searchGroups: '',
      loading: false,
      loadingGroups: false,
      showOnlyPending: false,
      securityServer: {
        ownerName: 'NIIS',
        ownerClass: 'ORG',
        ownerCode: '555',
        serverCode: 'NIIS-SS1',
        address: 'xroad-lxd-ss1.net',
        registered: '2020-11-10 16:55:01',
      },
    };
  },
  computed: {},
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

#security-server-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}

#global-groups-table {
  tbody tr td:last-child {
    width: 50px;
  }
}
</style>
