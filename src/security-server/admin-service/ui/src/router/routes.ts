/*
 * The MIT License
 *
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

import { RouteRecordRaw } from 'vue-router';

import { Permissions, RouteName } from '@/global';

import AppBase from '@/layouts/AppBase.vue';
import AppMainNavigation from '@/layouts/AppMainNavigation.vue';
import AddClient from '@/views/Clients/AddClient/AddClient.vue';
import AddKey from '@/views/AddKey/AddKey.vue';
import AddMember from '@/views/Clients/AddMember/AddMember.vue';
import AddSubsystem from '@/views/Clients/AddSubsystem/AddSubsystem.vue';
import AppError from '@/views/AppError.vue';
import AppForbidden from '@/views/AppForbidden.vue';
import AppLogin from '@/views/AppLogin.vue';
import CertificateDetailsView from '@/views/CertificateDetails/CertificateDetailsView.vue';
import ClientsView from '@/views/Clients/ClientsView.vue';
import ClientsListView from '@/views/Clients/ClientsListView.vue';
import ClientView from '@/views/Clients/ClientView.vue';
import ClientDetailsView from '@/views/Clients/Details/ClientDetailsView.vue';
import InternalServersView from '@/views/Clients/InternalServers/InternalServersView.vue';
import LocalGroupsView from '@/views/Clients/LocalGroups/LocalGroupsView.vue';
import AddServiceClientAccessRights from '@/views/Clients/ServiceClients/AddServiceClientAccessRightsWizard.vue';
import ServiceClientAccessRights from '@/views/Clients/ServiceClients/ServiceClientAccessRights.vue';
import ServiceClientsView from '@/views/Clients/ServiceClients/ServiceClientsView.vue';
import ServicesView from '@/views/Clients/Services/ServicesView.vue';
import SubsystemView from '@/views/Clients/SubsystemView.vue';
import ClientTlsCertificateView from '@/views/Clients/InternalServers/TlsCertificate/ClientTlsCertificateView.vue';
import DiagnosticsView from '@/views/Diagnostics/DiagnosticsView.vue';
import GenerateCertificateSignRequest from '@/views/GenerateCertificateSignRequest/GenerateCertificateSignRequest.vue';
import InitialConfigurationView from '@/views/InitialConfiguration/InitialConfigurationView.vue';
import InternalCertificateDetails from '@/views/InternalCertificateDetails/InternalCertificateDetails.vue';
import KeyDetails from '@/views/KeyDetails/KeyDetails.vue';
import ApiKey from '@/views/KeysAndCertificates/ApiKey/ApiKeysView.vue';
import CreateApiKeyStepper from '@/views/KeysAndCertificates/ApiKey/CreateApiKeyStepper.vue';
import KeysAndCertificates from '@/views/KeysAndCertificates/KeysAndCertificates.vue';
import SSTlsCertificate from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/SecurityServerTlsCertificate.vue';
import SignAndAuthKeys from '@/views/KeysAndCertificates/SignAndAuthKeys/SignAndAuthKeys.vue';
import LocalGroup from '@/views/Clients/LocalGroups/LocalGroup/LocalGroup.vue';
import EndpointAccessRights from '@/views/Clients/Services/Service/Endpoints/Endpoint/EndpointAccessRights.vue';
import EndpointDetails from '@/views/Clients/Services/Service/Endpoints/Endpoint/EndpointDetails.vue';
import EndpointsView from '@/views/Clients/Services/Service/Endpoints/EndpointsView.vue';
import ServiceParameters from '@/views/Clients/Services/Service/Parameters/ServiceParameters.vue';
import ServiceView from '@/views/Clients/Services/Service/ServiceView.vue';
import ServiceDescriptionDetailsView from '@/views/Clients/Services/ServiceDescriptionDetails/ServiceDescriptionDetailsView.vue';
import BackupAndRestore from '@/views/Settings/BackupAndRestore/BackupAndRestore.vue';
import SettingsView from '@/views/Settings/SettingsView.vue';
import SystemParameters from '@/views/Settings/SystemParameters/SystemParameters.vue';
import DiagnosticsOverview from '@/views/Diagnostics/Overview/DiagnosticsOverview.vue';
import TrafficContainer from '@/views/Diagnostics/Traffic/TrafficContainer.vue';
import ConnectionContainer from "@/views/Diagnostics/Connection/ConnectionContainer.vue";
import AdminUsersView from '@/views/Settings/AdminUsers/AdminUsersView.vue';
import AddAdminUserView from '@/views/Settings/AdminUsers/AddAdminUserView.vue';
import { XrdMainNavigationContainer } from '@niis/shared-ui';
import AppFooter from '@/layouts/AppFooter.vue';
import { useSettingsTabs } from '@/store/modules/settings-tabs';
import { useDiagnosticsTabs } from '@/store/modules/diagnostics-tabs';
import { useKeysTabs } from '@/store/modules/keys-tabs';
import { useMainTabs } from '@/store/modules/main-tabs';

const baseViewParts = {
  navigation: AppMainNavigation,
  footer: AppFooter,
};

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AppBase,
    name: RouteName.BaseRoute,
    redirect: () => useMainTabs().firstAllowedTab.to,
    children: [
      {
        name: RouteName.InitialConfiguration,
        path: '/initial-configuration',
        components: {
          default: InitialConfigurationView,
          navigation: XrdMainNavigationContainer,
        },
        meta: { permissions: [Permissions.INIT_CONFIG] },
      },
      {
        name: RouteName.Keys,
        path: '/keys',
        components: {
          ...baseViewParts,
          default: KeysAndCertificates,
        },
        props: {
          default: true,
        },
        meta: { permissions: [Permissions.VIEW_KEYS] },
        redirect: () => useKeysTabs().firstAllowedTab.to,
        children: [
          {
            name: RouteName.SignAndAuthKeys,
            path: 'sign-and-auth',
            component: SignAndAuthKeys,
            props: true,
            meta: { permissions: [Permissions.VIEW_KEYS] },
          },
          {
            name: RouteName.ApiKey,
            path: 'api-key',
            component: ApiKey,
            props: true,
            meta: {
              permissions: [
                Permissions.VIEW_API_KEYS,
                Permissions.CREATE_API_KEY,
                Permissions.UPDATE_API_KEY,
                Permissions.REVOKE_API_KEY,
              ],
            },
          },
          {
            name: RouteName.SSTlsCertificate,
            path: 'tls-cert',
            component: SSTlsCertificate,
            props: true,
            meta: { permissions: [Permissions.VIEW_INTERNAL_TLS_CERT] },
          },
        ],
      },
      {
        name: RouteName.CreateApiKey,
        path: '/keys/api-key/create',
        components: {
          default: CreateApiKeyStepper,
          navigation: XrdMainNavigationContainer,
        },
        props: {
          default: true,
        },
        meta: { permissions: [Permissions.CREATE_API_KEY] },
      },
      {
        name: RouteName.Diagnostics,
        path: '/diagnostics',
        meta: { permissions: [Permissions.DIAGNOSTICS] },
        components: {
          ...baseViewParts,
          default: DiagnosticsView,
        },
        redirect: () => useDiagnosticsTabs().firstAllowedTab.to,
        children: [
          {
            name: RouteName.DiagnosticsOverview,
            path: 'overview',
            component: DiagnosticsOverview,
            props: true,
            meta: { permissions: [Permissions.DIAGNOSTICS] },
          },
          {
            name: RouteName.DiagnosticsTraffic,
            path: 'traffic',
            component: TrafficContainer,
            props: true,
            meta: { permissions: [Permissions.DIAGNOSTICS] },
          },
          {
            name: RouteName.DiagnosticsConnection,
            path: 'connection',
            component: ConnectionContainer,
            props: true,
            meta: { permissions: [Permissions.DIAGNOSTICS] },
          },
        ],
      },
      {
        name: RouteName.Settings,
        path: '/settings',
        meta: {
          permissions: [
            Permissions.VIEW_SYS_PARAMS,
            Permissions.BACKUP_CONFIGURATION,
            Permissions.VIEW_ADMIN_USERS,
          ],
        },
        components: {
          ...baseViewParts,
          default: SettingsView,
        },
        redirect: () => useSettingsTabs().firstAllowedTab.to,
        children: [
          {
            name: RouteName.SystemParameters,
            path: 'system-parameters',
            component: SystemParameters,
            props: true,
            meta: { permissions: [Permissions.VIEW_SYS_PARAMS] },
          },
          {
            name: RouteName.BackupAndRestore,
            path: 'backup',
            component: BackupAndRestore,
            props: true,
            meta: { permissions: [Permissions.BACKUP_CONFIGURATION] },
          },
          {
            name: RouteName.AdminUsers,
            path: 'users',
            component: AdminUsersView,
            props: true,
            meta: {
              permissions: [
                Permissions.VIEW_ADMIN_USERS,
                Permissions.ADD_ADMIN_USER,
                Permissions.UPDATE_ADMIN_USER,
                Permissions.DELETE_ADMIN_USER,
              ],
            },
          },
        ],
      },
      {
        name: RouteName.AddAdminUser,
        path: '/settings/users/add',
        components: {
          default: AddAdminUserView,
          navigation: XrdMainNavigationContainer,
        },
        props: {
          default: true,
        },
        meta: { permissions: [Permissions.ADD_ADMIN_USER] },
      },
      {
        name: RouteName.AddSubsystem,
        path: '/add-subsystem/:instanceId/:memberClass/:memberCode/:memberName',
        components: {
          default: AddSubsystem,
          navigation: XrdMainNavigationContainer,
        },
        props: {
          default: true,
        },
        meta: { permissions: [Permissions.ADD_CLIENT] },
      },
      {
        name: RouteName.AddClient,
        path: '/add-client',
        components: {
          default: AddClient,
          navigation: XrdMainNavigationContainer,
        },
        meta: { permissions: [Permissions.ADD_CLIENT] },
      },
      {
        name: RouteName.AddMember,
        path: '/add-member/:ownerInstanceId/:ownerMemberClass/:ownerMemberCode',
        components: {
          default: AddMember,
          navigation: XrdMainNavigationContainer,
        },
        props: {
          default: true,
        },
        meta: { permissions: [Permissions.ADD_CLIENT] },
      },
      {
        path: '/clients',
        components: {
          ...baseViewParts,
          default: ClientsView,
        },
        children: [
          {
            name: RouteName.Clients,
            path: '',
            component: ClientsListView,
            meta: { permissions: [Permissions.VIEW_CLIENTS] },
          },
          {
            name: RouteName.Subsystem,
            path: 'subsystem/:id',
            meta: { permissions: [Permissions.VIEW_CLIENT_DETAILS] },
            redirect: { name: RouteName.SubsystemDetails },
            component: SubsystemView,
            props: true,
            children: [
              {
                name: RouteName.SubsystemDetails,
                path: 'details',
                component: ClientDetailsView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_DETAILS] },
              },
              {
                name: RouteName.SubsystemServiceClients,
                path: 'service-clients',
                component: ServiceClientsView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_ACL_SUBJECTS] },
              },
              {
                name: RouteName.SubsystemServices,
                path: 'services',
                component: ServicesView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_SERVICES] },
              },
              {
                name: RouteName.SubsystemServers,
                path: 'internal-servers',
                component: InternalServersView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS] },
              },
              {
                name: RouteName.SubsystemLocalGroups,
                path: 'local-groups',
                component: LocalGroupsView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_LOCAL_GROUPS] },
              },
            ],
          },
          {
            name: RouteName.Client,
            path: 'member/:id',
            meta: { permissions: [Permissions.VIEW_CLIENT_DETAILS] },
            redirect: { name: RouteName.MemberDetails },
            component: ClientView,
            props: true,
            children: [
              {
                name: RouteName.MemberDetails,
                path: 'details',
                component: ClientDetailsView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_DETAILS] },
              },
              {
                name: RouteName.MemberServers,
                path: 'internal-servers',
                component: InternalServersView,
                props: true,
                meta: { permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS] },
              },
            ],
          },
        ],
      },
      {
        name: RouteName.Certificate,
        path: '/certificate/:hash/:usage',
        components: {
          default: CertificateDetailsView,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.Key,
        path: '/key/:id',
        components: {
          default: KeyDetails,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.ClientTlsCertificate,
        path: '/client-tls-certificate/:id/:hash',
        components: {
          default: ClientTlsCertificateView,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
        meta: {
          permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERT_DETAILS],
        },
      },
      {
        name: RouteName.ServiceClientAccessRights,
        path: '/subsystem/:id/service-clients/:serviceClientId',
        props: { default: true },
        components: {
          default: ServiceClientAccessRights,
          navigation: XrdMainNavigationContainer,
        },
      },
      {
        name: RouteName.AddServiceClientAccessRight,
        path: '/subsystem/:id/service-clients/add',
        props: { default: true },
        components: {
          default: AddServiceClientAccessRights,
          navigation: XrdMainNavigationContainer,
        },
      },
      {
        name: RouteName.LocalGroup,
        path: '/local-group/:groupId',
        components: {
          default: LocalGroup,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.ServiceDescriptionDetails,
        path: '/service-description/:id',
        components: {
          default: ServiceDescriptionDetailsView,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.Service,
        path: '/service',
        components: {
          default: ServiceView,
          navigation: XrdMainNavigationContainer,
        },
        redirect: { name: RouteName.ServiceParameters },
        props: { default: true },
        children: [
          {
            name: RouteName.ServiceParameters,
            path: '/service/:serviceId/parameters',
            components: {
              default: ServiceParameters,
            },
          },
          {
            name: RouteName.Endpoints,
            path: '/service/:serviceId/endpoints',
            components: {
              default: EndpointsView,
            },
          },
        ],
      },
      {
        name: RouteName.EndpointDetails,
        path: '/endpoints/:id',
        components: {
          default: EndpointDetails,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.EndpointAccessRights,
        path: '/endpoints/:id/access-rights',
        components: {
          default: EndpointAccessRights,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.GenerateCertificateSignRequest,
        path: '/generate-csr/:keyId/:tokenType',
        components: {
          default: GenerateCertificateSignRequest,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.AddKey,
        path: '/add-key/:tokenId/:tokenType',
        components: {
          default: AddKey,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        name: RouteName.InternalTlsCertificate,
        path: '/internal-tls-certificate',
        components: {
          default: InternalCertificateDetails,
          navigation: XrdMainNavigationContainer,
        },
        props: { default: true },
      },
      {
        path: '/not-found',
        name: RouteName.NotFound,
        components: {
          ...baseViewParts,
          default: AppError,
        },
      },
      {
        path: '/forbidden',
        name: RouteName.Forbidden,
        components: {
          ...baseViewParts,
          default: AppForbidden,
        },
      },
    ],
  },
  {
    path: '/login',
    name: RouteName.Login,
    component: AppLogin,
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/not-found',
  },
  {
    path: '/:pathMatch(.*)',
    redirect: '/not-found',
  },
];

export default routes;
