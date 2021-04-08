/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import { RouteConfig } from 'vue-router';
import TabsBase from '@/components/layout/TabsBase.vue';

import AppLogin from '@/views/AppLogin.vue';
import AppBase from '@/views/AppBase.vue';

import AppError from '@/views/AppError.vue';

import { Permissions, RouteName } from '@/global';

import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import Settings from '@/views/Settings/Settings.vue';
import SettingsTabs from '@/views/Settings/SettingsTabs.vue';

import MockView1 from '@/views/MockView1.vue';
import MockView2 from '@/views/MockView2.vue';
import MockSubview from '@/views/MockSubview.vue';
import Members from '@/views/Members/Members.vue';

const routes: RouteConfig[] = [
  {
    path: '/',
    component: AppBase,
    name: RouteName.BaseRoute,
    redirect: { name: RouteName.Members },
    children: [
      {
        path: '/settings',
        meta: {
          permissions: [
            Permissions.MOCK_PERMISSION1,
            Permissions.MOCK_PERMISSION2,
          ],
        },
        components: {
          default: Settings,
          top: TabsBase,
          subTabs: SettingsTabs,
          alerts: AlertsContainer,
        },
        props: {
          subTabs: true,
        },
        children: [
          {
            name: RouteName.SystemSettings,
            path: '',
            component: MockSubview,
            props: true,
            meta: { permissions: [Permissions.MOCK_PERMISSION1] },
          },
          {
            name: RouteName.BackupAndRestore,
            path: 'backup',
            component: MockSubview,
            props: true,
            meta: { permissions: [Permissions.MOCK_PERMISSION1] },
          },
        ],
      },

      {
        name: RouteName.SecurityServers,
        path: '/security-servers',
        components: {
          default: MockView2,
          top: TabsBase,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.MOCK_PERMISSION1] },
      },

      {
        name: RouteName.Members,
        path: '/members',
        components: {
          default: Members,
          top: TabsBase,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.MOCK_PERMISSION1] },
      },

      {
        name: RouteName.TrustServices,
        path: '/trust-services',
        components: {
          default: MockView1,
          top: TabsBase,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.MOCK_PERMISSION1] },
      },

      {
        name: RouteName.ManagementRequests,
        path: '/management-requests',
        components: {
          default: MockView1,
          top: TabsBase,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.MOCK_PERMISSION1] },
      },

      {
        name: RouteName.GlobalConfiguration,
        path: '/global-configuration',
        components: {
          default: MockView1,
          top: TabsBase,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.MOCK_PERMISSION1] },
      },
    ],
  },
  {
    path: '/login',
    name: RouteName.Login,
    component: AppLogin,
  },
  {
    path: '*',
    component: AppError,
  },
];

export default routes;
