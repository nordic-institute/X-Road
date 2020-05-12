import Router, { NavigationGuard, Route } from 'vue-router';
import { sync } from 'vuex-router-sync';
import TabsBase from '@/components/layout/TabsBase.vue';
import AppLogin from '@/views/AppLogin.vue';
import AppBase from '@/views/AppBase.vue';
import Clients from '@/views/Clients/Clients.vue';
import Client from '@/views/Clients/Client.vue';
import KeysAndCertificates from '@/views/KeysAndCertificates/KeysAndCertificates.vue';
import SignAndAuthKeys from '@/views/KeysAndCertificates/SignAndAuthKeys/SignAndAuthKeys.vue';
import SSTlsCertificate from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/SecurityServerTlsCertificate.vue';
import ApiKey from '@/views/KeysAndCertificates/ApiKey/ApiKey.vue';
import Settings from '@/views/Settings/Settings.vue';
import SystemParameters from '@/views/Settings/SystemParameters/SystemParameters.vue';
import BackupAndRestore from '@/views/Settings/BackupAndRestore/BackupAndRestore.vue';
import Diagnostics from '@/views/Diagnostics/Diagnostics.vue';
import AddSubsystem from '@/views/AddSubsystem/AddSubsystem.vue';
import AddClient from '@/views/AddClient/AddClient.vue';
import AddMember from '@/views/AddMember/AddMember.vue';
import Subsystem from '@/views/Clients/Subsystem.vue';
import ClientDetails from '@/views/Clients/Details/ClientDetails.vue';
import InternalServers from '@/views/Clients/InternalServers/InternalServers.vue';
import Services from '@/views/Clients/Services/Services.vue';
import ServiceClients from '@/views/Clients/ServiceClients/ServiceClients.vue';
import LocalGroups from '@/views/Clients/LocalGroups/LocalGroups.vue';
import ClientTlsCertificate from '@/views/ClientTlsCertificate/ClientTlsCertificate.vue';
import AppError from '@/views/AppError.vue';
import LocalGroup from '@/views/LocalGroup/LocalGroup.vue';
import ServiceDescriptionDetails from '@/views/ServiceDescriptionDetails/ServiceDescriptionDetails.vue';
import TokenDetails from '@/views/TokenDetails/TokenDetails.vue';
import KeyDetails from '@/views/KeyDetails/KeyDetails.vue';
import CertificateDetails from '@/views/CertificateDetails/CertificateDetails.vue';
import Service from '@/views/Service/Service.vue';
import GenerateCertificateSignRequest from '@/views/GenerateCertificateSignRequest/GenerateCertificateSignRequest.vue';
import AddKey from '@/views/AddKey/AddKey.vue';
import store from '@/store';
import { Permissions, RouteName } from '@/global';
import ServiceParameters from '@/views/Service/Parameters/ServiceParameters.vue';
import InternalCertificateDetails from '@/views/InternalCertificateDetails/InternalCertificateDetails.vue';
import EndpointDetails from '@/views/Service/Endpoints/Endpoint/EndpointDetails.vue';
import EndpointAccessRights from '@/views/Service/Endpoints/Endpoint/EndpointAccessRights.vue';
import Endpoints from '@/views/Service/Endpoints/Endpoints.vue';
import GenerateInternalCsr from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/GenerateInternalCsr.vue';
import CreateApiKeyStepper from '@/views/KeysAndCertificates/ApiKey/CreateApiKeyStepper.vue';
import ServiceClientAccessRights from '@/views/Clients/ServiceClients/ServiceClientAccessRights.vue';

// At the moment the vue router does not have a type for Next.
// Using this solution was recommended in a github comment:
// https://github.com/vuejs/vue-router/pull/2497#issuecomment-474010032
type Next = Parameters<NavigationGuard>[2];

