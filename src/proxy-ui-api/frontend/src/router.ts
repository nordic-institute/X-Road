import Vue from 'vue';
import Router from 'vue-router';
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
import { sync } from 'vuex-router-sync';
import store from './store';


Vue.use(Router);

const router = new Router({
  routes: [
    {
      path: '/',
      component: Base,
      children: [
        {
          name: 'keys',
          path: '/keys',
          component: Keys,
        },
        {
          name: 'diagnostics',
          path: '/diagnostics',
          component: Diagnostics,
        },
        {
          name: 'settings',
          path: '/settings',
          component: Settings,
        },
        {
          name: 'add-subsystem',
          path: '/add-subsystem',
          component: AddSubsystem,
        },
        {
          name: 'add-client',
          path: '/add-client',
          component: AddClient,
        },
        {
          name: 'subsystem',
          path: '/subsystem',
          component: Subsystem,
        },
        {
          name: 'client',
          path: '/client',
          component: Client,
        },
        {
          name: 'clients',
          path: '',
          component: Clients,
        },
      ]
    },
    {
      path: '/login',
      name: 'login',
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
    next();
  } else {
    next({
      path: '/login',
    });
  }
});

sync(store, router);

export default router;
