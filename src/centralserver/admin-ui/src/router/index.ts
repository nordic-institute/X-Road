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
import Router, { NavigationGuardNext, Route } from 'vue-router';
import { sync } from 'vuex-router-sync';
import store from '@/store';
import routes from './routes';
import { RouteName, StoreTypes } from '@/global';

// Create the router
const router = new Router({
  routes: routes,
});

router.beforeEach((to: Route, from: Route, next: NavigationGuardNext) => {
  // Going to login
  if (to.name === RouteName.Login) {
    next();
    return;
  }

  // Clear error notifications when the route is changed
  store.commit(StoreTypes.mutations.RESET_NOTIFICATIONS_STATE);

  // User is allowed to access any other view than login only after authenticated information has been fetched
  // Session alive information is fetched before any view is accessed. This prevents UI flickering by not allowing
  // user to be redirected to a view that contains api calls (s)he is not allowed.
  if (
    store.getters[StoreTypes.getters.IS_SESSION_ALIVE] &&
    store.getters[StoreTypes.getters.IS_AUTHENTICATED]
  ) {
    // Server is not initialized
    /*
    Check initialisation status here
    */

    next();
    /*
    Check permissions here 
    */
    return;
  } else {
    next({
      name: RouteName.Login,
    });
  }
});

sync(store, router);

export default router;