const router = new Router({
  routes: [
    {
      path: '/',
      component: AppBase,
      children: [
        {
          path: '/keys',
          components: {
            default: KeysAndCertificates,
            top: TabsBase,
          },
          meta: { permission: Permissions.VIEW_KEYS },
          children: [
            {
              name: RouteName.SignAndAuthKeys,
              path: '',
              component: SignAndAuthKeys,
              props: true,
              meta: { permission: Permissions.VIEW_KEYS },
            },
            {
              name: RouteName.ApiKey,
              path: 'apikey',
              component: ApiKey,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS },
            },
            {
              name: RouteName.SSTlsCertificate,
              path: 'tls-cert',
              component: SSTlsCertificate,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS },
            },
          ],
        },
        {
          name: RouteName.CreateApiKey,
          path: '/keys/apikey/create',
          component: CreateApiKeyStepper,
          props: true,
          meta: { permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS },
        },
        {
          name: RouteName.GenerateInternalCSR,
          path: '/keys/tsl-cert/generate-csr',
          component: GenerateInternalCsr,
          meta: { permission: Permissions.GENERATE_INTERNAL_SSL_CSR },
          props: true,
        },
        {
          name: RouteName.Diagnostics,
          path: '/diagnostics',
          components: {
            default: Diagnostics,
            top: TabsBase,
          },
          meta: { permission: Permissions.DIAGNOSTICS },
        },
        {
          path: '/settings',
          components: {
            default: Settings,
            top: TabsBase,
          },
          children: [
            {
              name: RouteName.SystemParameters,
              path: 'system-parameters',
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
          },
        },
        {
          name: RouteName.AddMember,
          path: '/add-member/:instanceId/:memberClass/:memberCode',
          components: {
            default: AddMember,
          },
          props: {
            default: true,
          },
        },
        {
          name: RouteName.Subsystem,
          path: '/subsystem',
          meta: { permission: Permissions.VIEW_CLIENT_DETAILS },
          redirect: '/subsystem/details/:id',
          components: {
            default: Subsystem,
            top: TabsBase,
          },
          props: {
            default: true,
          },
          children: [
            {
              name: RouteName.SubsystemDetails,
              path: '/subsystem/details/:id',
              component: ClientDetails,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_DETAILS },
            },
            {
              name: RouteName.SubsystemServiceClients,
              path: '/subsystem/serviceclients/:id',
              component: ServiceClients,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS },
            },
            {
              name: RouteName.SubsystemServices,
              path: '/subsystem/services/:id',
              component: Services,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_SERVICES },
            },
            {
              name: RouteName.SubsystemServers,
              path: '/subsystem/internalservers/:id',
              component: InternalServers,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_INTERNAL_CERTS },
            },
            {
              name: RouteName.SubsystemLocalGroups,
              path: '/subsystem/localgroups/:id',
              component: LocalGroups,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_LOCAL_GROUPS },
            },
          ],
        },
        {
          name: RouteName.Client,
          path: '/client',
          meta: { permission: Permissions.VIEW_CLIENT_DETAILS },
          redirect: '/client/details/:id',
          components: {
            default: Client,
            top: TabsBase,
          },
          props: { default: true },
          children: [
            {
              name: RouteName.MemberDetails,
              path: '/client/details/:id',
              component: ClientDetails,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_DETAILS },
            },
            {
              name: RouteName.MemberServers,
              path: '/client/internalservers/:id',
              component: InternalServers,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_INTERNAL_CERTS },
            },
          ],
        },
        {
          name: RouteName.Clients,
          path: '',
          components: {
            default: Clients,
            top: TabsBase,
          },
          meta: { permission: Permissions.VIEW_CLIENTS },
        },
        {
          name: RouteName.Certificate,
          path: '/certificate/:hash/:usage',
          components: {
            default: CertificateDetails,
          },
          props: { default: true },
        },
        {
          name: RouteName.Token,
          path: '/token/:id',
          components: {
            default: TokenDetails,
          },
          props: { default: true },
        },
        {
          name: RouteName.Key,
          path: '/key/:id',
          components: {
            default: KeyDetails,
          },
          props: { default: true },
        },
        {
          name: RouteName.ClientTlsCertificate,
          path: '/client-tls-certificate/:id/:hash',
          components: {
            default: ClientTlsCertificate,
          },
          props: { default: true },
          meta: { permission: Permissions.VIEW_CLIENT_INTERNAL_CERT_DETAILS },
        },
        {
          name: RouteName.ServiceClientAccessRights,
          path: '/subsystem/:id/serviceclients/:serviceClientId',
          props: { default: true },
          components: {
            default: ServiceClientAccessRights,
          },
        },
        {
          name: RouteName.LocalGroup,
          path: '/localgroup/:clientId/:groupId',
          components: {
            default: LocalGroup,
          },
          props: { default: true },
        },
        {
          name: RouteName.ServiceDescriptionDetails,
          path: '/service-description/:id',
          components: {
            default: ServiceDescriptionDetails,
          },
          props: { default: true },
        },
        {
          name: RouteName.Service,
          path: '/service',
          components: {
            default: Service,
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
            },
          ],
        },
        {
          name: RouteName.EndpointDetails,
          path: '/service/:clientId/:serviceId/endpoints/:id',
          components: {
            default: EndpointDetails,
          },
          props: { default: true },
        },
        {
          name: RouteName.EndpointAccessRights,
          path: '/service/:clientId/:serviceId/endpoints/:id/accessrights',
          components: {
            default: EndpointAccessRights,
          },
          props: { default: true },
        },
        {
          name: RouteName.GenerateCertificateSignRequest,
          path: '/generate-csr/:keyId',
          components: {
            default: GenerateCertificateSignRequest,
          },
          props: { default: true },
        },
        {
          name: RouteName.AddKey,
          path: '/add-key/:tokenId',
          components: {
            default: AddKey,
          },
          props: { default: true },
        },
        {
          name: RouteName.InternalTlsCertificate,
          path: '/internal-tls-certificate',
          components: {
            default: InternalCertificateDetails,
          },
          props: { default: true },
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
  ],
});

router.beforeEach((to: Route, from: Route, next: Next) => {
  // Going to login
  if (to.name === 'login') {
    next();
    return;
  }

  if (store.getters.isAuthenticated) {
    if (!to.meta.permission) {
      next();
    } else if (store.getters.hasPermission(to.meta.permission)) {
      // This route is allowed
      next();
    } else {
      // This route is not allowed
      next({
        name: store.getters.firstAllowedTab.to.name,
      });
    }
    return;
  } else {
    next({
      path: '/login',
    });
  }
});

sync(store, router);

export default router;
