import Vue from 'vue';
import Router from 'vue-router';
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
          component: Keys,
          meta: { permission: Permissions.VIEW_KEYS },
        },
        {
          name: RouteName.Diagnostics,
          path: '/diagnostics',
          component: Diagnostics,
          meta: { permission: Permissions.DIAGNOSTICS },
        },
        {
          name: RouteName.Settings,
          path: '/settings',
          component: Settings,
        },
        {
          name: RouteName.AddSubsystem,
          path: '/add-subsystem',
          component: AddSubsystem,
        },
        {
          name: RouteName.AddClient,
          path: '/add-client',
          component: AddClient,
        },
        {
          name: RouteName.Subsystem,
          path: '/subsystem',
          component: Subsystem,
        },
        {
          name: RouteName.Client,
          path: '/client',
          component: Client,
        },
        {
          name: RouteName.Clients,
          path: '',
          component: Clients,
          meta: { permission: Permissions.VIEW_CLIENTS },
        },
      ],
    },
    {
      path: '/login',
      name: RouteName.Login,
      component: Login,
    },
  ],
});

router.beforeEach((to, from, next) => {

  // Going to login
  if (to.name === 'login') {
    next();
    return;
  }

  if (store.getters.isAuthenticated) {

    const record = to.matched.find((route) => route.meta.permission);
    if (record) {
      if (store.getters.permissions.includes(record.meta.permission)) {
        // Route is allowed
        next();
        return;
      } else {
        // This route is not allowed
        next({
          name: store.getters.firstAllowedTab.to.name,
        });
        return;
      }
    }

    next();

  } else {
    next({
      path: '/login',
    });
  }
});

sync(store, router);

export default router;
