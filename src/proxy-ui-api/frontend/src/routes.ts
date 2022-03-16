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

import AddClient from '@/views/AddClient/AddClient.vue';
import AddKey from '@/views/AddKey/AddKey.vue';
import AddMember from '@/views/AddMember/AddMember.vue';
import AddServiceClientAccessRights from '@/views/Clients/ServiceClients/AddServiceClientAccessRightsWizard.vue';
import AddSubsystem from '@/views/AddSubsystem/AddSubsystem.vue';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import ApiKey from '@/views/KeysAndCertificates/ApiKey/ApiKey.vue';
import AppBase from '@/views/AppBase.vue';
import AppError from '@/views/AppError.vue';
import AppLogin from '@/views/AppLogin.vue';
import BackupAndRestore from '@/views/Settings/BackupAndRestore/BackupAndRestore.vue';
import CertificateDetails from '@/views/CertificateDetails/CertificateDetails.vue';
import Client from '@/views/Clients/Client.vue';
import ClientDetails from '@/views/Clients/Details/ClientDetails.vue';
import ClientTabs from '@/views/Clients/ClientTabs.vue';
import ClientTlsCertificate from '@/views/ClientTlsCertificate/ClientTlsCertificate.vue';
import Clients from '@/views/Clients/Clients.vue';
import CreateApiKeyStepper from '@/views/KeysAndCertificates/ApiKey/CreateApiKeyStepper.vue';
import Diagnostics from '@/views/Diagnostics/Diagnostics.vue';
import EndpointAccessRights from '@/views/Service/Endpoints/Endpoint/EndpointAccessRights.vue';
import EndpointDetails from '@/views/Service/Endpoints/Endpoint/EndpointDetails.vue';
import Endpoints from '@/views/Service/Endpoints/Endpoints.vue';
import GenerateCertificateSignRequest from '@/views/GenerateCertificateSignRequest/GenerateCertificateSignRequest.vue';
import GenerateInternalCsr from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/GenerateInternalCsr.vue';
import InternalCertificateDetails from '@/views/InternalCertificateDetails/InternalCertificateDetails.vue';
import InternalServers from '@/views/Clients/InternalServers/InternalServers.vue';
import KeyDetails from '@/views/KeyDetails/KeyDetails.vue';
import KeysAndCertificates from '@/views/KeysAndCertificates/KeysAndCertificates.vue';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';
import LocalGroup from '@/views/LocalGroup/LocalGroup.vue';
import LocalGroups from '@/views/Clients/LocalGroups/LocalGroups.vue';
import AppForbidden from '@/views/AppForbidden.vue';
import SSTlsCertificate from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/SecurityServerTlsCertificate.vue';
import Service from '@/views/Service/Service.vue';
import ServiceClientAccessRights from '@/views/Clients/ServiceClients/ServiceClientAccessRights.vue';
import ServiceClients from '@/views/Clients/ServiceClients/ServiceClients.vue';
import ServiceDescriptionDetails from '@/views/ServiceDescriptionDetails/ServiceDescriptionDetails.vue';
import ServiceParameters from '@/views/Service/Parameters/ServiceParameters.vue';
import Services from '@/views/Clients/Services/Services.vue';
import Settings from '@/views/Settings/Settings.vue';
import SettingsTabs from '@/views/Settings/SettingsTabs.vue';
import SignAndAuthKeys from '@/views/KeysAndCertificates/SignAndAuthKeys/SignAndAuthKeys.vue';
import Subsystem from '@/views/Clients/Subsystem.vue';
import SubsystemTabs from '@/views/Clients/SubsystemTabs.vue';
import SystemParameters from '@/views/Settings/SystemParameters/SystemParameters.vue';
import TabsBase from '@/components/layout/TabsBase.vue';
import TokenDetails from '@/views/TokenDetails/TokenDetails.vue';
import InitialConfiguration from '@/views/InitialConfiguration/InitialConfiguration.vue';
import TabsBaseEmpty from '@/components/layout/TabsBaseEmpty.vue';
import { Permissions, RouteName } from '@/global';
import i18n from './i18n';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { NavigationGuardNext, Route, RouteConfig } from 'vue-router';

