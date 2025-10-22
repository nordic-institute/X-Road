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

import { RouteLocationNormalized } from 'vue-router';

import { XrdMainNavigationContainer, XrdRoute } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { useSettingsTabs } from '@/store/modules/settings-tabs';

import AlertsContainer from '@/components/AlertsContainer.vue';
import AppBase from '@/layouts/AppBase.vue';
import AppFooter from '@/layouts/AppFooter.vue';
import AppMainNavigation from '@/layouts/AppMainNavigation.vue';
import AppError from '@/views/AppError.vue';
import AppForbidden from '@/views/AppForbidden.vue';
import AppLogin from '@/views/AppLogin.vue';
import ExternalConfigurationView from '@/views/GlobalConfiguration/ExternalConfiguration/ExternalConfigurationView.vue';
import GlobalConfigurationView from '@/views/GlobalConfiguration/GlobalConfigurationView.vue';
import InternalConfigurationView from '@/views/GlobalConfiguration/InternalConfiguration/InternalConfigurationView.vue';
import TrustedAnchorsView from '@/views/GlobalConfiguration/TrustedAnchors/TrustedAnchorsView.vue';
import InitialConfigurationView from '@/views/InitialConfiguration/InitialConfigurationView.vue';
import ManagementRequestDetails from '@/views/ManagementRequests/ManagementRequestDetails.vue';
import ManagementRequests from '@/views/ManagementRequests/ManagementRequests.vue';
import ManagementRequestsList from '@/views/ManagementRequests/ManagementRequestsList.vue';
import MemberDetails from '@/views/Members/Member/Details/MemberDetails.vue';
import MemberView from '@/views/Members/Member/MemberView.vue';
import MemberSubsystems from '@/views/Members/Member/Subsystems/MemberSubsystems.vue';
import MemberList from '@/views/Members/MemberList.vue';
import MembersView from '@/views/Members/MembersView.vue';
import SecurityServerAuthenticationCertificate from '@/views/SecurityServers/SecurityServer/SecurityServerAuthenticationCertificate.vue';
import SecurityServerAuthenticationCertificates from '@/views/SecurityServers/SecurityServer/SecurityServerAuthenticationCertificates.vue';
import SecurityServerClients from '@/views/SecurityServers/SecurityServer/SecurityServerClients.vue';
import SecurityServerDetails from '@/views/SecurityServers/SecurityServer/SecurityServerDetails.vue';
import SecurityServerView from '@/views/SecurityServers/SecurityServer/SecurityServerView.vue';
import SecurityServersList from '@/views/SecurityServers/SecurityServersList.vue';
import SecurityServersView from '@/views/SecurityServers/SecurityServersView.vue';
import ApiKeysView from '@/views/Settings/ApiKeys/ApiKeysView.vue';
import CreateApiKeyStepper from '@/views/Settings/ApiKeys/CreateApiKeyStepper.vue';
import BackupAndRestoreView from '@/views/Settings/BackupAndRestore/BackupAndRestoreView.vue';
import GlobalGroupView from '@/views/Settings/GlobalResources/GlobalGroup/GlobalGroupView.vue';
import GlobalGroupsList from '@/views/Settings/GlobalResources/GlobalGroupsList.vue';
import GlobalResourcesView from '@/views/Settings/GlobalResources/GlobalResourcesView.vue';
import SettingsView from '@/views/Settings/SettingsView.vue';
import SystemSettingsView from '@/views/Settings/SystemSettings/SystemSettingsView.vue';
import ManagementServiceCertificate from '@/views/Settings/TlsCertificates/ManagementServiceCertificate.vue';
import ManagementServiceTlsCertificateView from '@/views/Settings/TlsCertificates/ManagementServiceTlsCertificateView.vue';
import CertificationServiceCertificate from '@/views/TrustServices/CertificationServices/CertificationService/CertificationServiceCertificate.vue';
import CertificationServiceDetails from '@/views/TrustServices/CertificationServices/CertificationService/CertificationServiceDetails.vue';
import CertificationServiceIntermediateCas from '@/views/TrustServices/CertificationServices/CertificationService/CertificationServiceIntermediateCas.vue';
import CertificationServiceOcspResponders from '@/views/TrustServices/CertificationServices/CertificationService/CertificationServiceOcspResponders.vue';
import CertificationServiceSettings from '@/views/TrustServices/CertificationServices/CertificationService/CertificationServiceSettings.vue';
import CertificationServiceView from '@/views/TrustServices/CertificationServices/CertificationService/CertificationServiceView.vue';
import IntermediateCACertificate from '@/views/TrustServices/CertificationServices/CertificationService/IntermediateCa/IntermediateCACertificate.vue';
import IntermediateCaDetails from '@/views/TrustServices/CertificationServices/CertificationService/IntermediateCa/IntermediateCaDetails.vue';
import IntermediateCaOcspResponders from '@/views/TrustServices/CertificationServices/CertificationService/IntermediateCa/IntermediateCaOcspResponders.vue';
import IntermediateCaView from '@/views/TrustServices/CertificationServices/CertificationService/IntermediateCa/IntermediateCaView.vue';
import OcspResponderCertificate from '@/views/TrustServices/CertificationServices/CertificationService/OcspResponders/OcspResponderCertificate.vue';
import TimestampingServiceCertificate from '@/views/TrustServices/TimestampingServices/TimestampingServiceCertificate.vue';
import TrustServices from '@/views/TrustServices/TrustServices.vue';
import TrustServicesView from '@/views/TrustServices/TrustServicesView.vue';
import { useGlobalConfTabs } from '@/store/modules/global-conf-tabs';
import { useMainTabs } from '@/store/modules/main-tabs';

