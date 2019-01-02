import Vue from 'vue';
import Router from 'vue-router';
import Login from './views/Login.vue';
import store from './store';

Vue.use(Router);

const router = new Router({
  routes: [
    {
      path: '/',
      name: 'login',
      component: Login,
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (about.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import(/* webpackChunkName: "about" */ './views/About.vue'),
    },
  ],
});

router.beforeEach((to, from, next) => {

  if (to.name === 'login') {
    next();
    return;
  }

  if (!store.getters.isAuthenticated) {
    next({
      path: '/',
    });
  } else {
    next();
  }
});

export default router;
