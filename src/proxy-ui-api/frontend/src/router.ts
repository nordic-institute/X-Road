import Vue from 'vue';
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
import Certificate from '@/views/Certificate.vue';
import Error from '@/views/Error.vue';
import ClientDetails from '@/components/ClientDetails.vue';
import InternalServers from '@/components/InternalServers.vue';
import LocalGroups from '@/components/LocalGroups.vue';
import Services from '@/components/Services.vue';
import ServiceClients from '@/components/ServiceClients.vue';
import store from './store';
import { RouteName, Permissions } from '@/global';

Vue.use(Router);

const router = new Router({
  routes: [
    {
      path: '/',
      component: Base,
      children: [
        {
          name: RouteName.Keys,
          path: '/keys',
          components: {
            default: Keys,
            top: TabsBase,
          },

          meta: { permission: Permissions.VIEW_KEYS },
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
              path: '/subsystem/clients/:id',
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
              path: '/subsystem/groups/:id',
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
          meta: { permission: Permissions.VIEW_CLIENT_INTERNAL_CERT_DETAILS },
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
