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
import {
  createRouter,
  createWebHashHistory,
  NavigationGuardNext,
  RouteLocationNormalized,
  RouteLocationNormalizedLoaded,
  Router,
} from 'vue-router';

import { useNotifications } from '../composables';
import { useHistory } from '../stores';
import { XrdLocation, XrdRoute } from '../types';

interface Config {
  loginRouteName: string;
  initialisationRouteName: string;
  forbiddenRouteName: string;
  isAuthenticated: () => boolean;
  isSessionAlive: () => boolean;
  isServerInitialized: () => boolean;
  hasAnyOfPermissions: (permissions: string[]) => boolean;
  routes: XrdRoute[];
}

export function createXrdRouter(config: Config): Router {
  // Create the router
  const router = createRouter({
    history: createWebHashHistory(),
    routes: config.routes,
  });

  router.afterEach((to: RouteLocationNormalized, from: RouteLocationNormalizedLoaded) => {
    const { push } = useHistory();

    push(to);
  });

  router.beforeEach(async (to: XrdLocation, from: RouteLocationNormalized, next: NavigationGuardNext) => {
    // Going to login
    if (to.name === config.loginRouteName) {
      next();
      return;
    }

    // Pinia stores
    const notifications = useNotifications();

    // User is allowed to access any other view than login only after authenticated information has been fetched
    // Session alive information is fetched before any view is accessed. This prevents UI flickering by not allowing
    // user to be redirected to a view that contains api calls (s)he is not allowed.
    if (config.isSessionAlive() && config.isAuthenticated()) {
      // Server is not initialized
      if (!config.isServerInitialized() && to.name != config.initialisationRouteName && from.name != config.initialisationRouteName) {
        next({
          name: config.initialisationRouteName,
        });
      } else {
        // Clear success, error and continue init notifications when the route changed, except when coming from Initialization.
        if (from.name !== config.initialisationRouteName) {
          notifications.clear();
        }
        /*
      Check permissions here
      */

        if (!to?.meta?.permissions) {
          next();
        } else if (config.hasAnyOfPermissions(to.meta.permissions)) {
          // This route is allowed
          next();
        } else {
          // This route is not allowed
          next({
            name: config.forbiddenRouteName,
          });
        }
      }
    } else {
      next({
        name: config.loginRouteName,
      });
    }
  });

  return router;
}
