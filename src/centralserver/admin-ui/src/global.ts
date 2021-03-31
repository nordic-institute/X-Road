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
import { Tab } from '@/ui-types';

// Vuex Store root state
export interface RootState {
  version: string;
}

// Vuex store types
export const StoreTypes = {
  getters: {
    // User
    IS_AUTHENTICATED: 'IS_AUTHENTICATED',
    IS_SESSION_ALIVE: 'IS_SESSION_ALIVE',
    USERNAME: 'USERNAME',
    // Notifications
    SUCCESS_NOTIFICATIONS: 'SUCCESS_NOTIFICATIONS',
    ERROR_NOTIFICATIONS: 'ERROR_NOTIFICATIONS',
  },
  mutations: {
    // User
    SET_SESSION_ALIVE: 'SET_SESSION_ALIVE',
    SET_USERNAME: 'SET_USERNAME',
    CLEAR_AUTH_DATA: 'CLEAR_AUTH_DATA',
    AUTH_USER: 'AUTH_USER',
    // Notifications
    RESET_NOTIFICATIONS_STATE: 'RESET_NOTIFICATIONS_STATE',
    SET_SUCCESS_CODE: 'SET_SUCCESS_CODE',
    SET_SUCCESS_RAW: 'SET_SUCCESS_RAW',
    SET_ERROR_MESSAGE_CODE: 'SET_ERROR_MESSAGE_CODE',
    SET_ERROR_MESSAGE_RAW: 'SET_ERROR_MESSAGE_RAW',
    SET_ERROR_OBJECT: 'SET_ERROR_OBJECT',
    DELETE_SUCCESS_NOTIFICATION: 'DELETE_SUCCESS_NOTIFICATION',
    DELETE_NOTIFICATION: 'DELETE_NOTIFICATION',
  },
  actions: {
    // User
    LOGIN: 'LOGIN',
    LOGOUT: 'LOGOUT',
    CLEAR_AUTH: 'CLEAR_AUTH',
    FETCH_SESSION_STATUS: 'FETCH_SESSION_STATUS',
    FETCH_USER_DATA: 'FETCH_USER_DATA',
    // Notifications
    RESET_NOTIFICATIONS_STATE: 'RESET_NOTIFICATIONS_STATE',
    SHOW_ERROR: 'SHOW_ERROR',
    SHOW_SUCCESS: 'SHOW_SUCCESS',
    SHOW_SUCCESS_RAW: 'SHOW_SUCCESS_RAW',
    SHOW_ERROR_MESSAGE_CODE: 'SHOW_ERROR_MESSAGE_CODE',
    SHOW_ERROR_MESSAGE_RAW: 'SHOW_ERROR_MESSAGE_RAW',
  },
};

// A "single source of truth" for route names
export enum RouteName {
  BaseRoute = 'base',
  Members = 'members',
  MemberDetails = 'member-details',
  MemberManagementRequests = 'member-management-requests',
  MemberSubsystems = 'member-subsystems',
  SecurityServers = 'security-servers',
  ManagementRequests = 'management-requests',
  TrustServices = 'trust-services',
  GlobalConfiguration = 'global-configuration',
  Settings = 'settings',
  GlobalResources = 'global-resources',
  SystemSettings = 'system-settings',
  BackupAndRestore = 'backup-and-restore',
  InternalConfiguration = 'internal-configuration',
  ExternalConfiguration = 'external-configuration',
  TrustedAnchors = 'trusted-anchors',
  Login = 'login',
}

// A "single source of truth" for permission strings
export enum Permissions {
  MOCK_PERMISSION1 = 'MOCK_PERMISSION1',
  MOCK_PERMISSION2 = 'MOCK_PERMISSION2', // mock
}

export const mainTabs: Tab[] = [
  {
    to: { name: RouteName.Members },
    key: 'members',
    name: 'tab.main.members',
  },
  {
    to: { name: RouteName.SecurityServers },
    key: 'keys',
    name: 'tab.main.securityServers',
  },
  {
    to: { name: RouteName.ManagementRequests },
    key: 'managementRequests',
    name: 'tab.main.managementRequests',
  },
  {
    to: { name: RouteName.TrustServices },
    key: 'trustServices',
    name: 'tab.main.trustServices',
  },
  {
    to: { name: RouteName.GlobalConfiguration },
    key: 'globalConfiguration',
    name: 'tab.main.globalConfiguration',
  },
  {
    to: { name: RouteName.SystemSettings }, // name of the firsts child of settings
    key: 'settings',
    name: 'tab.main.settings',
    permissions: [Permissions.MOCK_PERMISSION1, Permissions.MOCK_PERMISSION2],
  },
];

// Version 7.0 colors as enum.
export enum Colors {
  Purple10 = '#efebfb',
  Purple20 = '#e0d8f8',
  Purple30 = '#d1c4f4',
  Purple70 = '#9376e6',
  Purple100 = '#663cdc',
  Black10 = '#e8e8e8',
  Black30 = '#bcbbbb',
  Black50 = '#908e8e',
  Black70 = '#636161',
  Black100 = '#211e1e',
  White100 = '#ffffff',
  WarmGrey10 = '#f4f3f6',
  WarmGrey20 = '#eae8ee',
  WarmGrey30 = '#dedce4',
  WarmGrey50 = '#c9c6d3',
  WarmGrey70 = '#b4afc2',
  WarmGrey100 = '#575169',
  Error = '#ec4040',
  Success100 = '#0cc177',
  Success10 = '#e6f8f1',
  Background = '#e5e5e5',
}
