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
} from 'vue-router';
import routes from './routes';
import { RouteName } from '@/global';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';

// Create the router
const router = createRouter({
  history: createWebHashHistory(),
  routes: routes,
});

router.beforeEach(
  async (
    to: RouteLocationNormalized,
    from: RouteLocationNormalized,
    next: NavigationGuardNext,
  ) => {
    // Going to login
    if (to.name === RouteName.Login) {
      next();
      return;
    }

    // Pinia stores
    const user = useUser();
    const notifications = useNotifications();
    const system = useSystem();

    // User is allowed to access any other view than login only after authenticated information has been fetched
    // Session alive information is fetched before any view is accessed. This prevents UI flickering by not allowing
    // user to be redirected to a view that contains api calls (s)he is not allowed.
    if (user.isSessionAlive && user.isAuthenticated) {
      // Server is not initialized
      if (
        !system.isServerInitialized &&
        to.name != RouteName.Initialisation &&
        from.name != RouteName.Initialisation
      ) {
        next({
          name: RouteName.Initialisation,
        });
      } else {
        // Clear success, error and continue init notifications when the route changed, except when coming from Initialization.
        if (from.name !== RouteName.Initialisation) {
          notifications.resetNotifications();
        }
        /*
    Check permissions here
    */
        if (!to?.meta?.permissions) {
          to.meta.backTo = true;
          next();
        } else if (user.hasAnyOfPermissions(to.meta.permissions)) {
          // This route is allowed
          to.meta.backTo = from.matched.length > 0;
          next();
        } else {
          // This route is not allowed
          next({
            name: RouteName.Forbidden,
          });
        }
      }
    } else {
      next({
        name: RouteName.Login,
      });
    }
  },
);

export default router;
