import Router, { Route } from 'vue-router';
import { sync } from 'vuex-router-sync';
import Login from './views/Login.vue';
import Base from './views/Base.vue';
import Clients from './views/Clients.vue';
import Keys from './views/Keys.vue';
import Settings from './views/Settings.vue';
import Diagnostics from './views/Diagnostics.vue';
import AddSubsystem from './views/AddSubsystem.vue';
import AddClient from './views/AddClient.vue';
import Subsystem from './views/Subsystem.vue';
import Client from './views/Client.vue';
import TabsBase from '@/views/TabsBase.vue';
import ClientTlsCertificate from '@/views/ClientTlsCertificate.vue';
import Error from '@/views/Error.vue';
import ClientDetails from '@/components/ClientDetails.vue';
import InternalServers from '@/components/InternalServers.vue';
import LocalGroups from '@/components/LocalGroups.vue';
import LocalGroup from '@/views/LocalGroup.vue';
import Services from '@/components/Services.vue';
import ServiceClients from '@/components/ServiceClients.vue';
import ServiceDescriptionDetails from '@/views/ServiceDescriptionDetails.vue';
import SignAndAuthKeys from '@/components/SignAndAuthKeys.vue';
import SSTlsCertificate from '@/components/SSTlsCertificate.vue';
import ApiKey from '@/components/ApiKey.vue';
import SystemParameters from '@/components/SystemParameters.vue';
import BackupAndRestore from '@/components/BackupAndRestore.vue';
import Token from '@/views/Token.vue';
import Key from '@/views/Key.vue';
import Certificate from '@/views/Certificate.vue';
import Service from '@/views/Service.vue';
import store from './store';
import { RouteName, Permissions } from '@/global';


const router = new Router({
  routes: [
    {
      path: '/',
      component: Base,
      children: [
        {
          path: '/keys',
          components: {
            default: Keys,
            top: TabsBase,
          },
          meta: { permission: Permissions.VIEW_KEYS },
          children: [
            {
              name: RouteName.SignAndAuthKeys,
              path: '',
              component: SignAndAuthKeys,
              props: true,
              meta: { permission: Permissions.VIEW_CLIENT_DETAILS },
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
          name: RouteName.Diagnostics,
          path: '/diagnostics',
          components: {
            default: Diagnostics,
            top: TabsBase,
          },
          meta: { permission: Permissions.DIAGNOSTICS },
        },
        {
          name: RouteName.Settings,
          path: '/settings',
          components: {
            default: Settings,
            top: TabsBase,
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
          path: '/add-subsystem',
          components: {
            default: AddSubsystem,
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
          path: '/certificate/:id/:hash',

          components: {
            default: Certificate,
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
          name: RouteName.LocalGroup,
          path: '/localgroup/:clientId/:groupId',
          components: {
            default: LocalGroup,
          },
          props: { default: true },
        },
        {
          name: RouteName.ServiceDescriptionDetails,
          path: '/services/details/:id',
          components: {
            default: ServiceDescriptionDetails,
          },
          props: { default: true },
        },
        {
          name: RouteName.Service,
          path: '/service/:serviceId',
          components: {
            default: Service,
          },
          props: { default: true },
        },
      ],
    },
    {
      path: '/login',
      name: RouteName.Login,
      component: Login,
    },
    {
      path: '*',
      component: Error,
    },
  ],
});

router.beforeEach((to: Route, from: Route, next) => {

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
