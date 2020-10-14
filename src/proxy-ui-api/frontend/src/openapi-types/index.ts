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
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export { ApiError } from './core/ApiError';
export { OpenAPI } from './core/OpenAPI';

export type { AccessRight } from './models/AccessRight';
export type { AccessRights } from './models/AccessRights';
export type { Anchor } from './models/Anchor';
export type { Backup } from './models/Backup';
export type { BackupArchive } from './models/BackupArchive';
export type { CertificateAuthority } from './models/CertificateAuthority';
export { CertificateAuthorityOcspResponse } from './models/CertificateAuthorityOcspResponse';
export type { CertificateDetails } from './models/CertificateDetails';
export { CertificateOcspStatus } from './models/CertificateOcspStatus';
export { CertificateStatus } from './models/CertificateStatus';
export type { Client } from './models/Client';
export type { ClientAdd } from './models/ClientAdd';
export { ClientStatus } from './models/ClientStatus';
export type { CodeWithDetails } from './models/CodeWithDetails';
export { ConfigurationStatus } from './models/ConfigurationStatus';
export { ConnectionType } from './models/ConnectionType';
export type { ConnectionTypeWrapper } from './models/ConnectionTypeWrapper';
export { CsrFormat } from './models/CsrFormat';
export type { CsrGenerate } from './models/CsrGenerate';
export type { CsrSubjectFieldDescription } from './models/CsrSubjectFieldDescription';
export { DiagnosticStatusClass } from './models/DiagnosticStatusClass';
export type { DistinguishedName } from './models/DistinguishedName';
export { Endpoint } from './models/Endpoint';
export { EndpointUpdate } from './models/EndpointUpdate';
export type { ErrorInfo } from './models/ErrorInfo';
export type { GlobalConfDiagnostics } from './models/GlobalConfDiagnostics';
export type { GlobalConfiguration } from './models/GlobalConfiguration';
export type { GroupMember } from './models/GroupMember';
export type { IgnoreWarnings } from './models/IgnoreWarnings';
export type { InitializationStatus } from './models/InitializationStatus';
export type { InitialServerConf } from './models/InitialServerConf';
export type { Key } from './models/Key';
export type { KeyLabel } from './models/KeyLabel';
export type { KeyLabelWithCsrGenerate } from './models/KeyLabelWithCsrGenerate';
export type { KeyName } from './models/KeyName';
export { KeyUsage } from './models/KeyUsage';
export { KeyUsageType } from './models/KeyUsageType';
export type { KeyValuePair } from './models/KeyValuePair';
export type { KeyWithCertificateSigningRequestId } from './models/KeyWithCertificateSigningRequestId';
export type { Language } from './models/Language';
export type { LocalGroup } from './models/LocalGroup';
export type { LocalGroupAdd } from './models/LocalGroupAdd';
export type { LocalGroupDescription } from './models/LocalGroupDescription';
export type { MemberName } from './models/MemberName';
export type { Members } from './models/Members';
export type { OcspResponder } from './models/OcspResponder';
export type { OcspResponderDiagnostics } from './models/OcspResponderDiagnostics';
export { OcspStatus } from './models/OcspStatus';
export type { OrphanInformation } from './models/OrphanInformation';
export { PossibleAction } from './models/PossibleAction';
export type { PossibleActions } from './models/PossibleActions';
export type { SecurityServer } from './models/SecurityServer';
export type { SecurityServerAddress } from './models/SecurityServerAddress';
export type { Service } from './models/Service';
export type { ServiceClient } from './models/ServiceClient';
export type { ServiceClients } from './models/ServiceClients';
export { ServiceClientType } from './models/ServiceClientType';
export type { ServiceDescription } from './models/ServiceDescription';
export type { ServiceDescriptionAdd } from './models/ServiceDescriptionAdd';
export type { ServiceDescriptionDisabledNotice } from './models/ServiceDescriptionDisabledNotice';
export type { ServiceDescriptionUpdate } from './models/ServiceDescriptionUpdate';
export { ServiceType } from './models/ServiceType';
export type { ServiceUpdate } from './models/ServiceUpdate';
export type { TimestampingService } from './models/TimestampingService';
export type { TimestampingServiceDiagnostics } from './models/TimestampingServiceDiagnostics';
export { TimestampingStatus } from './models/TimestampingStatus';
export type { Token } from './models/Token';
export type { TokenCertificate } from './models/TokenCertificate';
export type { TokenCertificateSigningRequest } from './models/TokenCertificateSigningRequest';
export { TokenInitStatus } from './models/TokenInitStatus';
export type { TokenName } from './models/TokenName';
export type { TokenPassword } from './models/TokenPassword';
export type { TokensLoggedOut } from './models/TokensLoggedOut';
export { TokenStatus } from './models/TokenStatus';
export { TokenType } from './models/TokenType';
export type { User } from './models/User';
export type { Version } from './models/Version';

export { BackupsService } from './services/BackupsService';
export { CertificateAuthoritiesService } from './services/CertificateAuthoritiesService';
export { ClientsService } from './services/ClientsService';
export { DiagnosticsService } from './services/DiagnosticsService';
export { EndpointsService } from './services/EndpointsService';
export { InitializationService } from './services/InitializationService';
export { KeysService } from './services/KeysService';
export { LanguageService } from './services/LanguageService';
export { LocalGroupsService } from './services/LocalGroupsService';
export { MemberClassesService } from './services/MemberClassesService';
export { MemberNamesService } from './services/MemberNamesService';
export { SecurityServersService } from './services/SecurityServersService';
export { ServiceDescriptionsService } from './services/ServiceDescriptionsService';
export { ServicesService } from './services/ServicesService';
export { SystemService } from './services/SystemService';
export { TimestampingServicesService } from './services/TimestampingServicesService';
export { TokenCertificatesService } from './services/TokenCertificatesService';
export { TokensService } from './services/TokensService';
export { UserService } from './services/UserService';
export { XroadInstancesService } from './services/XroadInstancesService';
