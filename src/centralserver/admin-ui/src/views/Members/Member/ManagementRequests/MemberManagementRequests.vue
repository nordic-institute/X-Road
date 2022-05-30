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
          id: 13,
          type: 'AUTH_CERT_REGISTRATION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Tartu Kesklinna Perearstikeskus OÜ',
          security_server_id: {
            instance_id: 'DEV7X',
            type: 'SERVER',
            member_class: 'TST',
            member_code: 'MEMBER1',
            server_code: 'RH1',
          },
          status: 'APPROVED',
          created_at: '2021-07-07T10:09:42.10186Z',
        },
        {
          id: 736287,
          type: 'CLIENT_REGISTRATION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Eesti Põllumajandusloomade Jõudluskontrolli ASi',
          security_server_id: {
            instance_id: 'DEV9X',
            type: 'SERVER',
            member_class: 'TST',
            member_code: 'MEMBER22',
            server_code: 'RH3',
          },
          status: 'REJECTED',
          created_at: '2021-02-08T10:09:40.10186Z',
        },
        {
          id: 64,
          type: 'OWNER_CHANGE_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Helsingin kristillisen koulun kannatusyhdistys',
          security_server_id: {
            instance_id: 'OPP',
            type: 'SERVER',
            member_class: 'RAA',
            member_code: 'MEMBER7',
            server_code: 'X1',
          },
          status: 'PENDING',
          created_at: '2021-03-11T10:09:40.10186Z',
        },
        {
          id: 112283,
          type: 'CLIENT_DELETION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Siseministeerium',
          security_server_id: {
            instance_id: 'WAP',
            type: 'SERVER',
            member_class: 'MOP',
            member_code: 'MEM227',
            server_code: 'K8',
          },
          status: 'APPROVED',
          created_at: '2020-12-13T10:09:40.10186Z',
        },
        {
          id: 947283,
          type: 'AUTH_CERT_DELETION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Turvallisuus- ja kemikaalivirasto',
          security_server_id: {
            instance_id: 'NEO',
            type: 'SERVER',
            member_class: 'AUS',
            member_code: 'MEMBER9',
            server_code: 'SR2',
          },
          status: 'PENDING',
          created_at: '2020-12-13T10:09:40.10186Z',
        },
      ],
    };
  },
});
</script>
