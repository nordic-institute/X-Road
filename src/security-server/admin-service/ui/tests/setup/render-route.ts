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
import { defineComponent, h, type Plugin } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { createPersistedState } from 'pinia-plugin-persistedstate';
import { createRouter, createWebHashHistory, RouterView, type Router } from 'vue-router';
import { render } from 'vitest-browser-vue';
import { createValidators } from '@niis/shared-ui/src/plugins/vee-validate';
import { i18n as sharedI18n } from '@niis/shared-ui/src/plugins/i18n';
import { useAppState, XrdApp, setupAddErrorNavigation } from '@niis/shared-ui';
import axios from 'axios';
import type { RequestHandler } from 'msw';
import deepmerge from 'deepmerge';
import { worker } from './browser-setup';

import routes from '@/router/routes';
import { Permissions, RouteName } from '@/global';
import { createFilters } from '@/filters';
import vuetify from '@/plugins/vuetify';
import { useUser } from '@/store/modules/user';
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
}

const DEFAULT_PERMISSIONS: string[] = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_SERVICES,
  Permissions.VIEW_CLIENT_ACL_SUBJECTS,
  Permissions.VIEW_CLIENT_INTERNAL_CERTS,
  Permissions.VIEW_CLIENT_LOCAL_GROUPS,
  Permissions.ADD_OPENAPI3,
  Permissions.ENABLE_DISABLE_WSDL,
];

async function loadMessages(): Promise<Record<string, unknown>> {
  const [sharedUiEn, ssEn, veeValidateEn] = await Promise.all([
    import('@niis/shared-ui/src/locales/en.json'),
    import('@/locales/en.json'),
    import('@vee-validate/i18n/dist/locale/en.json'),
  ]);
  return deepmerge.all([
    sharedUiEn.default as Record<string, unknown>,
    ssEn.default as Record<string, unknown>,
    { validation: veeValidateEn.default },
  ]) as Record<string, unknown>;
}

let messagesPromise: Promise<void> | null = null;

function ensureMessages(): Promise<void> {
  if (messagesPromise) return messagesPromise;
  messagesPromise = loadMessages().then((merged) => {
    sharedI18n.global.setLocaleMessage('en', merged);
  });
  return messagesPromise;
}

/*
 * Deliberately stubbed bootstrap (not covered by this tier):
 *   - Session/auth state: userStore.$patch replaces the real session-bootstrap fetch. Auth
 *     behaviour is mocked by design; the real fetch flow is an e2e concern.
 *   - Hash history: createWebHashHistory() avoids base-URL and reverse-proxy config. Web-
 *     history / base-URL routing is a deploy-smoke concern.
 *   - Vite dev build: specs run under Vite's dev transform, not a minified production bundle.
 *     Bundle/CORS testing is a deploy-smoke concern.
 *   - No client-side CSRF: the app uses cookie-session only (no axios interceptors, no
 *     token-attach). CSRF testing is out of scope for this tier.
 */
export async function renderRoute(
  path: string,
  options: RenderRouteOptions = {},
): Promise<{ router: Router }> {
  const { permissions = DEFAULT_PERMISSIONS, session = 'alive' } = options;

  await ensureMessages();

  if (options.msw && options.msw.length > 0) {
    worker.use(...options.msw);
  }

  axios.defaults.baseURL = '/api/v1';

  const pinia = createPinia();
  pinia.use(createPersistedState({ storage: sessionStorage }));
  setActivePinia(pinia);

  const appState = useAppState();
  appState.setSessionAlive(session === 'alive');

  const userStore = useUser();
  userStore.$patch({
    authenticated: true,
    initializationStatus: {
      is_anchor_imported: true,
      is_server_code_initialized: true,
      is_server_owner_initialized: true,
      software_token_init_status: TokenInitStatus.INITIALIZED,
      enforce_token_pin_policy: false,
    },
  });
  userStore.setPermissions(permissions);

  const router = createRouter({
    history: createWebHashHistory(),
    routes,
  });

  setupAddErrorNavigation(router, {
    404: { name: RouteName.NotFound },
  });

  const AppShell = defineComponent({
    render: () =>
      h(
        XrdApp,
        { loginView: false, initialUserView: false, onLogout: () => {} },
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
