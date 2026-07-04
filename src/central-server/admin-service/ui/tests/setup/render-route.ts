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
import { defineComponent, h, nextTick, type Plugin } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { createPersistedState } from 'pinia-plugin-persistedstate';
import { createRouter, createWebHashHistory, RouterView, type Router } from 'vue-router';
import { render } from 'vitest-browser-vue';
import { createValidators } from '@niis/shared-ui/src/plugins/vee-validate';
import { i18n as sharedI18n } from '@niis/shared-ui/src/plugins/i18n';
import { useAppState, XrdApp, setupAddErrorNavigation, createXrdRouter } from '@niis/shared-ui';
import axios from 'axios';
import type { RequestHandler } from 'msw';
import { worker } from './browser-setup';
import { ensureMessages } from './i18n-messages';

import routes from '@/router/routes';
import { Permissions, RouteName } from '@/global';
import { createFilters } from '@/filters';
import vuetify from '@/plugins/vuetify';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';
import { TokenInitStatus } from '@/openapi-types';

export interface RenderRouteOptions {
  msw?: RequestHandler[];
  permissions?: string[];
  /**
   * Session state to boot into.
   * - `'alive'` (default): authenticated, session active.
   * - `'expired'`: session marked dead; XrdApp renders the logout/session-expired dialog.
   */
  session?: 'alive' | 'expired';
  /**
   * When true, installs the real createXrdRouter beforeEach guard and boots with the
   * user unauthenticated / session dead — enabling guard-redirect specs.
   * Normal authenticated navigation specs must NOT set this flag.
   */
  bootstrap?: boolean;
  /**
   * Override the system status patched into the system store before the route mounts.
   * By default the store is patched with an INITIALIZED state (instance_identifier 'CS',
   * central_server_address 'cs.example.org', software_token_init_status INITIALIZED).
   * Pass a partial state object to override — e.g. for init-page specs that need a
   * NOT_INITIALIZED state so form fields are editable.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  systemStatus?: Record<string, any>;
}

const DEFAULT_PERMISSIONS: string[] = [
  Permissions.VIEW_MEMBERS,
  Permissions.VIEW_MEMBER_DETAILS,
  Permissions.VIEW_SECURITY_SERVERS,
  Permissions.VIEW_SECURITY_SERVER_DETAILS,
  Permissions.VIEW_MANAGEMENT_REQUESTS,
  Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS,
  Permissions.VIEW_GLOBAL_GROUPS,
];

export async function renderRoute(
  path: string,
  options: RenderRouteOptions = {},
): Promise<{ router: Router }> {
  const { permissions = DEFAULT_PERMISSIONS, session = 'alive', bootstrap = false, systemStatus } = options;

  await ensureMessages();

  if (options.msw && options.msw.length > 0) {
    worker.use(...options.msw);
  }

  axios.defaults.baseURL = '/api/v1';

  const pinia = createPinia();
  pinia.use(createPersistedState({ storage: sessionStorage }));
  setActivePinia(pinia);

  const appState = useAppState();
  const userStore = useUser();
  const systemStore = useSystem();

  if (bootstrap) {
    appState.setSessionAlive(false);
    userStore.$patch({ authenticated: false });
  } else {
    appState.setSessionAlive(session === 'alive');
    userStore.$patch({ authenticated: true });
    userStore.setPermissions(permissions);
    systemStore.$patch(
      systemStatus ?? {
        systemStatus: {
          initialization_status: {
            instance_identifier: 'CS',
            central_server_address: 'cs.example.org',
            software_token_init_status: TokenInitStatus.INITIALIZED,
          },
          high_availability_status: {
            is_ha_configured: false,
            node_name: undefined,
          },
        },
      },
    );
  }

  let router: Router;
  if (bootstrap) {
    router = createXrdRouter({
      forbiddenRouteName: RouteName.Forbidden,
      initialisationRouteName: RouteName.Initialisation,
      loginRouteName: RouteName.Login,
      hasAnyOfPermissions: (perms: string[]) => userStore.hasAnyOfPermissions(perms),
      isAuthenticated: () => userStore.isAuthenticated,
      isServerInitialized: () => systemStore.isServerInitialized,
      routes,
    });
  } else {
    router = createRouter({
      history: createWebHashHistory(),
      routes,
    });
  }

  setupAddErrorNavigation(router, {
    404: { name: RouteName.NotFound },
  });

  const onLogout = () => {
    useUser().logout(false);
    nextTick(() => router.replace({ name: RouteName.Login }));
  };

  const AppShell = defineComponent({
    render: () =>
      h(
        XrdApp,
        { loginView: false, onLogout },
        { default: () => h(RouterView) },
      ),
  });

  render(AppShell, {
    global: {
      plugins: [
        pinia,
        router,
        vuetify,
        createFilters() as unknown as Plugin,
        sharedI18n as unknown as Plugin,
        createValidators() as unknown as Plugin,
      ],
    },
  });

  await router.push(path);
  await router.isReady();

  return { router };
}
