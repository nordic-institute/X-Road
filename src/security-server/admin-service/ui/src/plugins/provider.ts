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

import { Plugin } from 'vue';
import { useUser } from '@/store/modules/user';
import { routingKey, systemKey, userKey } from '@niis/shared-ui';
import { RouteName } from '@/global';
import { useRouter } from 'vue-router';
import { useSystem } from '@/store/modules/system';

export default {
  install(app) {
    app.runWithContext(() => {

      const user = useUser();
      const system = useSystem();
      const router = useRouter();

      app.provide(routingKey, {
        toLogin() {
          return router.replace({ name: RouteName.Login });
        },
        toHome() {
          return router.replace(user.firstAllowedTab.to);
        },
        goBack(steps: number) {
          return router.go(steps);
        },
      });

      app.provide(userKey, {
        login(username: string, password: string) {
          return user.loginUser({ username, password });
        },
        logout() {
          return user.logoutUser();
        },
        username() {
          return user.username;
        },
        isSessionAlive(): boolean {
          return user.sessionAlive;
        },
      });

      app.provide(systemKey, {
        version() {
          return system.securityServerVersion.info;
        },
      });
    });
  },
} as Plugin;
