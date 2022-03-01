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
import Router, { NavigationGuardNext, Route, RouteConfig } from 'vue-router';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import InitialConfiguration from '@/views/InitialConfiguration/InitialConfiguration.vue';
import TabsBaseEmpty from '@/components/layout/TabsBaseEmpty.vue';
import { Permissions, RouteName } from '@/global';
import routes from '@/routes';
import i18n from './i18n';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';

// Route for initialisation view. This is created separeately because it's linked to vuex store and this causes the unit tests to break.
const initRoute: RouteConfig = {
  name: RouteName.InitialConfiguration,
  path: '/initial-configuration',
  components: {
    default: InitialConfiguration,
    alerts: AlertsContainer,
    top: TabsBaseEmpty,
  },
  beforeEnter: (to: Route, from: Route, next: NavigationGuardNext): void => {
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
};

// Create the router
const router = new Router({
  routes: routes,
});

// Add the security server initialisation route
router.addRoute(RouteName.BaseRoute, initRoute);

router.beforeEach((to: Route, from: Route, next: NavigationGuardNext) => {
  // Going to login
  if (to.name === RouteName.Login) {
    next();
    return;
  }

  const notifications = useNotifications();
  const user = useUser();

  // Clear error notifications when route is changed
  notifications.clearErrorNotifications();

  // User is allowed to access any other view than login only after authenticated information has been fetched
  // Session alive information is fetched before any view is accessed. This prevents UI flickering by not allowing
  // user to be redirected to a view that contains api calls (s)he is not allowed.
  if (user.sessionAlive && user.authenticated) {
    // Server is not initialized
    if (user.needsInitialization) {
      if (to.name !== RouteName.InitialConfiguration) {
        // Redirect to init
        next({
          name: RouteName.InitialConfiguration,
        });
        return;
      }
    }

    if (!to?.meta?.permissions) {
      next();
    } else if (user.hasAnyOfPermissions(to.meta.permissions)) {
      // This route is allowed
      next();
    } else {
      // This route is not allowed
      next({
        name: RouteName.Forbidden,
      });
    }
    return;
  } else {
    next({
      name: RouteName.Login,
    });
  }
});

export default router;
