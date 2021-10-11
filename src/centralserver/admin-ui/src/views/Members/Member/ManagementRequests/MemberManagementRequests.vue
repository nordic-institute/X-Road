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
    <ManagementRequests :management-requests="managementRequests" />
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { Colors } from '@/global';
import ManagementRequests from '@/components/managementRequests/ManagementRequests.vue';

/**
 * Component for Member Management requests
 */

export default Vue.extend({
  name: 'MemberManagementRequests',

  components: {
    ManagementRequests,
  },
  data() {
    return {
      colors: Colors,
      loading: false,
      managementRequests: [
        {
          id: '938726',
          created: '2021-02-01',
          type: 'change_owner',
          serverOwnerName: 'Tartu Kesklinna Perearstikeskus OÜ',
          serverOnwerId: 'DEV-333',
          serverCode: 'sidecar',
          status: 'APPROVED',
        },
        {
          id: '736287',
          created: '2021-05-05',
          type: 'delete_certificate',
          serverOwnerName: 'Eesti Põllumajandusloomade Jõudluskontrolli ASi',
          serverOnwerId: 'COM-777',
          serverCode: 'SS1',
          status: 'REJECTED',
        },
        {
          id: '234234',
          created: '2021-03-12',
          type: 'delete_client',
          serverOwnerName: 'Helsingin kristillisen koulun kannatusyhdistys',
          serverOnwerId: 'COM-666',
          serverCode: 'SS2',
          status: 'PENDING',
        },
        {
          id: '987283',
          created: '2021-04-22',
          type: 'register_certificate',
          serverOwnerName: 'Siseministeerium',
          serverOnwerId: 'DEV-444',
          serverCode: 'SS2',
          status: 'APPROVED',
        },
        {
          id: '123235',
          created: '2021-01-21',
          type: 'register_client',
          serverOwnerName: 'Turvallisuus- ja kemikaalivirasto',
          serverOnwerId: 'COM-555',
          serverCode: 'dev-toolkit-confidential.i.x-road',
          status: 'PENDING',
        },
      ],
    };
  },
});
</script>
