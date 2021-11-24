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

import { createLocalVue } from '@vue/test-utils';
import Vuex, { Store } from 'vuex';
import { State, module as storeConfig } from '@/store/modules/user';
import { mainTabs, StoreTypes } from '@/global';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
import { cloneDeep } from 'lodash';

const testPermissions: string[] = [
  'EDIT_APPROVED_TSA',
  'VIEW_CENTRAL_SERVICE_DETAILS',
  'VIEW_INTERNAL_CONFIGURATION_SOURCE',
  'VIEW_TRUSTED_ANCHORS',
  'RESTORE_CONFIGURATION',
  'VIEW_MEMBER_CLASSES',
  'VIEW_EXTERNAL_CONFIGURATION_SOURCE',
  'DELETE_APPROVED_TSA',
  'REVOKE_API_KEY',
  'VIEW_API_KEYS',
  'EDIT_MANAGEMENT_SERVICES_ADDRESS',
  'CREATE_API_KEY',
  'REMOVE_CENTRAL_SERVICE',
  'INIT_CONFIG',
  'DEACTIVATE_TOKEN',
  'ADD_APPROVED_TSA',
  'DOWNLOAD_TRUSTED_ANCHOR',
  'VIEW_CONFIGURATION_MANAGEMENT',
  'VIEW_CENTRAL_SERVICES',
  'VIEW_VERSION',
  'DELETE_SECURITY_CATEGORY',
  'UPDATE_API_KEY',
  'DELETE_MEMBER_CLASS',
  'VIEW_GLOBAL_GROUPS',
  'VIEW_APPROVED_CA_DETAILS',
  'VIEW_SECURITY_SERVERS',
  'EDIT_MEMBER_CLASS',
  'DOWNLOAD_CONFIGURATION_PART',
  'ADD_CENTRAL_SERVICE',
  'VIEW_APPROVED_TSA_DETAILS',
  'EDIT_IMPLEMENTING_SERVICE',
  'VIEW_SECURITY_CATEGORIES',
  'DELETE_APPROVED_CA',
  'VIEW_CONFIGURATION',
  'EDIT_APPROVED_CA',
  'ADD_SECURITY_CATEGORY',
  'ADD_APPROVED_CA',
  'BACKUP_CONFIGURATION',
  'ACTIVATE_TOKEN',
  'ADD_MEMBER_CLASS',
  'VIEW_APPROVED_TSAS',
  'EDIT_SECURITY_CATEGORY',
  'VIEW_APPROVED_CAS',
  'VIEW_SECURITY_SERVER_DETAILS',
];
const memberPermissions: Array<string> = [
  'VIEW_MEMBERS',
  'VIEW_MEMBER_DETAILS',
  'SEARCH_MEMBERS',
];
let userStore: Store<State>;

describe('user store user.ts  -- setters & getters', () => {
  beforeEach(() => {
    const localVue = createLocalVue();
    localVue.use(Vuex);
    const renewedStoreConfig = cloneDeep(storeConfig);
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    userStore = new Vuex.Store<State>(renewedStoreConfig);
  });
  it('SET_PERMISSIONS fills permissions & bannedRoutes', () => {
    userStore.commit(StoreTypes.mutations.SET_PERMISSIONS, testPermissions);
    const storedState: State = userStore.state;
    expect(storedState.permissions).toHaveLength(testPermissions.length);
    expect(storedState.permissions).toEqual(testPermissions);
    expect(storedState.bannedRoutes?.length).toBeLessThan(
      storedState.permissions.length,
    );
    // member-details  needs "VIEW_MEMBER_DETAILS" - permission which is not contained in testPermissions
    expect(storedState.bannedRoutes).toContainEqual('member-details');
    expect(storedState.permissions).not.toContainEqual('VIEW_MEMBER_DETAILS');
  });
  it('GET_ALLOWED_TABS filters correctly', () => {
    userStore.commit(StoreTypes.mutations.SET_PERMISSIONS, memberPermissions);
    expect(mainTabs).not.toBeUndefined();
    const allowedTabs =
      userStore.getters[StoreTypes.getters.GET_ALLOWED_TABS](mainTabs);

    expect(allowedTabs).not.toBeUndefined();
    expect(allowedTabs.length).toBeLessThan(mainTabs.length);
    expect(allowedTabs.length).toEqual(1);
    expect(allowedTabs[0].name).toEqual('tab.main.members');
  });
  it('FIRST_ALLOWED_TAB returns right tab', () => {
    userStore.commit(StoreTypes.mutations.SET_PERMISSIONS, memberPermissions);
    const { FIRST_ALLOWED_TAB } = StoreTypes.getters;
    const firstTab = userStore.getters[FIRST_ALLOWED_TAB];

    // const firstTab = initializedFirstAllowedTabFunction(userState, getters)();
    expect(firstTab).not.toBeNull();
    expect(firstTab.name).toEqual('tab.main.members');
  });
});