const routes: RouteConfig[] = [
  {
    path: '/',
    component: AppBase,
    name: RouteName.BaseRoute,
    redirect: { name: RouteName.Clients },
    children: [
      {
        path: '/keys',
        components: {
          default: KeysAndCertificates,
          top: TabsBase,
          subTabs: KeysAndCertificatesTabs,
          alerts: AlertsContainer,
        },
        props: {
          default: true,
          subTabs: true,
        },
        children: [
          {
            name: RouteName.SignAndAuthKeys,
            path: '',
            component: SignAndAuthKeys,
            props: true,
          },
          {
            name: RouteName.ApiKey,
            path: 'apikey',
            component: ApiKey,
            props: true,
          },
          {
            name: RouteName.SSTlsCertificate,
            path: 'tls-cert',
            component: SSTlsCertificate,
            props: true,
          },
        ],
      },
      {
        name: RouteName.CreateApiKey,
        path: '/keys/apikey/create',
        components: {
          default: CreateApiKeyStepper,
          alerts: AlertsContainer,
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.GenerateInternalCSR,
        path: '/keys/tsl-cert/generate-csr',
        components: {
          default: GenerateInternalCsr,
          alerts: AlertsContainer,
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.Diagnostics,
        path: '/diagnostics',
        components: {
          default: Diagnostics,
          top: TabsBase,
          alerts: AlertsContainer,
        },
      },
      {
        path: '/settings',
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
            name: RouteName.SystemParameters,
            path: '',
            component: SystemParameters,
            props: true,
          },
          {
            name: RouteName.BackupAndRestore,
            path: 'backup',
            component: BackupAndRestore,
            props: true,
          },
        ],
      },
      {
        name: RouteName.AddSubsystem,
        path: '/add-subsystem/:instanceId/:memberClass/:memberCode/:memberName',
        components: {
          default: AddSubsystem,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.AddClient,
        path: '/add-client',
        components: {
          default: AddClient,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
      },
      {
        name: RouteName.AddMember,
        path: '/add-member/:instanceId/:memberClass/:memberCode',
        components: {
          default: AddMember,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: {
          default: true,
        },
      },
      {
        name: RouteName.Subsystem,
        path: '/subsystem',
        redirect: '/subsystem/details/:id',
        components: {
          default: Subsystem,
          top: TabsBase,
          subTabs: SubsystemTabs,
          alerts: AlertsContainer,
        },
        props: {
          default: true,
          subTabs: true,
        },
        children: [
          {
            name: RouteName.SubsystemDetails,
            path: '/subsystem/details/:id',
            component: ClientDetails,
            props: true,
          },
          {
            name: RouteName.SubsystemServiceClients,
            path: '/subsystem/serviceclients/:id',
            component: ServiceClients,
            props: true,
          },
          {
            name: RouteName.SubsystemServices,
            path: '/subsystem/services/:id',
            component: Services,
            props: true,
          },
          {
            name: RouteName.SubsystemServers,
            path: '/subsystem/internalservers/:id',
            component: InternalServers,
            props: true,
          },
          {
            name: RouteName.SubsystemLocalGroups,
            path: '/subsystem/localgroups/:id',
            component: LocalGroups,
            props: true,
          },
        ],
      },
      {
        name: RouteName.Client,
        path: '/client',
        redirect: '/client/details/:id',
        components: {
          default: Client,
          top: TabsBase,
          subTabs: ClientTabs,
          alerts: AlertsContainer,
        },
        props: { default: true, subTabs: true },
        children: [
          {
            name: RouteName.MemberDetails,
            path: '/client/details/:id',
            component: ClientDetails,
            props: true,
          },
          {
            name: RouteName.MemberServers,
            path: '/client/internalservers/:id',
            component: InternalServers,
            props: true,
          },
        ],
      },
      {
        name: RouteName.Clients,
        path: '/clients',
        components: {
          default: Clients,
          top: TabsBase,
          alerts: AlertsContainer,
        },
      },
      {
        name: RouteName.Certificate,
        path: '/certificate/:hash/:usage',
        components: {
          default: CertificateDetails,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.Token,
        path: '/token/:id',
        components: {
          default: TokenDetails,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.Key,
        path: '/key/:id',
        components: {
          default: KeyDetails,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.ClientTlsCertificate,
        path: '/client-tls-certificate/:id/:hash',
        components: {
          default: ClientTlsCertificate,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.ServiceClientAccessRights,
        path: '/subsystem/:id/serviceclients/:serviceClientId',
        props: { default: true },
        components: {
          default: ServiceClientAccessRights,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
      },
      {
        name: RouteName.AddServiceClientAccessRight,
        path: '/subsystem/serviceclients/:id/add',
        props: { default: true },
        components: {
          default: AddServiceClientAccessRights,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
      },
      {
        name: RouteName.LocalGroup,
        path: '/localgroup/:clientId/:groupId',
        components: {
          default: LocalGroup,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.ServiceDescriptionDetails,
        path: '/service-description/:id',
        components: {
          default: ServiceDescriptionDetails,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.Service,
        path: '/service',
        components: {
          default: Service,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        redirect: '/service/:clientId/:serviceId/parameters',
        props: { default: true },
        children: [
          {
            name: RouteName.ServiceParameters,
            path: '/service/:clientId/:serviceId/parameters',
            components: {
              default: ServiceParameters,
            },
            props: { default: true },
          },
          {
            name: RouteName.Endpoints,
            path: '/service/:clientId/:serviceId/endpoints',
            components: {
              default: Endpoints,
            },
            props: { default: true },
          },
        ],
      },
      {
        name: RouteName.EndpointDetails,
        path: '/service/:clientId/:serviceId/endpoints/:id',
        components: {
          default: EndpointDetails,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.EndpointAccessRights,
        path: '/service/:clientId/:serviceId/endpoints/:id/accessrights',
        components: {
          default: EndpointAccessRights,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.GenerateCertificateSignRequest,
        path: '/generate-csr/:keyId/:tokenType',
        components: {
          default: GenerateCertificateSignRequest,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.AddKey,
        path: '/add-key/:tokenId/:tokenType',
        components: {
          default: AddKey,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.InternalTlsCertificate,
        path: '/internal-tls-certificate',
        components: {
          default: InternalCertificateDetails,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        props: { default: true },
      },
      {
        name: RouteName.InitialConfiguration,
        path: '/initial-configuration',
        components: {
          default: InitialConfiguration,
          alerts: AlertsContainer,
          top: TabsBaseEmpty,
        },
        beforeEnter: (
          to: Route,
          from: Route,
          next: NavigationGuardNext,
        ): void => {
          // Coming from login is ok
          if (from.name === RouteName.Login) {
            next();
            return;
          }

          const notifications = useNotifications();
          const user = useUser();

          // Coming from somewhere else, needs a check
          if (user.needsInitialization) {
            // Check if the user has permission to initialize the server
            if (!user.hasPermission(Permissions.INIT_CONFIG)) {
              notifications.showErrorMessage(
                i18n.t('initialConfiguration.noPermission'),
              );

              return;
            }
            next();
          }
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
    path: '/forbidden',
    name: RouteName.Forbidden,
    component: AppForbidden,
  },
  {
    path: '*',
    component: AppError,
  },
];

export default routes;