const routes = [
  {
    path: '/',
    component: AppBase,
    name: RouteName.BaseRoute,
    redirect: ()=>useMainTabs().firstAllowedTab.to,
    children: [
      {
        name: RouteName.Settings,
        path: '/settings',
        meta: {
          permissions: [
            Permissions.VIEW_SYSTEM_SETTINGS,
            Permissions.VIEW_GLOBAL_GROUPS,
            Permissions.VIEW_SECURITY_SERVERS,
            Permissions.BACKUP_CONFIGURATION,
            Permissions.VIEW_API_KEYS,
          ],
        },
        components: {
          default: SettingsView,
          navigation: AppMainNavigation,
          footer: AppFooter,
          alerts: AlertsContainer,
        },
        redirect: () => useSettingsTabs().firstAllowedTab.to,
        props: {
          subTabs: true,
        },
        children: [
          {
            path: 'global-resources',
            component: GlobalResourcesView,
            props: true,
            meta: {
              permissions: [
                Permissions.VIEW_GLOBAL_GROUPS,
                Permissions.VIEW_SECURITY_SERVERS,
              ],
            },
            children: [
              {
                name: RouteName.GlobalGroups,
                path: '',
                component: GlobalGroupsList,
                props: true,
              },
              {
                name: RouteName.GlobalGroup,
                path: 'global-groups/:groupCode',
                component: GlobalGroupView,
                props: true,
                meta: { permissions: [Permissions.VIEW_GROUP_DETAILS] },
              },
            ],
          },
          {
            name: RouteName.SystemSettings,
            path: 'system-settings',
            component: SystemSettingsView,
            props: true,
            meta: { permissions: [Permissions.VIEW_SYSTEM_SETTINGS] },
          },
          {
            name: RouteName.BackupAndRestore,
            path: 'backup',
            component: BackupAndRestoreView,
            props: true,
            meta: { permissions: [Permissions.BACKUP_CONFIGURATION] },
          },
          {
            name: RouteName.ApiKeys,
            path: 'api-keys',
            component: ApiKeysView,
            props: true,
            meta: { permissions: [Permissions.VIEW_API_KEYS] },
          },
          {
            name: RouteName.TlsCertificates,
            path: 'tls-certificates',
            component: ManagementServiceTlsCertificateView,
            props: true,
            meta: {
              permissions: [Permissions.VIEW_MANAGEMENT_SERVICE_TLS_CERT],
            },
          },
        ],
      },
      {
        name: RouteName.ManagementServiceCertificateDetails,
        path: '/tls-certificates-details',
        components: {
          default: ManagementServiceCertificate,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.VIEW_TLS_CERTIFICATES] },
      },

      {
        name: RouteName.CreateApiKey,
        path: '/keys/apikey/create',
        components: {
          default: CreateApiKeyStepper,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        props: {
          default: true,
        },
        meta: { permissions: [Permissions.CREATE_API_KEY], backOnEscape: true },
      },

      {
        path: '/members',
        components: {
          default: MembersView,
          navigation: AppMainNavigation,
          footer: AppFooter,
          alerts: AlertsContainer,
        },
        children: [
          {
            name: RouteName.Members,
            path: '',
            component: MemberList,
            meta: {
              permissions: [Permissions.VIEW_MEMBERS],
            },
          },
          {
            path: ':memberId',
            components: {
              default: MemberView,
            },
            meta: {
              permissions: [Permissions.VIEW_MEMBER_DETAILS],
            },
            props: { default: true },
            redirect: { name: RouteName.MemberDetails },
            children: [
              {
                name: RouteName.MemberDetails,
                path: 'details',
                component: MemberDetails,
                meta: { permissions: [Permissions.VIEW_MEMBER_DETAILS] },
                props: true,
              },
              {
                name: RouteName.MemberSubsystems,
                path: 'subsystems',
                component: MemberSubsystems,
                meta: { permissions: [Permissions.VIEW_MEMBER_DETAILS] },
                props: true,
              },
            ],
          },
        ],
      },

      {
        path: '/security-servers',
        components: {
          default: SecurityServersView,
          navigation: AppMainNavigation,
          footer: AppFooter,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.VIEW_SECURITY_SERVERS] },
        children: [
          {
            name: RouteName.SecurityServers,
            path: '',
            component: SecurityServersList,
            meta: {
              permissions: [Permissions.VIEW_SECURITY_SERVERS],
            },
          },
          {
            path: ':serverId',
            components: {
              default: SecurityServerView,
            },
            props: { default: true },
            redirect: {
              name: RouteName.SecurityServerDetails,
            },
            meta: {
              permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
            },
            children: [
              {
                name: RouteName.SecurityServerDetails,
                path: 'details',
                component: SecurityServerDetails,
                props: true,
                meta: {
                  permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
                },
              },
              {
                name: RouteName.SecurityServerAuthenticationCertificates,
                path: 'authentication-certificates',
                component: SecurityServerAuthenticationCertificates,
                meta: {
                  permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
                },
                props: (
                  route: RouteLocationNormalized,
                ): { serverId: string } => {
                  return { serverId: route.params.serverId as string };
                },
              },
              {
                name: RouteName.SecurityServerClients,
                path: 'clients',
                component: SecurityServerClients,
                meta: {
                  permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
                },
                props: true,
              },
            ],
          },
        ],
      },

      {
        path: '/security-servers/:serverId/authentication-certificates/:certificateId',
        name: RouteName.SecurityServerAuthenticationCertificate,
        components: {
          default: SecurityServerAuthenticationCertificate,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        props: { default: true },
        meta: {
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },
      },

      {
        path: '/trust-services',
        components: {
          default: TrustServices,
          navigation: AppMainNavigation,
          alerts: AlertsContainer,
        },
        children: [
          {
            name: RouteName.TrustServices,
            path: '',
            component: TrustServicesView,
            meta: {
              permissions: [Permissions.VIEW_APPROVED_CAS],
            },
          },
          {
            path: '/certification-services/:certificationServiceId',
            component: CertificationServiceView,
            meta: {
              permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
            },
            props: (route: RouteLocationNormalized) => ({
              certificationServiceId: route.params.certificationServiceId,
            }),
            redirect: '/certification-services/:certificationServiceId/details',
            children: [
              {
                name: RouteName.CertificationServiceDetails,
                path: 'details',
                component: CertificationServiceDetails,
                meta: { permissions: [Permissions.VIEW_APPROVED_CA_DETAILS] },
              },
              {
                name: RouteName.CertificationServiceSettings,
                path: 'settings',
                component: CertificationServiceSettings,
                meta: { permissions: [Permissions.EDIT_APPROVED_CA] },
              },
              {
                name: RouteName.CertificationServiceOcspResponders,
                path: 'ocsp-responders',
                component: CertificationServiceOcspResponders,
                meta: { permissions: [Permissions.VIEW_APPROVED_CA_DETAILS] },
              },
              {
                name: RouteName.CertificationServiceIntermediateCas,
                path: 'intermediate-cas',
                component: CertificationServiceIntermediateCas,
                meta: { permissions: [Permissions.VIEW_APPROVED_CA_DETAILS] },
              },
            ],
          },
          {
            path: '/intermediate-cas/:intermediateCaId',
            component: IntermediateCaView,
            meta: {
              permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
            },
            props: true,
            redirect: '/intermediate-cas/:intermediateCaId/details',
            children: [
              {
                name: RouteName.IntermediateCaDetails,
                path: 'details',
                component: IntermediateCaDetails,
                meta: { permissions: [Permissions.VIEW_APPROVED_CA_DETAILS] },
              },
              {
                name: RouteName.IntermediateCaOcspResponders,
                path: 'ocsp-responders',
                component: IntermediateCaOcspResponders,
                meta: { permissions: [Permissions.VIEW_APPROVED_CA_DETAILS] },
              },
            ],
          },
        ],
      },
      {
        name: RouteName.TimestampingServiceCertificateDetails,
        path: '/timestamping-service-certificate/:timestampingServiceId',
        components: {
          default: TimestampingServiceCertificate,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        meta: {
          permissions: [Permissions.VIEW_APPROVED_TSAS],
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.CertificationServiceCertificateDetails,
        path: '/certification-services/:certificationServiceId/certificate-details',
        components: {
          default: CertificationServiceCertificate,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        meta: {
          permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.OcspResponderCertificateDetails,
        path: 'ocsp-responder/:ocspResponderId/certificate-details',
        components: {
          default: OcspResponderCertificate,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        meta: {
          permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.IntermediateCACertificateDetails,
        path: '/intermediate-cas/:intermediateCaId/certificate-details',
        components: {
          default: IntermediateCACertificate,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        meta: {
          permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
        },
        props: {
          default: true,
        },
      },

      {
        name: RouteName.Initialisation,
        path: '/init',
        components: {
          default: InitialConfigurationView,
          navigation: XrdMainNavigationContainer,
          alerts: AlertsContainer,
        },
        meta: { permissions: [Permissions.INIT_CONFIG] },
      },

      {
        path: '/management-requests',
        components: {
          default: ManagementRequests,
          navigation: AppMainNavigation,
          alerts: AlertsContainer,
        },
        children: [
          {
            name: RouteName.ManagementRequests,
            path: '',
            component: ManagementRequestsList,
            meta: {
              permissions: [Permissions.VIEW_MANAGEMENT_REQUESTS],
            },
          },
          {
            name: RouteName.ManagementRequestDetails,
            path: ':requestId/details',
            component: ManagementRequestDetails,
            props(route: RouteLocationNormalized): { requestId: number } {
              const requestId = Number(route.params.requestId);
              return { requestId };
            },
            meta: {
              permissions: [Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS],
            },
          },
        ],
      },

      {
        name: RouteName.GlobalConfiguration,
        path: '/global-configuration',
        components: {
          default: GlobalConfigurationView,
          navigation: AppMainNavigation,
          alerts: AlertsContainer,
        },
        props: {
          subTabs: true,
        },
        meta: { permissions: [Permissions.VIEW_CONFIGURATION_MANAGEMENT] },
        redirect: () => useGlobalConfTabs().firstAllowedTab.to,
        children: [
          {
            name: RouteName.InternalConfiguration,
            path: 'internal-configuration',
            component: InternalConfigurationView,
            props: true,
            meta: {
              permissions: [Permissions.VIEW_INTERNAL_CONFIGURATION_SOURCE],
            },
          },
          {
            name: RouteName.ExternalConfiguration,
            path: 'external-configuration',
            component: ExternalConfigurationView,
            props: true,
            meta: {
              permissions: [Permissions.VIEW_EXTERNAL_CONFIGURATION_SOURCE],
            },
          },
          {
            name: RouteName.TrustedAnchors,
            path: 'trusted-anchors',
            component: TrustedAnchorsView,
            props: true,
            meta: { permissions: [Permissions.VIEW_TRUSTED_ANCHORS] },
          },
        ],
      },
      {
        path: '/not-found',
        name: RouteName.NotFound,
        components: {
          default: AppError,
          navigation: AppMainNavigation,
          alerts: AlertsContainer,
          footer: AppFooter,
        },
      },
      {
        path: '/forbidden',
        name: RouteName.Forbidden,
        components: {
          default: AppForbidden,
          navigation: AppMainNavigation,
          alerts: AlertsContainer,
          footer: AppFooter,
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
] as XrdRoute[];

export default routes;
