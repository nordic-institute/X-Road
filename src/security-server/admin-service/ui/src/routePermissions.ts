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

import { Permissions, RouteName } from '@/global';

/* 
Route permission object needs the permissions and the name.
It could also use path for some routes that don't have name, but those routes are 
restricted by their child routes. So for now this simpler system works ok.
*/
export interface RoutePermission {
  permissions: string[];
  name: string;
}

// "single source of truth" for route permissions
export const routePermissions: RoutePermission[] = [
  {
    name: RouteName.SignAndAuthKeys,
    permissions: [Permissions.VIEW_KEYS],
  },
  {
    name: RouteName.ApiKey,
    permissions: [
      Permissions.VIEW_API_KEYS,
      Permissions.CREATE_API_KEY,
      Permissions.UPDATE_API_KEY,
      Permissions.REVOKE_API_KEY,
    ],
  },
  {
    name: RouteName.SSTlsCertificate,
    permissions: [Permissions.VIEW_INTERNAL_TLS_CERT],
  },
  {
    name: RouteName.CreateApiKey,
    permissions: [Permissions.CREATE_API_KEY],
  },
  {
    name: RouteName.GenerateInternalCSR,
    permissions: [Permissions.GENERATE_INTERNAL_TLS_CSR],
  },
  {
    name: RouteName.Diagnostics,
    permissions: [Permissions.DIAGNOSTICS],
  },
  {
    name: RouteName.SystemParameters,
    permissions: [Permissions.VIEW_SYS_PARAMS],
  },
  {
    name: RouteName.BackupAndRestore,
    permissions: [Permissions.BACKUP_CONFIGURATION],
  },
  {
    name: RouteName.AddSubsystem,
    permissions: [Permissions.ADD_CLIENT],
  },
  {
    name: RouteName.AddClient,
    permissions: [Permissions.ADD_CLIENT],
  },
  {
    name: RouteName.AddMember,
    permissions: [Permissions.ADD_CLIENT],
  },
  {
    name: RouteName.Subsystem,
    permissions: [Permissions.VIEW_CLIENT_DETAILS],
  },
  {
    name: RouteName.SubsystemDetails,
    permissions: [Permissions.VIEW_CLIENT_DETAILS],
  },
  {
    name: RouteName.SubsystemServiceClients,
    permissions: [Permissions.VIEW_CLIENT_ACL_SUBJECTS],
  },
  {
    name: RouteName.SubsystemServices,
    permissions: [Permissions.VIEW_CLIENT_SERVICES],
  },
  {
    name: RouteName.SubsystemServers,
    permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS],
  },
  {
    name: RouteName.SubsystemLocalGroups,
    permissions: [Permissions.VIEW_CLIENT_LOCAL_GROUPS],
  },

  {
    name: RouteName.Client,
    permissions: [Permissions.VIEW_CLIENT_DETAILS],
  },
  {
    name: RouteName.MemberDetails,
    permissions: [Permissions.VIEW_CLIENT_DETAILS],
  },
  {
    name: RouteName.MemberServers,
    permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS],
  },

  {
    name: RouteName.Clients,
    permissions: [Permissions.VIEW_CLIENTS],
  },

  {
    name: RouteName.ClientTlsCertificate,
    permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERT_DETAILS],
  },
];
