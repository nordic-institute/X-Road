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
package ee.ria.xroad.common;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import java.util.Arrays;
import java.util.Optional;

/**
 * Contains system-wide constants for system properties.
 */
public final class SystemProperties {

    private SystemProperties() {
    }

    /** The prefix for all properties. */
    public static final String PREFIX = "xroad.";
    private static final String SIGNER_PREFIX = PREFIX + "signer.";
    private static final String CENTER_PREFIX = PREFIX + "center.";

    private static final String COMMA_SPLIT = "\\s*,\\s*";

    // Common -----------------------------------------------------------------

    /** Property name of the temporary files path. */
    public static final String TEMP_FILES_PATH =
            PREFIX + "common.temp-files-path";

    /** Property name of the downloaded global configuration directory. */
    public static final String CONFIGURATION_PATH =
            PREFIX + "common.configuration-path";

    /** Current version number of the global configuration **/
    public static final int CURRENT_GLOBAL_CONFIGURATION_VERSION = 5;

    /** Minimum supported version number of the global configuration **/
    public static final int MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION = 2;

    /** Default minimum supported global conf version on central server */
    public static final String DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /** Default minimum supported global conf version on configuration proxy */
    public static final String DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /** Minimum supported global conf version on central server **/
    private static final String MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION =
            CENTER_PREFIX + "minimum-global-configuration-version";

    /** Minimum supported global conf version on configuration proxy **/
    private static final String MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION =
            PREFIX + "configuration-proxy.minimum-global-configuration-version";

    // REST API ---------------------------------------------------------------

    /** Property name of the Proxy UI API's ssl configuration file. */
    public static final String PROXY_UI_API_SSL_PROPERTIES =
            PREFIX + "proxy-ui-api.ssl-properties";

    /** Default whitelist for Proxy UI API's key management API (allow only localhost access, ipv4 and ipv6) */
    public static final String DEFAULT_KEY_MANAGEMENT_API_WHITELIST = "127.0.0.0/8, ::1";

    /** Default whitelist for Proxy UI API's regular APIs (allow all) */
    public static final String DEFAULT_REGULAR_API_WHITELIST = "0.0.0.0/0, ::/0";

    /** Property name of the whitelist for Proxy UI API's key management API */
    public static final String PROXY_UI_API_KEY_MANAGEMENT_API_WHITELIST =
            PREFIX + "proxy-ui-api.key-management-api-whitelist";

    /** Property name of the whitelist for Proxy UI API's regular APIs */
    public static final String PROXY_UI_API_REGULAR_API_WHITELIST =
            PREFIX + "proxy-ui-api.regular-api-whitelist";

    /** Property name of the flag for enabling automatic time-stamping service URL updates */
    public static final String PROXY_UI_API_AUTO_UPDATE_TIMESTAMP_SERVICE_URL =
            PREFIX + "proxy-ui-api.auto-update-timestamp-service-url";

    /** property name of the flag to allow generating csr for key with certificates */
    public static final String PROXY_UI_API_ALLOW_CSR_FOR_KEY_WITH_CERTIFICATE =
            PREFIX + "proxy-ui-api.allow-csr-for-key-with-certificate";

    /** property name of the number of attempts to check whether the acme authorizations have completed */
    public static final String PROXY_UI_API_ACME_AUTHORIZATION_WAIT_ATTEMPTS =
            PREFIX + "proxy-ui-api.acme-authorization-wait-attempts";

    /** property name of the amount of seconds to wait between acme authorization completion check attempts */
    public static final String PROXY_UI_API_ACME_AUTHORIZATION_WAIT_INTERVAL =
            PREFIX + "proxy-ui-api.acme-authorization-wait-interval";

    /** property name of the number of attempts to check whether the acme certificate is ready */
    public static final String PROXY_UI_API_ACME_CERTIFICATE_WAIT_ATTEMPTS =
            PREFIX + "proxy-ui-api.acme-certificate-wait-attempts";

    /** property name of the amount of seconds to wait between acme certificate completion check attempts */
    public static final String PROXY_UI_API_ACME_CERTIFICATE_WAIT_INTERVAL =
            PREFIX + "proxy-ui-api.acme-certificate-wait-interval";

    /** property name of the amount of days the ACME server account's self-signed certificate is valid */
    public static final String PROXY_UI_API_ACME_ACCOUNT_KEY_PAIR_EXPIRATION_IN_DAYS =
            PREFIX + "proxy-ui-api.acme-certificate-account-key-pair-expiration";

    /** property name of whether the service should listen on port 80 for incoming acme challenge requests */
    public static final String PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED =
            PREFIX + "proxy-ui-api.acme-challenge-port-enabled";
    public static final String PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED_ENV =
            propertyNameToEnvVariable(PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED);

    public static final String PROXY_UI_API_ACME_RENEWAL_ACTIVE =
            PREFIX + "proxy-ui-api.acme-renewal-active";

    public static final String PROXY_UI_API_ACME_RENEWAL_RETRY_DELAY =
            PREFIX + "proxy-ui-api.acme-renewal-retry-delay";

    public static final String PROXY_UI_API_ACME_RENEWAL_INTERVAL =
            PREFIX + "proxy-ui-api.acme-renewal-interval";

    public static final String PROXY_UI_API_ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE =
            PREFIX + "proxy-ui-api.acme-renewal-time-before-expiration-date";

    public static final String PROXY_UI_API_ACME_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE =
            PREFIX + "proxy-ui-api.acme-keypair-renewal-time-before-expiration-date";

    public static final String PROXY_UI_API_AUTOMATIC_ACTIVATE_AUTH_CERTIFICATE =
            PREFIX + "proxy-ui-api.automatic-activate-auth-certificate";

    public static final String PROXY_UI_API_AUTOMATIC_ACTIVATE_ACME_SIGN_CERTIFICATE =
            PREFIX + "proxy-ui-api.automatic-activate-acme-sign-certificate";

    /** property name of whether notification e-mail is sent in case of automatic certificate renewal job success */
    public static final String PROXY_UI_API_ACME_RENEWAL_SUCCESS_NOTIFICATION_ENABLED =
            PREFIX + "proxy-ui-api.acme-renewal-success-notification-enabled";

    /** property name of whether notification e-mail is sent in case of automatic certificate renewal job failure */
    public static final String PROXY_UI_API_ACME_RENEWAL_FAILURE_NOTIFICATION_ENABLED =
            PREFIX + "proxy-ui-api.acme-renewal-failure-notification-enabled";

    /** property name of whether notification e-mail is sent when authentication certificate has been registered in global conf */
    public static final String PROXY_UI_API_AUTH_CERT_REGISTERED_NOTIFICATION_ENABLED =
            PREFIX + "proxy-ui-api.auth-cert-registered-notification-enabled";

    /** property name of whether notification e-mail is sent when certificate has been automatically activated */
    public static final String PROXY_UI_API_CERT_AUTO_ACTIVATION_NOTIFICATION_ENABLED =
            PREFIX + "proxy-ui-api.cert-auto-activation-notification-enabled";

    /** property name of whether notification e-mail is sent when certificate automatic activation has failed  */
    public static final String PROXY_UI_API_CERT_AUTO_ACTIVATION_FAILURE_NOTIFICATION_ENABLED =
            PREFIX + "proxy-ui-api.cert-auto-activation-failure-notification-enabled";

    /** Locale for mail notifications, which determines the language of the notifications.
     * To add a new locale a corresponding notifications_[locale].properties file needs to be added to the resource bundle  */
    public static final String PROXY_UI_API_MAIL_NOTIFICATION_LOCALE =
            PREFIX + "proxy-ui-api.mail-notification-locale";

    // Proxy ------------------------------------------------------------------

    private static final String PROXY_PREFIX = PREFIX + "proxy.";

    /** Property name of controlling SSL support between Proxies. */
    public static final String PROXY_SSL_SUPPORT =
            PROXY_PREFIX + "ssl-enabled";

    /** Property name of the configuration anchor file. */
    public static final String CONFIGURATION_ANCHOR_FILE =
            PROXY_PREFIX + "configuration-anchor-file";

    /** Property name of the Proxy's local configuration file. */
    public static final String DATABASE_PROPERTIES =
            PROXY_PREFIX + "database-properties";

    /** Property name of the Proxy's connector host name. */
    public static final String PROXY_CONNECTOR_HOST =
            PROXY_PREFIX + "connector-host";

    /** Property name of the Client Proxy's port number. */
    public static final String PROXY_CLIENT_HTTP_PORT =
            PROXY_PREFIX + "client-http-port";

    /** Property name of the Client Proxy's port number. */
    public static final String PROXY_CLIENT_HTTPS_PORT =
            PROXY_PREFIX + "client-https-port";

    /** Property name of the Client Proxy's timeout (milliseconds). */
    public static final String PROXY_CLIENT_TIMEOUT =
            PROXY_PREFIX + "client-timeout";

    /** Property name of the Server Proxy's port number. */
    public static final String PROXY_SERVER_PORT =
            PROXY_PREFIX + "server-port";

    /** Property name of the Server Proxy's listen address. */
    public static final String PROXY_SERVER_LISTEN_ADDRESS =
            PROXY_PREFIX + "server-listen-address";

    /** Property name of the Server Proxy's listen port number. */
    public static final String PROXY_SERVER_LISTEN_PORT =
            PROXY_PREFIX + "server-listen-port";

    /** Property name of the cached OCSP response path for signer operation. */
    public static final String OCSP_CACHE_PATH = SIGNER_PREFIX + "ocsp-cache-path";

    /** Property name of the Ocsp Responder port. */
    public static final String OCSP_RESPONDER_PORT =
            PROXY_PREFIX + "ocsp-responder-port";

    /** Property name of the Ocsp Responder listen address. */
    public static final String OCSP_RESPONDER_LISTEN_ADDRESS =
            PROXY_PREFIX + "ocsp-responder-listen-address";

    /** Property name of the Ocsp Responder Client connect timeout. */
    public static final String OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT =
            PROXY_PREFIX + "ocsp-responder-client-connect-timeout";

    /** Property name of the Ocsp Responder Client read timeout. */
    public static final String OCSP_RESPONDER_CLIENT_READ_TIMEOUT =
            PROXY_PREFIX + "ocsp-responder-client-read-timeout";

    /** Property name of the flag to turn off proxy client SSL verification. */
    public static final String PROXY_VERIFY_CLIENT_CERT = PROXY_PREFIX + "verify-client-cert";

    /** Property name of the flag to turn on proxy client SSL logging. */
    public static final String PROXY_LOG_CLIENT_CERT = PROXY_PREFIX + "log-client-cert";

    /** Property name of the ClientProxy Jetty server configuration file. */
    public static final String JETTY_CLIENTPROXY_CONFIGURATION_FILE = PROXY_PREFIX + "jetty-clientproxy-configuration-file";

    /** Property name of the ServerProxy Jetty server configuration file. */
    public static final String JETTY_SERVERPROXY_CONFIGURATION_FILE = PROXY_PREFIX + "jetty-serverproxy-configuration-file";

    /** Property name of the CertHashBasedOcspResponder Jetty server configuration file. */
    public static final String JETTY_OCSP_RESPONDER_CONFIGURATION_FILE = PROXY_PREFIX + "jetty-ocsp-responder-configuration-file";

    /** Property name of the ClientProxy HTTPS connector and ServerProxy HTTP client supported TLS protocols */
    private static final String PROXY_CLIENT_TLS_PROTOCOLS =
            PROXY_PREFIX + "client-tls-protocols";

    /** Property name of the ClientProxy HTTPS connector and ServerProxy HTTP client supported TLS cipher suites */
    private static final String PROXY_CLIENT_TLS_CIPHERS =
            PROXY_PREFIX + "client-tls-ciphers";

    /** Property name of the ClientProxy HTTPS client and ServerProxy HTTPS connector supported TLS cipher suites */
    private static final String PROXY_XROAD_TLS_CIPHERS = PROXY_PREFIX + "xroad-tls-ciphers";

    private static final String SIGNER_ENFORCE_TOKEN_PIN_POLICY = SIGNER_PREFIX + "enforce-token-pin-policy";

    public static final String SERVER_CONF_CACHE_PERIOD =
            PROXY_PREFIX + "server-conf-cache-period";

    public static final String SERVER_CONF_CLIENT_CACHE_SIZE = PROXY_PREFIX + "server-conf-client-cache-size";

    public static final String SERVER_CONF_SERVICE_CACHE_SIZE = PROXY_PREFIX + "server-conf-service-cache-size";

    public static final String SERVER_CONF_ACL_CACHE_SIZE = PROXY_PREFIX + "server-conf-acl-cache-size";

    public static final String SERVER_CONF_SERVICE_ENDPOINTS_CACHE_SIZE = PROXY_PREFIX + "server-conf-service-endpoints-cache-size";

    /** Property name of the idle time that connections to the ServerProxy Connector are allowed, in milliseconds */
    private static final String SERVERPROXY_CONNECTOR_MAX_IDLE_TIME =
            PROXY_PREFIX + "server-connector-max-idle-time";

    /**
     * Property name of the idle time that connections to the serverproxy connector are initially allowed,
     * in milliseconds
     */
    private static final String SERVERPROXY_CONNECTOR_INITIAL_IDLE_TIME =
            PROXY_PREFIX + "server-connector-initial-idle-time";

    /** Property name of the server Connector socket SO_LINGER timer, in seconds, value of -1 means off */
    private static final String SERVERPROXY_CONNECTOR_SO_LINGER =
            PROXY_PREFIX + "server-connector-so-linger";

    /** Property name of the server's minimum supported client version */
    private static final String SERVERPROXY_MIN_SUPPORTED_CLIENT_VERSION =
            PROXY_PREFIX + "server-min-supported-client-version";

    private static final String SERVERPROXY_SUPPORT_CLIENTS_POOLED_CONNECTIONS =
            PROXY_PREFIX + "server-support-clients-pooled-connections";

    /**
     * Property name of the idle time that connections to the clientproxy connector are initially allowed,
     * in milliseconds
     */
    private static final String CLIENTPROXY_CONNECTOR_INITIAL_IDLE_TIME =
            PROXY_PREFIX + "client-connector-initial-idle-time";

    /** Property name of the idle time that ClientProxy connections are allowed, in milliseconds */
    private static final String CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME =
            PROXY_PREFIX + "client-connector-max-idle-time";

    /** Property name of the client connector socket SO_LINGER timer, in seconds, value of -1 means off */
    private static final String CLIENTPROXY_CONNECTOR_SO_LINGER =
            PROXY_PREFIX + "client-connector-so-linger";

    /**
     * Property name for he connection maximum idle time that should be set for client proxy apache HttpClient,
     * in milliseconds, value 0 means infinite timeout, -1 means the system default
     */
    private static final String CLIENTPROXY_HTTPCLIENT_TIMEOUT =
            PROXY_PREFIX + "client-httpclient-timeout";

    /** Property name for the so_linger value that should be set for client proxy apache HttpClient */
    private static final String CLIENTPROXY_HTTPCLIENT_SO_LINGER =
            PROXY_PREFIX + "client-httpclient-so-linger";

    private static final String CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL =
            PROXY_PREFIX + "client-idle-connection-monitor-interval";

    private static final String CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME =
            PROXY_PREFIX + "client-idle-connection-monitor-timeout";

    private static final String CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR =
            PROXY_PREFIX + "client-use-idle-connection-monitor";

    private static final String CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS =
            PROXY_PREFIX + "pool-total-max-connections";

    private static final String CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE =
            PROXY_PREFIX + "pool-total-default-max-connections-per-route";

    private static final String CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE =
            PROXY_PREFIX + "client-use-fastest-connecting-ssl-socket-autoclose";

    public static final String CLIENTPROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD =
            PROXY_PREFIX + "client-fastest-connecting-ssl-uri-cache-period";

    private static final String CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS =
            PROXY_PREFIX + "pool-validate-connections-after-inactivity-of-millis";

    private static final String CLIENTPROXY_POOL_REUSE_CONNECTIONS =
            PROXY_PREFIX + "pool-enable-connection-reuse";

    private static final String PROXY_HEALTH_CHECK_INTERFACE = PROXY_PREFIX + "health-check-interface";

    public static final String PROXY_HEALTH_CHECK_PORT = PROXY_PREFIX + "health-check-port";

    private static final String ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK =
            PROXY_PREFIX + "enforce-client-is-cert-validity-period-check";

    private static final String PROXY_BACKUP_ENCRYPTION_ENABLED = PROXY_PREFIX + "backup-encryption-enabled";

    private static final String PROXY_BACKUP_ENCRYPTION_KEY_IDS = PROXY_PREFIX + "backup-encryption-keyids";

    private static final String HSM_HEALTH_CHECK_ENABLED = PROXY_PREFIX + "hsm-health-check-enabled";
    private static final String PROXY_MESSAGE_SIGN_DIGEST_NAME = PROXY_PREFIX + "message-sign-digest-name";
    public static final String PROXY_MEMORY_USAGE_THRESHOLD = PROXY_PREFIX + "memory-usage-threshold";

    private static final String FALSE = Boolean.FALSE.toString();
    private static final String TRUE = Boolean.TRUE.toString();
    private static final String DEFAULT_HSM_HEALTH_CHECK_ENABLED = FALSE;
    private static final String DEFAULT_PROXY_BACKUP_ENCRYPTED = FALSE;
    private static final String DEFAULT_CENTER_TRUSTED_ANCHORS_ALLOWED = FALSE;

    private static final String DEFAULT_CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS = FALSE;

    private static final String DEFAULT_CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS = FALSE;

    private static final String DEFAULT_CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS = FALSE;

    private static final String DEFAULT_AUTO_UPDATE_TIMESTAMP_SERVICE_URL = FALSE;

    private static final String DEFAULT_ALLOW_CSR_FOR_KEY_WITH_CERTIFICATE = FALSE;

    private static final String DEFAULT_SERVERPROXY_CONNECTOR_MAX_IDLE_TIME = "0";

    private static final String DEFAULT_PROXY_CONNECTOR_INITIAL_IDLE_TIME = "30000";

    private static final String DEFAULT_SERVERPROXY_CONNECTOR_SO_LINGER = "-1";

    private static final String DEFAULT_SERVERPROXY_SUPPORT_CLIENTS_POOLED_CONNECTIONS = FALSE;

    private static final String DEFAULT_CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME = "0";

    private static final String DEFAULT_CLIENTPROXY_CONNECTOR_SO_LINGER = "-1";

    private static final String DEFAULT_CLIENTPROXY_HTTPCLIENT_TIMEOUT = "0";

    private static final String DEFAULT_CLIENTPROXY_HTTPCLIENT_SO_LINGER = "-1";

    public static final String DEFAULT_OCSP_RESPONDER_CLIENT_READ_TIMEOUT = "30000";

    private static final String DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL = "30000";

    private static final String DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME = "60000";

    private static final String DEFAULT_CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR = TRUE;

    private static final String DEFAULT_CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS = "10000";

    private static final String DEFAULT_CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE = "2500";

    private static final String DEFAULT_CLIENTPROXY_TIMEOUT = "30000";

    private static final String DEFAULT_CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE = TRUE;

    private static final String DEFAULT_CLIENTPROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD = "3600";

    private static final String DEFAULT_ENV_MONITOR_LIMIT_REMOTE_DATA_SET = FALSE;

    private static final String DEFAULT_CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS = "2000";

    private static final String DEFAULT_ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK = FALSE;

    /**
     * The default value of the on/off switch for a group of settings that affect whether or not pooled connections
     * for the ClientProxy can be actually reused
     **/
    private static final String DEFAULT_CLIENTPROXY_POOL_REUSE_CONNECTIONS = FALSE;

    private static final String DEFAULT_PROXY_HEALTH_CHECK_INTERFACE = "0.0.0.0";

    private static final String DEFAULT_PROXY_HEALTH_CHECK_PORT = "0";

    public static final String DEFAULT_SIGNER_ENFORCE_TOKEN_PIN_POLICY = FALSE;

    private static final String OCSP_VERIFIER_CACHE_PERIOD =
            PROXY_PREFIX + "ocsp-verifier-cache-period";

    private static final int OCSP_VERIFIER_CACHE_PERIOD_MAX = 180;

    // Signer -----------------------------------------------------------------

    /** Property name of the key configuration file. */
    public static final String KEY_CONFIGURATION_FILE = SIGNER_PREFIX + "key-configuration-file";

    /** Property name of the device configuration file. */
    public static final String DEVICE_CONFIGURATION_FILE = SIGNER_PREFIX + "device-configuration-file";

    /** Property name of the Signer's admin port number. */
    public static final String SIGNER_ADMIN_PORT = SIGNER_PREFIX + "admin-port";

    /** Property name of the SignerClient's timeout. */
    public static final String SIGNER_CLIENT_TIMEOUT = SIGNER_PREFIX + "client-timeout";

    public static final String SIGNER_MODULE_INSTANCE_PROVIDER = SIGNER_PREFIX + "module-instance-provider";

    public static final String SIGNER_KEY_LENGTH = SIGNER_PREFIX + "key-length";
    public static final String SIGNER_KEY_NAMED_CURVE = SIGNER_PREFIX + "key-named-curve";

    public static final String PASSWORD_STORE_IPC_KEY_PATHNAME = SIGNER_PREFIX + "password-store-ipc-key-pathname";

    public static final int MIN_SIGNER_KEY_LENGTH = 2048;
    public static final int DEFAULT_SIGNER_KEY_LENGTH = MIN_SIGNER_KEY_LENGTH;

    public static final String DEFAULT_SIGNER_CLIENT_TIMEOUT = "60000";

    public static final String SIGNER_CSR_SIGNATURE_DIGEST_ALGORITHM = SIGNER_PREFIX + "csr-signature-digest-algorithm";

    public static final String OCSP_RESPONSE_RETRIEVAL_ACTIVE = SIGNER_PREFIX + "ocsp-response-retrieval-active";

    public static final String SIGNER_OCSP_RETRY_DELAY = SIGNER_PREFIX + "ocsp-retry-delay";

    private static final String DEFAULT_SIGNER_OCSP_RETRY_DELAY = "60";

    public static final String SIGNER_MODULE_MANAGER_UPDATE_INTERVAL = SIGNER_PREFIX + "module-manager-update-interval";
    public static final String SOFT_TOKEN_RSA_SIGN_MECHANISM = SIGNER_PREFIX + "soft-token-rsa-sign-mechanism";
    public static final String SOFT_TOKEN_EC_SIGN_MECHANISM = SIGNER_PREFIX + "soft-token-ec-sign-mechanism";
    public static final String SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM = SIGNER_PREFIX + "soft-token-pin-keystore-algorithm";
    public static final String SIGNER_SELF_SIGNED_CERT_DIGEST_ALGORITHM = SIGNER_PREFIX + "selfsigned-cert-digest-algorithm";

    public static final String DEFAULT_SIGNER_MODULE_MANAGER_UPDATE_INTERVAL = "60";
    public static final KeyAlgorithm DEFAULT_SIGNER_DEFAULT_KEY_ALGORITHM = KeyAlgorithm.RSA;
    public static final String DEFAULT_SIGNER_KEY_NAMED_CURVE = "secp256r1";
    public static final KeyAlgorithm DEFAULT_SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM = KeyAlgorithm.RSA;

    public static final String DEFAULT_DOWNLOADER_READ_TIMEOUT = "30000";
    public static final String DEFAULT_DOWNLOADER_CONNECT_TIMEOUT = "10000";

    // AntiDos ----------------------------------------------------------------

    /** Property name of the AntiDos on/off switch */
    public static final String ANTIDOS_ENABLED =
            PREFIX + "anti-dos.enabled";

    /** Property name of the maximum number of allowed parallel connections */
    public static final String ANTIDOS_MAX_PARALLEL_CONNECTIONS =
            PREFIX + "anti-dos.max-parallel-connections";

    /** Property name of the maximum allowed cpu load value */
    public static final String ANTIDOS_MAX_CPU_LOAD =
            PREFIX + "anti-dos.max-cpu-load";

    /** Property name of the minimum number of free file handles */
    public static final String ANTIDOS_MIN_FREE_FILEHANDLES =
            PREFIX + "anti-dos.min-free-file-handles";

    /** Property name of the maximum allowed JVM heap usage value */
    public static final String ANTIDOS_MAX_HEAP_USAGE =
            PREFIX + "anti-dos.max-heap-usage";

    // Configuration client ---------------------------------------------------

    public static final String CONFIGURATION_CLIENT_PORT =
            PREFIX + "configuration-client.port";

    public static final String CONFIGURATION_CLIENT_ADMIN_PORT =
            PREFIX + "configuration-client.admin-port";

    public static final String CONFIGURATION_CLIENT_UPDATE_INTERVAL_SECONDS =
            PREFIX + "configuration-client.update-interval";

    public static final String CONFIGURATION_CLIENT_PROXY_CONFIGURATION_BACKUP_CRON =
            PREFIX + "configuration-client.proxy-configuration-backup-cron";

    public static final String CONFIGURATION_CLIENT_GLOBAL_CONF_TLS_CERT_VERIFICATION =
            PREFIX + "configuration-client.global-conf-tls-cert-verification";

    public static final String CONFIGURATION_CLIENT_GLOBAL_CONF_HOSTNAME_VERIFICATION =
            PREFIX + "configuration-client.global-conf-hostname-verification";

    public static final String CONFIGURATION_CLIENT_DOWNLOADER_READ_TIMEOUT =
            PREFIX + "configuration-client.downloader-read-timeout";

    public static final String CONFIGURATION_CLIENT_DOWNLOADER_CONNECT_TIMEOUT =
            PREFIX + "configuration-client.downloader-connect-timeout";

    public static final String CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS =
            PREFIX + "configuration-client.allowed-federations";

    /**
     * A constant to describe the X-Road instances this security server federates with.
     * {@link #CUSTOM} means a list of named, comma-separated X-Road instances to allow.
     * {@link #ALL} naturally means all and {@link #NONE} means federation is disabled.
     * The configurations for those instances won't be downloaded.
     */
    public enum AllowedFederationMode { ALL, NONE, CUSTOM }

    // Center -----------------------------------------------------------------

    public static final String CENTER_DATABASE_PROPERTIES = CENTER_PREFIX + "database-properties";

    public static final String CENTER_TRUSTED_ANCHORS_ALLOWED = CENTER_PREFIX + "trusted-anchors-allowed";

    public static final String CENTER_INTERNAL_DIRECTORY = CENTER_PREFIX + "internal-directory";

    public static final String CENTER_EXTERNAL_DIRECTORY = CENTER_PREFIX + "external-directory";

    private static final String CENTER_GENERATED_CONF_DIR = CENTER_PREFIX + "generated-conf-dir";

    /** Property name of the path where conf backups are created. */
    public static final String CONF_BACKUP_PATH = CENTER_PREFIX + "conf-backup-path";

    /** Property name of enabling automatic approval of auth cert registration requests. */
    public static final String CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS = CENTER_PREFIX + "auto-approve-auth-cert-reg-requests";

    /** Property name of enabling automatic approval of client registration requests. */
    public static final String CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS = CENTER_PREFIX + "auto-approve-client-reg-requests";

    /** Property name of enabling automatic approval of owner change requests. */
    public static final String CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS = CENTER_PREFIX + "auto-approve-owner-change-requests";

    // Misc -------------------------------------------------------------------

    /** Property name of the configuration files path. */
    public static final String CONF_PATH = PREFIX + "conf.path";

    /** Property name of the log folder for Log Reader. */
    public static final String LOG_READER_PATH = PREFIX + "logReader.path";

    /** Property name of the application log file path. */
    public static final String LOG_PATH = PREFIX + "appLog.path";

    /** Property name of the application log level of ee.ria.xroad. */
    public static final String XROAD_LOG_LEVEL = PREFIX + "appLog.xroad.level";

    // Proxy UI ---------------------------------------------------------------

    /** Property name of the WSDL validator command. */
    public static final String WSDL_VALIDATOR_COMMAND =
            PREFIX + "proxy-ui-api.wsdl-validator-command";

    /**
     * Property name of the signature digest algorithm ID used for generating authentication certificate
     * registration request.
     */
    private static final String PROXYUI_AUTH_CERT_REG_SIGNATURE_DIGEST_ALGORITHM_ID =
            PREFIX + "proxy-ui-api.auth-cert-reg-signature-digest-algorithm-id";

    /**
     * Property name of the Security Server url, used to send management requests from Proxy UI.
     */
    private static final String PROXYUI_SECURITY_SERVER_URL = PREFIX + "proxy-ui-api.security-server-url";

    /**
     * Property name of the management request sender client keystore path, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE =
            PREFIX + "proxy-ui-api.management-request-sender-client-keystore";

    /**
     * Property name of the management request sender client keystore password, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD =
            PREFIX + "proxy-ui-api.management-request-sender-client-keystore-password";
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD_ENV =
            propertyNameToEnvVariable(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD);

    /**
     * Property name of the management request sender client truststore path, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE =
            PREFIX + "proxy-ui-api.management-request-sender-client-truststore";

    /**
     * Property name of the management request sender client truststore password, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD =
            PREFIX + "proxy-ui-api.management-request-sender-client-truststore-password";
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD_ENV =
            propertyNameToEnvVariable(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD);

    // Proxy & Central monitor agent ------------------------------------------

    /** Property name of the proxy monitor info collection interval. */
    public static final String PROXY_PARAMS_COLLECTING_INTERVAL =
            PREFIX + "proxy-monitor-agent.params-collecting-interval";

    public static final String NET_STATS_FILE =
            PREFIX + "proxy-monitor-agent.net-stats-file";

    // Configuration proxy ------------------------------------------------- //

    /** Property name of the confproxy download script path. */
    public static final String CONFIGURATION_PROXY_DOWNLOAD_SCRIPT =
            PREFIX + "configuration-proxy.download-script";

    /** Property name of the confproxy configuration path. */
    public static final String CONFIGURATION_PROXY_CONF_PATH =
            PREFIX + "configuration-proxy.configuration-path";

    /** Property name of the confproxy public configuration distribution path. */
    public static final String CONFIGURATION_PROXY_GENERATED_CONF_PATH =
            PREFIX + "configuration-proxy.generated-conf-path";

    /** Property name of the confproxy configuration signature digest algorithm. */
    public static final String CONFIGURATION_PROXY_SIGNATURE_DIGEST_ALGORITHM_ID =
            PREFIX + "configuration-proxy.signature-digest-algorithm-id";

    /** Property name of the confproxy configuration file hashing algorithm. */
    public static final String CONFIGURATION_PROXY_HASH_ALGORITHM_URI =
            PREFIX + "configuration-proxy.hash-algorithm-uri";

    /** Property name of the confproxy webserver address. */
    public static final String CONFIGURATION_PROXY_ADDRESS =
            PREFIX + "configuration-proxy.address";

    // Environmental Monitoring  -------------------------- //

    /** Property name of environmental monitor port. */
    public static final String ENV_MONITOR_PORT =
            PREFIX + "env-monitor.port";

    /** Property name of environmental monitor limiting remote data set. */
    public static final String ENV_MONITOR_LIMIT_REMOTE_DATA_SET =
            PREFIX + "env-monitor.limit-remote-data-set";

    /** Property name of system metrics sensor interval. */
    public static final String ENV_MONITOR_SYSTEM_METRICS_SENSOR_INTERVAL =
            PREFIX + "env-monitor.system-metrics-sensor-interval";

    /** Property name of disk space sensor interval. */
    public static final String ENV_MONITOR_DISK_SPACE_SENSOR_INTERVAL =
            PREFIX + "env-monitor.disk-space-sensor-interval";

    /** Property name of system metrics sensor interval. */
    public static final String ENV_MONITOR_EXEC_LISTING_SENSOR_INTERVAL =
            PREFIX + "env-monitor.exec-listing-sensor-interval";

    /** Property name of certificate info sensor refresh interval. */
    public static final String ENV_MONITOR_CERTIFICATE_INFO_SENSOR_INTERVAL =
            PREFIX + "env-monitor.certificate-info-sensor-interval";

    public static final String ONE_DAY_AS_SECONDS = String.valueOf(24 * 60 * 60);

    // gRPC internal cross-component transport configuration  -------------------------- //

    /**
     * Property name for gRPC host.
     */
    public static final String GRPC_INTERNAL_HOST =
            PREFIX + "common.grpc-internal-host";

    /**
     * Property name for gRPC host.
     */
    public static final String GRPC_INTERNAL_TLS_ENABLED =
            PREFIX + "common.grpc-internal-tls-enabled";

    /**
     * Property name for gRPC signer port.
     */
    public static final String GRPC_SIGNER_PORT = SIGNER_PREFIX + "grpc-port";

    /**
     * Property name for gRPC proxy port.
     */
    public static final String PROXY_GRPC_PORT = PROXY_PREFIX + "grpc-port";

    /**
     * Property name for gRPC internal keystore location.
     */
    public static final String GRPC_INTERNAL_KEYSTORE =
            PREFIX + "common.grpc-internal-keystore";

    /**
     * Property name for gRPC internal keystore password.
     */
    public static final String GRPC_INTERNAL_KEYSTORE_PASSWORD =
            PREFIX + "common.grpc-internal-keystore-password";
    public static final String GRPC_INTERNAL_KEYSTORE_PASSWORD_ENV =
            propertyNameToEnvVariable(GRPC_INTERNAL_KEYSTORE_PASSWORD);

    /**
     * Property name for gRPC internal truststore location.
     */
    public static final String GRPC_INTERNAL_TRUSTSTORE =
            PREFIX + "common.grpc-internal-truststore";

    /**
     * Property name for gRPC internal truststore password.
     */
    public static final String GRPC_INTERNAL_TRUSTSTORE_PASSWORD =
            PREFIX + "common.grpc-internal-truststore-password";
    public static final String GRPC_INTERNAL_TRUSTSTORE_PASSWORD_ENV =
            propertyNameToEnvVariable(GRPC_INTERNAL_TRUSTSTORE_PASSWORD);

    /**
     * Property name for global configuration refresh rate in seconds.
     */
    public static final String GLOBAL_CONF_REFRESH_RATE_SECONDS = PREFIX + "common.global-conf-refresh-rate-seconds";

    // Cluster node configuration ------------------------------------------ //

    /**
     * The type of this server node in the cluster. Default is {@link #STANDALONE} which means this server is not
     * part of a cluster.
     */
    public enum NodeType {
        STANDALONE, MASTER, SLAVE;

        /**
         * Parse an enum (ignoring case) from the given String or return the default {@link #STANDALONE}
         * if the argument is not understood.
         *
         * @param name
         * @return
         */
        public static NodeType fromStringIgnoreCaseOrReturnDefault(String name) {
            return Arrays.stream(NodeType.values())
                    .filter(e -> e.name().equalsIgnoreCase(name))
                    .findAny()
                    .orElse(STANDALONE);
        }
    }

    public static final String NODE_TYPE = PREFIX + "node.type";

    // Configuration file names and section names -------------------------- //

    public static final String CONF_FILE_COMMON =
            getConfPath() + "conf.d/common.ini";

    public static final String CONF_FILE_PROXY =
            getConfPath() + "conf.d/proxy.ini";

    public static final String CONF_FILE_NODE =
            getConfPath() + "conf.d/node.ini";

    public static final String CONF_FILE_PROXY_UI_API =
            getConfPath() + "conf.d/proxy-ui-api.ini";

    public static final String CONF_FILE_SIGNER =
            getConfPath() + "conf.d/signer.ini";

    public static final String CONF_FILE_CENTER =
            getConfPath() + "conf.d/center.ini";

    public static final String CONF_FILE_CONFPROXY =
            getConfPath() + "conf.d/confproxy.ini";

    public static final String CONF_FILE_OP_MONITOR =
            getConfPath() + "conf.d/op-monitor.ini";

    public static final String CONF_FILE_ENV_MONITOR =
            getConfPath() + "conf.d/addons/monitor.ini";

    public static final String CONF_FILE_USER_LOCAL =
            getConfPath() + "conf.d/local.ini";

    public static final String CONF_FILE_ADDON_PATH =
            getConfPath() + "conf.d/addons/";

    public static final String CONF_FILE_MESSAGE_LOG = CONF_FILE_ADDON_PATH + "message-log.ini";

    // --------------------------------------------------------------------- //

    // For testing purpose only!
    public static final boolean IGNORE_SIGNATURE_VERIFICATION = false;

    // --------------------------------------------------------------------- //

    public static final String DEFAULT_CONNECTOR_HOST = "0.0.0.0";

    /**
     * @return path to the directory where configuration files are located, '/etc/xroad/' by default.
     */
    public static String getConfPath() {
        return System.getProperty(CONF_PATH, DefaultFilepaths.CONF_PATH);
    }

    /**
     * @return path to the directory where application logs are stored, '/var/log/xroad/' by default.
     */
    public static String getLogPath() {
        return System.getProperty(LOG_PATH, DefaultFilepaths.LOG_PATH);
    }

    /**
     * @return log level of the 'ee.ria.xroad.*' packages, 'DEBUG' by default.
     */
    public static String getXROADLogLevel() {
        return System.getProperty(XROAD_LOG_LEVEL, "DEBUG");
    }

    /**
     * @return path to the proxy database configuration file, '/etc/xroad/db.properties' by default.
     */
    public static String getDatabasePropertiesFile() {
        return System.getProperty(DATABASE_PROPERTIES, getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /**
     * @return path to the proxy ssl configuration file, '/etc/xroad/ssl.properties' by default.
     */
    public static String getSslPropertiesFile() {
        return System.getProperty(PROXY_UI_API_SSL_PROPERTIES,
                getConfPath() + DefaultFilepaths.PROXY_UI_API_SSL_PROPERTIES);
    }

    /**
     * TO DO: not correct, fix
     *
     * @return whitelist for Proxy UI API's key management API, "127.0.0.0/8, ::1" (localhost) by default
     */
    public static String getKeyManagementApiWhitelist() {
        return System.getProperty(PROXY_UI_API_KEY_MANAGEMENT_API_WHITELIST,
                DEFAULT_KEY_MANAGEMENT_API_WHITELIST);
    }

    /**
     * @return whitelist for Proxy UI API's regular APIs, "0.0.0.0/0, ::/0" (allow all) by default
     */
    public static String getRegularApiWhitelist() {
        return System.getProperty(PROXY_UI_API_REGULAR_API_WHITELIST,
                DEFAULT_REGULAR_API_WHITELIST);
    }

    /**
     * @return whether automatic update of timestamp service URLs is enabled, 'false' by default.
     */
    public static boolean geUpdateTimestampServiceUrlsAutomatically() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_AUTO_UPDATE_TIMESTAMP_SERVICE_URL,
                DEFAULT_AUTO_UPDATE_TIMESTAMP_SERVICE_URL));
    }

    /**
     * @return whether generating CSR is allowed for with existing certificate, 'false' by default
     */
    public static boolean getAllowCsrForKeyWithCertificate() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_ALLOW_CSR_FOR_KEY_WITH_CERTIFICATE,
                DEFAULT_ALLOW_CSR_FOR_KEY_WITH_CERTIFICATE));
    }

    public static int getAcmeAuthorizationWaitAttempts() {
        return Integer.parseInt(System.getProperty(PROXY_UI_API_ACME_AUTHORIZATION_WAIT_ATTEMPTS, "5"));
    }

    public static long getAcmeAuthorizationWaitInterval() {
        return Long.parseLong(System.getProperty(PROXY_UI_API_ACME_AUTHORIZATION_WAIT_INTERVAL, "5"));
    }

    public static int getAcmeCertificateWaitAttempts() {
        return Integer.parseInt(System.getProperty(PROXY_UI_API_ACME_CERTIFICATE_WAIT_ATTEMPTS, "5"));
    }

    public static long getAcmeCertificateWaitInterval() {
        return Long.parseLong(System.getProperty(PROXY_UI_API_ACME_CERTIFICATE_WAIT_INTERVAL, "5"));
    }

    public static long getAcmeAccountKeyPairExpirationInDays() {
        return Long.parseLong(System.getProperty(PROXY_UI_API_ACME_ACCOUNT_KEY_PAIR_EXPIRATION_IN_DAYS, "365"));
    }

    public static boolean isAcmeChallengePortEnabled() {
        String isAcmeChallengePortEnabled = Optional.ofNullable(System.getenv().get(PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED_ENV))
                .orElse(System.getProperty(PROXY_UI_API_ACME_CHALLENGE_PORT_ENABLED, FALSE));
        return TRUE.equalsIgnoreCase(isAcmeChallengePortEnabled);
    }

    /**
     * @return path to the configuration anchor file, '/etc/xroad/configuration-anchor.xml' by default.
     */
    public static String getConfigurationAnchorFile() {
        return System.getProperty(CONFIGURATION_ANCHOR_FILE,
                getConfPath() + DefaultFilepaths.CONFIGURATION_ANCHOR_FILE);
    }

    /**
     * @return path to the directory where the downloaded global configuration is placed,
     * '/etc/xroad/globalconf/' by default.
     */
    public static String getConfigurationPath() {
        return System.getProperty(CONFIGURATION_PATH, getConfPath() + DefaultFilepaths.CONFIGURATION_PATH);
    }

    /**
     * @return path to the signing key configuration file, '/etc/xroad/signer/keyconf.xml' by default.
     */
    public static String getKeyConfFile() {
        return System.getProperty(KEY_CONFIGURATION_FILE, getConfPath() + DefaultFilepaths.KEY_CONFIGURATION_FILE);
    }

    /**
     * @return path to the signing key device configuration file, '/etc/xroad/signer/devices.ini' by default.
     */
    public static String getDeviceConfFile() {
        return System.getProperty(DEVICE_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.DEVICE_CONFIGURATION_FILE);
    }

    /**
     * @return path to the client proxy jetty server configuration file, '/etc/xroad/jetty/clientproxy.xml' by default.
     */
    public static String getJettyClientProxyConfFile() {
        return System.getProperty(JETTY_CLIENTPROXY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.JETTY_CLIENTPROXY_CONFIGURATION_FILE);
    }

    /**
     * @return path to the server proxy jetty server configuration file, '/etc/xroad/jetty/serverproxy.xml' by default.
     */
    public static String getJettyServerProxyConfFile() {
        return System.getProperty(JETTY_SERVERPROXY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.JETTY_SERVERPROXY_CONFIGURATION_FILE);
    }

    /**
     * @return path to the cert hash based OCSP responder jetty server configuration file,
     * '/etc/xroad/jetty/ocsp-responder.xml' by default.
     */
    public static String getJettyOcspResponderConfFile() {
        return System.getProperty(JETTY_OCSP_RESPONDER_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE);
    }

    /**
     * @return WSDL validator command string. Defaults to null.
     */
    public static String getWsdlValidatorCommand() {
        return System.getProperty(WSDL_VALIDATOR_COMMAND, null);
    }

    /**
     * @return signature digest algorithm ID used for generating authentication certificate registration request,
     * SHA-512 by default.
     */
    public static DigestAlgorithm getAuthCertRegSignatureDigestAlgorithmId() {
        return Optional.ofNullable(System.getProperty(PROXYUI_AUTH_CERT_REG_SIGNATURE_DIGEST_ALGORITHM_ID))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * @return Security Server url, used to send management requests from Proxy UI. Defaults to 'https://localhost:8443'.
     */
    public static String getProxyUiSecurityServerUrl() {
        return System.getProperty(PROXYUI_SECURITY_SERVER_URL, "https://localhost:" + getClientProxyHttpsPort());
    }

    /**
     * @return path to the management request sender client keystore. Uses PKCS#12 format.
     */
    public static String getManagementRequestSenderClientKeystore() {
        return System.getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE);
    }

    /**
     * @return management request sender client keystore password.
     */
    public static char[] getManagementRequestSenderClientKeystorePassword() {
        return Optional.ofNullable(System.getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD,
                        System.getenv().get(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD_ENV)))
                .map(String::toCharArray)
                .orElse(null);
    }

    /**
     * @return path to the management request sender client truststore. Uses PKCS#12 format.
     */
    public static String getManagementRequestSenderClientTruststore() {
        return System.getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE);
    }

    /**
     * @return management request sender client truststore password.
     */
    public static char[] getManagementRequestSenderClientTruststorePassword() {
        return Optional.ofNullable(System.getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD,
                        System.getenv().get(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD_ENV)))
                .map(String::toCharArray)
                .orElse(null);
    }

    /**
     * @return path to the directory where query logs are archived, '/var/lib/xroad/' by default.
     */
    public static String getLogReaderPath() {
        return System.getProperty(LOG_READER_PATH, DefaultFilepaths.SECURE_LOG_PATH);
    }

    /**
     * @return path to the directory where temporary files are stored, '/var/tmp/xroad/' by default.
     */
    public static String getTempFilesPath() {
        return System.getProperty(TEMP_FILES_PATH, DefaultFilepaths.TEMP_FILES_PATH);
    }

    /**
     * @return path to the directory where OCSP responses are stored, '/var/cache/xroad/' by default.
     */
    public static String getOcspCachePath() {
        return System.getProperty(OCSP_CACHE_PATH, DefaultFilepaths.OCSP_CACHE_PATH);
    }

    /**
     * @return path to the directory where configuration backups are stored, '/var/lib/xroad/backup/' by default.
     */
    public static String getConfBackupPath() {
        return System.getProperty(CONF_BACKUP_PATH, DefaultFilepaths.CONF_BACKUP_PATH);
    }

    /**
     * @return the host address on which the client proxy is listening, '0.0.0.0' by default.
     */
    public static String getConnectorHost() {
        return System.getProperty(PROXY_CONNECTOR_HOST, DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the HTTP port on which the client proxy is listening, '8080' by default.
     */
    public static int getClientProxyHttpPort() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_HTTP_PORT,
                Integer.toString(PortNumbers.CLIENT_HTTP_PORT)));
    }

    /**
     * @return the HTTPS port on which the client proxy is listening, '8443' by default.
     */
    public static int getClientProxyHttpsPort() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_HTTPS_PORT,
                Integer.toString(PortNumbers.CLIENT_HTTPS_PORT)));
    }

    /**
     * @return the client proxy connect timeout in milliseconds, '30000' by default.
     */
    public static int getClientProxyTimeout() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_TIMEOUT, DEFAULT_CLIENTPROXY_TIMEOUT));
    }

    /**
     * @return the HTTP port on which the server proxy listens for messages, '5500' by default.
     */
    public static int getServerProxyPort() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_PORT, Integer.toString(PortNumbers.PROXY_PORT)));
    }

    /**
     * @return the HTTP port on which the server proxy listens for messages, '5500' by default.
     */
    public static int getServerProxyListenPort() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_LISTEN_PORT, Integer.toString(PortNumbers.PROXY_PORT)));
    }

    /**
     * @return the host address on which the server proxy listens for messages, '0.0.0.0' by default.
     */
    public static String getServerProxyListenAddress() {
        return System.getProperty(PROXY_SERVER_LISTEN_ADDRESS, DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the port on which the signer admin listens for requests
     */
    public static int getSignerAdminPort() {
        return Integer.parseInt(System.getProperty(SIGNER_ADMIN_PORT, Integer.toString(PortNumbers.SIGNER_ADMIN_PORT)));
    }

    /**
     * @return the signer connection timeout in milliseconds, '60000' by default.
     */
    public static int getSignerClientTimeout() {
        return Integer.parseInt(System.getProperty(SIGNER_CLIENT_TIMEOUT, DEFAULT_SIGNER_CLIENT_TIMEOUT));
    }

    /**
     * @return authentication and signing key length when RSA is used.
     */
    public static int getSignerKeyLength() {
        return Math.max(MIN_SIGNER_KEY_LENGTH, Integer.getInteger(SIGNER_KEY_LENGTH, DEFAULT_SIGNER_KEY_LENGTH));
    }

    /**
     * @return authentication and signing key named curve when EC is used.
     */
    public static String getSignerKeyNamedCurve() {
        return System.getProperty(SIGNER_KEY_NAMED_CURVE, DEFAULT_SIGNER_KEY_NAMED_CURVE);
    }

    /**
     * Get the pathname for password store IPC key generation (used as an input for ftok kernel function).
     *
     * @return path
     */
    public static String getSignerPasswordStoreIPCKeyPathname() {
        return System.getProperty(PASSWORD_STORE_IPC_KEY_PATHNAME, "/");
    }

    /**
     * Get CSR signature digest algorithm, SHA-256 by default.
     *
     * @return algorithm
     */
    public static DigestAlgorithm getSignerCsrSignatureDigestAlgorithm() {
        return Optional.ofNullable(System.getProperty(SIGNER_CSR_SIGNATURE_DIGEST_ALGORITHM))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA256);
    }

    /**
     * @return whether OCSP-response retrieval loop should be activated
     */
    public static boolean isOcspResponseRetrievalActive() {
        return Boolean.parseBoolean(System.getProperty(OCSP_RESPONSE_RETRIEVAL_ACTIVE, TRUE));
    }

    /**
     * @return the OCSP-response retry delay in seconds that should be set for signer, 60 by default
     */
    public static int getOcspResponseRetryDelay() {
        return Integer.parseInt(System.getProperty(SIGNER_OCSP_RETRY_DELAY,
                DEFAULT_SIGNER_OCSP_RETRY_DELAY));
    }

    /**
     * @return the module manager update interval in seconds that should be set for signer, 60 by default
     */
    public static int getModuleManagerUpdateInterval() {
        return Integer.parseInt(System.getProperty(SIGNER_MODULE_MANAGER_UPDATE_INTERVAL,
                DEFAULT_SIGNER_MODULE_MANAGER_UPDATE_INTERVAL));
    }

    /**
     * @return software token signing mechanism type, CKM_RSA_PKCS by default
     */
    public static SignMechanism getSoftTokenRsaSignMechanism() {
        return Optional.ofNullable(System.getProperty(SOFT_TOKEN_RSA_SIGN_MECHANISM))
                .map(SignMechanism::valueOf)
                .orElse(SignMechanism.CKM_RSA_PKCS);
    }

    /**
     * @return software token signing mechanism type for EC keys, CKM_ECDSA by default
     */
    public static SignMechanism getSofTokenEcSignMechanism() {
        return Optional.ofNullable(System.getProperty(SOFT_TOKEN_EC_SIGN_MECHANISM))
                .map(SignMechanism::valueOf)
                .orElse(SignMechanism.CKM_ECDSA);
    }

    /**
     * @return software token keystore PIN file algorithm, RSA by default
     */
    public static KeyAlgorithm getSofTokenPinKeystoreAlgorithm() {
        return Optional.ofNullable(System.getProperty(SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM))
                .map(KeyAlgorithm::valueOf)
                .orElse(DEFAULT_SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM);
    }

    /**
     * @return software token keystore PIN file algorithm, RSA by default
     */
    public static DigestAlgorithm getSelfSignedCertDigestAlgorithm() {
        return Optional.ofNullable(System.getProperty(SIGNER_SELF_SIGNED_CERT_DIGEST_ALGORITHM))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * @return the ACME certificate renewal toggle
     */
    public static boolean isAcmeCertificateRenewalActive() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_ACME_RENEWAL_ACTIVE, "true"));
    }

    /**
     * @return the ACME certificate renewal retry delay in seconds
     */
    public static int getAcmeCertificateRenewalRetryDelay() {
        return Integer.parseInt(System.getProperty(PROXY_UI_API_ACME_RENEWAL_RETRY_DELAY, "60"));
    }

    /**
     * @return the ACME certificate renewal job interval in seconds
     */
    public static int getAcmeCertificateRenewalInterval() {
        return Integer.parseInt(System.getProperty(PROXY_UI_API_ACME_RENEWAL_INTERVAL, "3600"));
    }

    /**
     * @return when to trigger automatic renewal subtracted as days from the expiration date of the certificate.
     * Used when it's not possible to receive the ACME renewal information from the ACME server.
     */
    public static int getAcmeRenewalTimeBeforeExpirationDate() {
        return Integer.parseInt(System.getProperty(PROXY_UI_API_ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE, "14"));
    }

    /**
     * @return when to trigger automatic acme account keypair renewal subtracted as days from the expiration date of the certificate.
     */
    public static int getAcmeKeypairRenewalTimeBeforeExpirationDate() {
        return Integer.parseInt(System.getProperty(PROXY_UI_API_ACME_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE, "14"));
    }

    /**
     * @return whether to automatically activate new authentication certificates after they have been registered on the Central Server.
     */
    public static boolean getAutomaticActivateAuthCertificate() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_AUTOMATIC_ACTIVATE_AUTH_CERTIFICATE, FALSE));
    }

    /**
     * @return whether to automatically activate new signing certificates after they are ordered with ACME.
     */
    public static boolean getAutomaticActivateAcmeSignCertificate() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_AUTOMATIC_ACTIVATE_ACME_SIGN_CERTIFICATE, FALSE));
    }

    /**
     * @return true if ACME automatic renewal job success notifications are enabled
     */
    public static boolean getAcmeRenewalSuccessNotificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_ACME_RENEWAL_SUCCESS_NOTIFICATION_ENABLED, TRUE));
    }

    /**
     * @return true if ACME automatic renewal job failure notifications are enabled
     */
    public static boolean getAcmeRenewalFailureNotificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_ACME_RENEWAL_FAILURE_NOTIFICATION_ENABLED, TRUE));
    }

    /**
     * @return true if authentication certificate registered notifications are enabled
     */
    public static boolean getAuthCertRegisteredNotificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_AUTH_CERT_REGISTERED_NOTIFICATION_ENABLED, TRUE));
    }

    /**
     * @return true if certificate automatic activation success notifications are enabled
     */
    public static boolean getAcmeCertAutomaticallyActivatedNotificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_CERT_AUTO_ACTIVATION_NOTIFICATION_ENABLED, TRUE));
    }

    /**
     * @return true if certificate automatic activation failure notifications are enabled
     */
    public static boolean getAcmeCertAutomaticActivationFailureNotificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_UI_API_CERT_AUTO_ACTIVATION_FAILURE_NOTIFICATION_ENABLED, TRUE));
    }

    /**
     * @return Locale for mail notifications.
     * No default value here, notifications' resource bundle already has defaults in notifications.properties
     */
    public static String getMailNotificationLocale() {
        return System.getProperty(PROXY_UI_API_MAIL_NOTIFICATION_LOCALE);
    }

    /**
     * @return the gRPC port on which the configuration client is listening, '5665' by default.
     */
    public static int getConfigurationClientPort() {
        return Integer.parseInt(System.getProperty(CONFIGURATION_CLIENT_PORT,
                Integer.toString(PortNumbers.CONFIGURATION_CLIENT_PORT)));
    }

    /**
     * @return the HTTP port on which the configuration client is listening, '5675' by default.
     */
    public static int getConfigurationClientAdminPort() {
        return Integer.parseInt(System.getProperty(CONFIGURATION_CLIENT_ADMIN_PORT,
                Integer.toString(PortNumbers.CONFIGURATION_CLIENT_ADMIN_PORT)));
    }

    /**
     * @return the update interval in seconds at which configuration client
     * downloads the global configuration, '60' by default.
     */
    public static int getConfigurationClientUpdateIntervalSeconds() {
        return Integer.parseInt(System.getProperty(CONFIGURATION_CLIENT_UPDATE_INTERVAL_SECONDS, "60"));
    }

    /**
     * @return the proxy configuration auto backup cron expression.
     * defaults to '0 15 3 * * ?'
     */
    public static String getConfigurationClientProxyConfigurationBackupCron() {
        return System.getProperty(CONFIGURATION_CLIENT_PROXY_CONFIGURATION_BACKUP_CRON, "0 15 3 * * ?");
    }

    public static boolean isConfigurationClientGlobalConfTlsCertVerificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(CONFIGURATION_CLIENT_GLOBAL_CONF_TLS_CERT_VERIFICATION, TRUE));
    }

    public static boolean isConfigurationClientGlobalConfHostnameVerificationEnabled() {
        return Boolean.parseBoolean(System.getProperty(CONFIGURATION_CLIENT_GLOBAL_CONF_HOSTNAME_VERIFICATION, TRUE));
    }

    public static int getConfigurationClientDownloaderReadTimeout() {
        return Integer.parseInt(System.getProperty(CONFIGURATION_CLIENT_DOWNLOADER_READ_TIMEOUT, DEFAULT_DOWNLOADER_READ_TIMEOUT));
    }

    public static int getConfigurationClientDownloaderConnectTimeout() {
        return Integer.parseInt(System.getProperty(CONFIGURATION_CLIENT_DOWNLOADER_CONNECT_TIMEOUT, DEFAULT_DOWNLOADER_CONNECT_TIMEOUT));
    }

    public static String getConfigurationClientAllowedFederations() {
        return System.getProperty(CONFIGURATION_CLIENT_ALLOWED_FEDERATIONS, AllowedFederationMode.NONE.name());
    }

    /**
     * @return the HTTP port on which the server proxy OCSP responder is listening, '5577' by default.
     */
    public static int getOcspResponderPort() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_PORT, Integer.toString(PortNumbers.PROXY_OCSP_PORT)));
    }

    /**
     * @return the host address on which the server proxy OCSP responder is listening, '0.0.0.0' by default.
     */
    public static String getOcspResponderListenAddress() {
        return System.getProperty(OCSP_RESPONDER_LISTEN_ADDRESS, DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the OCSP Responder Client connect timeout in milliseconds, '20000' by default.
     */
    public static int getOcspResponderClientConnectTimeout() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT, "20000"));
    }

    /**
     * @return the OCSP Responder Client read timeout in milliseconds, '30000' by default.
     */
    public static int getOcspResponderClientReadTimeout() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_CLIENT_READ_TIMEOUT,
                DEFAULT_OCSP_RESPONDER_CLIENT_READ_TIMEOUT));
    }

    /**
     * @return whether SSL should be used between client and server proxies, 'true' by default.
     */
    public static boolean isSslEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_SSL_SUPPORT, TRUE));
    }

    /**
     * @return path to the central server database configuration file, '/etc/xroad/db.properties' by default.
     */
    public static String getCenterDatabasePropertiesFile() {
        return System.getProperty(CENTER_DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /**
     * @return whether configuration of trusted anchors is enabled in the central server UI, 'true' by default.
     */
    public static boolean getCenterTrustedAnchorsAllowed() {
        return Boolean.parseBoolean(System.getProperty(CENTER_TRUSTED_ANCHORS_ALLOWED,
                DEFAULT_CENTER_TRUSTED_ANCHORS_ALLOWED));
    }

    /**
     * @return the name of the signed internal configuration directory
     * that will be distributed to security servers inside the instance, internalconf' by default.
     */
    public static String getCenterInternalDirectory() {
        return System.getProperty(CENTER_INTERNAL_DIRECTORY, "internalconf");
    }

    /**
     * @return the name of the signed external configuration directory
     * that will be distributed to security servers inside the federation, 'externalconf' by default.
     */
    public static String getCenterExternalDirectory() {
        return System.getProperty(CENTER_EXTERNAL_DIRECTORY, "externalconf");
    }

    /**
     * @return path to the directory on the central server where both private
     * and shared parameter files are created for distribution, '/var/lib/xroad/public' by default.
     */
    public static String getCenterGeneratedConfDir() {
        return System.getProperty(CENTER_GENERATED_CONF_DIR, DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    /**
     * @return whether automatic approval of auth cert registration requests is enabled, 'false' by default.
     */
    public static boolean getCenterAutoApproveAuthCertRegRequests() {
        return Boolean.parseBoolean(System.getProperty(CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS,
                DEFAULT_CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS));
    }

    /**
     * @return whether automatic approval of client registration requests is enabled, 'false' by default.
     */
    public static boolean getCenterAutoApproveClientRegRequests() {
        return Boolean.parseBoolean(System.getProperty(CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS,
                DEFAULT_CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS));
    }

    /**
     * @return whether automatic approval of owner change requests is enabled, 'false' by default.
     */
    public static boolean getCenterAutoApproveOwnerChangeRequests() {
        return Boolean.parseBoolean(System.getProperty(CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS,
                DEFAULT_CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS));
    }

    /**
     * @return path to the directory containing configuration proxy configuration files,
     * '/etc/xroad/confproxy' by default.
     */
    public static String getConfigurationProxyConfPath() {
        return System.getProperty(CONFIGURATION_PROXY_CONF_PATH, getConfPath() + "confproxy/");
    }

    /**
     * @return path to the global configuration download script,
     * '/usr/share/xroad/scripts/download_instance_configuration.sh' by default.
     */
    public static String getConfigurationProxyDownloadScript() {
        return System.getProperty(CONFIGURATION_PROXY_DOWNLOAD_SCRIPT,
                "/usr/share/xroad/scripts/download_instance_configuration.sh");
    }

    /**
     * @return path to the directory on the configuration proxy where global
     * configuration files are generated for distribution, '/var/lib/xroad/public' by default.
     */
    public static String getConfigurationProxyGeneratedConfPath() {
        return System.getProperty(CONFIGURATION_PROXY_GENERATED_CONF_PATH,
                DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    /**
     * @return ID of the signing digest algorithm the configuration proxy uses when
     * signing generated global configuration directories, 'SHA-512' by default.
     */
    public static DigestAlgorithm getConfigurationProxySignatureDigestAlgorithmId() {
        return Optional.ofNullable(System.getProperty(CONFIGURATION_PROXY_SIGNATURE_DIGEST_ALGORITHM_ID))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * @return URI of the hashing algorithm the configuration proxy uses when
     * calculating hashes of files in the global configuratoin directory,
     * 'http://www.w3.org/2001/04/xmlenc#sha512' by default.
     */
    public static DigestAlgorithm getConfigurationProxyHashAlgorithmUri() {
        return Optional.ofNullable(System.getProperty(CONFIGURATION_PROXY_HASH_ALGORITHM_URI))
                .map(DigestAlgorithm::ofUri)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * @return the host address on which the configuration proxy listens for
     * global configuration download requests, '0.0.0.0' by default.
     */
    public static String getConfigurationProxyAddress() {
        return System.getProperty(CONFIGURATION_PROXY_ADDRESS, DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the interval in seconds at which proxy monitor agent collects monitoring data, '60' by default.
     */
    public static int getProxyParamsCollectingInterval() {
        return Integer.parseInt(System.getProperty(PROXY_PARAMS_COLLECTING_INTERVAL, "60"));
    }

    /**
     * @return proxy grpc port, {@link PortNumbers#PROXY_GRPC_PORT} by default.
     */
    public static int getProxyGrpcPort() {
        return Integer.getInteger(PROXY_GRPC_PORT, PortNumbers.PROXY_GRPC_PORT);
    }

    /**
     * @return environmental monitoring port, '2552' by default.
     */
    public static int getEnvMonitorPort() {
        return Integer.parseInt(System.getProperty(ENV_MONITOR_PORT, "2552"));
    }

    /**
     * @return enviroonmental monitoring limiting remote return data set, 'false' by default.
     */
    public static boolean getEnvMonitorLimitRemoteDataSet() {
        return Boolean.parseBoolean(System.getProperty(ENV_MONITOR_LIMIT_REMOTE_DATA_SET,
                DEFAULT_ENV_MONITOR_LIMIT_REMOTE_DATA_SET));
    }

    /**
     * @return system metrics sensor interval in seconds,'5' by default.
     */
    public static int getEnvMonitorSystemMetricsSensorInterval() {
        return Integer.parseInt(System.getProperty(ENV_MONITOR_SYSTEM_METRICS_SENSOR_INTERVAL, "5"));
    }

    /**
     * @return disk space sensor interval in seconds, '60' by default.
     */
    public static int getEnvMonitorDiskSpaceSensorInterval() {
        return Integer.parseInt(System.getProperty(ENV_MONITOR_DISK_SPACE_SENSOR_INTERVAL, "60"));
    }

    /**
     * @return exec listing sensor interval in seconds, '60' by default.
     */
    public static int getEnvMonitorExecListingSensorInterval() {
        return Integer.parseInt(System.getProperty(ENV_MONITOR_EXEC_LISTING_SENSOR_INTERVAL, "60"));
    }

    /**
     * @return exec listing sensor interval in seconds, 1 day by default.
     */
    public static int getEnvMonitorCertificateInfoSensorInterval() {
        return Integer.parseInt(System.getProperty(ENV_MONITOR_CERTIFICATE_INFO_SENSOR_INTERVAL, ONE_DAY_AS_SECONDS));
    }

    /**
     * @return path to the file containing network statistics,
     * '/proc/net/dev' by default.
     */
    public static String getNetStatsFile() {
        return System.getProperty(NET_STATS_FILE, "/proc/net/dev");
    }

    /**
     * This parameter may be set to false for cases where an external
     * component has already verified the certificate before sending the
     * request to client proxy.
     *
     * @return whether the client proxy should verify client's SSL certificate,
     * 'true' by default.
     */
    public static boolean shouldVerifyClientCert() {
        return Boolean.parseBoolean(System.getProperty(PROXY_VERIFY_CLIENT_CERT, TRUE));
    }

    /**
     * This parameter may be set to true for cases where there is a need to log client's SSL certificate.
     *
     * @return whether the client proxy should log client's SSL certificate,
     * 'false' by default.
     */
    public static boolean shouldLogClientCert() {
        return Boolean.parseBoolean(System.getProperty(PROXY_LOG_CLIENT_CERT, FALSE));
    }

    /**
     * @return the maximum number of allowed parallel connections that
     * Anti-Dos will let through to be processed, '5000' by default.
     */
    public static int getAntiDosMaxParallelConnections() {
        return Integer.parseInt(System.getProperty(ANTIDOS_MAX_PARALLEL_CONNECTIONS, "5000"));
    }

    /**
     * @return the maximum allowed CPU load value after which Anti-Dos will
     * start rejecting incoming connections (ignored if > 1.0),
     * '1.1' by default.
     */
    public static double getAntiDosMaxCpuLoad() {
        return Double.parseDouble(System.getProperty(ANTIDOS_MAX_CPU_LOAD, "1.1"));
    }

    /**
     * @return the minimum number of free file handles after which Anti-Dos will
     * start rejecting incoming connections, '100' by default.
     */
    public static int getAntiDosMinFreeFileHandles() {
        return Integer.parseInt(System.getProperty(ANTIDOS_MIN_FREE_FILEHANDLES, "100"));
    }

    /**
     * @return the maximum allowed JVM heap usage value after which Anti-Dos
     * will start rejecting incoming connections (ignored if > 1.0),
     * '1.1' by default.
     */
    public static double getAntiDosMaxHeapUsage() {
        return Double.parseDouble(System.getProperty(ANTIDOS_MAX_HEAP_USAGE, "1.1"));
    }

    /**
     * @return whether Anti-Dos should be used, 'true' by default.
     */
    public static boolean isAntiDosEnabled() {
        return Boolean.parseBoolean(System.getProperty(ANTIDOS_ENABLED, TRUE));
    }

    /**
     * Get proxy client's TLS protocols.
     *
     * @return protocols.
     */
    public static String[] getProxyClientTLSProtocols() {
        return System.getProperty(PROXY_CLIENT_TLS_PROTOCOLS, "TLSv1.2").trim().split(COMMA_SPLIT);
    }

    private static final String DEFAULT_CLIENT_SSL_CIPHER_SUITES = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,"
            + "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";

    /**
     * Get proxy client's accepted TLS cipher suites (between is and ss).
     *
     * @return cipher suites.
     */
    public static String[] getProxyClientTLSCipherSuites() {
        return System.getProperty(PROXY_CLIENT_TLS_CIPHERS, DEFAULT_CLIENT_SSL_CIPHER_SUITES).trim().split(COMMA_SPLIT);
    }

    private static final String DEFAULT_XROAD_SSL_CIPHER_SUITES = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384";

    /**
     * Get X-Road accepted TLS cipher suites (between ss and ss).
     *
     * @return cipher suites.
     */
    public static String[] getXroadTLSCipherSuites() {
        return System.getProperty(PROXY_XROAD_TLS_CIPHERS, DEFAULT_XROAD_SSL_CIPHER_SUITES).trim().split(COMMA_SPLIT);
    }

    /**
     * Tell whether token PIN policy should be enforced.
     *
     * @return true if PIN policy should be enforced.
     */
    public static boolean shouldEnforceTokenPinPolicy() {
        return Boolean.parseBoolean(System.getProperty(SIGNER_ENFORCE_TOKEN_PIN_POLICY,
                DEFAULT_SIGNER_ENFORCE_TOKEN_PIN_POLICY));
    }

    /**
     * @return the update interval in seconds at which server conf in cached, '60' by default
     */
    public static int getServerConfCachePeriod() {
        return Integer.parseInt(System.getProperty(SERVER_CONF_CACHE_PERIOD, "60"));
    }

    /**
     * @return the interval in seconds at which verifier caches results.
     * Max value is 180 seconds and cannot be exceeded in configuration.
     * Default is 60 s.
     */
    public static int getOcspVerifierCachePeriod() {
        int period = Integer.parseInt(System.getProperty(OCSP_VERIFIER_CACHE_PERIOD, "60"));
        return period < OCSP_VERIFIER_CACHE_PERIOD_MAX ? period : OCSP_VERIFIER_CACHE_PERIOD_MAX;
    }

    /**
     * @return serverproxy initial idle time (used until the request processing starts)
     */
    public static long getServerProxyConnectorInitialIdleTime() {
        return Integer.parseInt(System.getProperty(SERVERPROXY_CONNECTOR_INITIAL_IDLE_TIME,
                DEFAULT_PROXY_CONNECTOR_INITIAL_IDLE_TIME));
    }

    /**
     * @return the connection maximum idle time that should be set for server proxy connector
     */
    public static int getServerProxyConnectorMaxIdleTime() {
        return Integer.parseInt(System.getProperty(SERVERPROXY_CONNECTOR_MAX_IDLE_TIME,
                DEFAULT_SERVERPROXY_CONNECTOR_MAX_IDLE_TIME));
    }

    /**
     * @return the so_linger value in milliseconds that should be set for server proxy connector, -1 (disabled) by
     * default
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public static int getServerProxyConnectorSoLinger() {
        final int linger = Integer.parseInt(System.getProperty(SERVERPROXY_CONNECTOR_SO_LINGER,
                DEFAULT_SERVERPROXY_CONNECTOR_SO_LINGER));
        if (linger >= 0) return linger * 1000;
        return -1;
    }

    public static String getServerProxyMinSupportedClientVersion() {
        return System.getProperty(SERVERPROXY_MIN_SUPPORTED_CLIENT_VERSION);
    }

    /**
     * @return the connection maximum idle time that should be set for client proxy apache HttpClient
     */
    public static int getClientProxyHttpClientTimeout() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_HTTPCLIENT_TIMEOUT,
                DEFAULT_CLIENTPROXY_HTTPCLIENT_TIMEOUT));

    }

    /**
     * @return the so_linger value in seconds that should be set for client proxy apache HttpClient, -1 by default
     */
    public static int getClientProxyHttpClientSoLinger() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_HTTPCLIENT_SO_LINGER,
                DEFAULT_CLIENTPROXY_HTTPCLIENT_SO_LINGER));
    }

    /**
     * @return the so_linger value in seconds that should be set for client proxy connector, 0 by default
     */
    public static int getClientProxyConnectorSoLinger() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_CONNECTOR_SO_LINGER,
                DEFAULT_CLIENTPROXY_CONNECTOR_SO_LINGER));
    }

    /**
     * @return clientproxy initial idle time (used until the request processing starts)
     */
    public static long getClientProxyConnectorInitialIdleTime() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_CONNECTOR_INITIAL_IDLE_TIME,
                DEFAULT_PROXY_CONNECTOR_INITIAL_IDLE_TIME));
    }

    /**
     * @return the connection maximum idle time that should be set for client proxy connector
     */
    public static int getClientProxyConnectorMaxIdleTime() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME,
                DEFAULT_CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME));

    }

    /**
     * @return true if the idle connection monitor thread should be used for client proxy
     */
    public static boolean isClientUseIdleConnectionMonitor() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR,
                DEFAULT_CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR));
    }

    /**
     * @return the interval at which pooled idle connections should be cleaned up by the connection monitor
     */
    public static int getClientProxyIdleConnectionMonitorInterval() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL,
                DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL));
    }

    /**
     * @return the idle time after which pooled connections should be discarded
     */
    public static int getClientProxyIdleConnectionMonitorIdleTime() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME,
                DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME));
    }

    public static boolean isEnableClientProxyPooledConnectionReuse() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_POOL_REUSE_CONNECTIONS,
                DEFAULT_CLIENTPROXY_POOL_REUSE_CONNECTIONS));
    }

    public static boolean isServerProxySupportClientsPooledConnections() {
        return Boolean.parseBoolean(System.getProperty(SERVERPROXY_SUPPORT_CLIENTS_POOLED_CONNECTIONS,
                DEFAULT_SERVERPROXY_SUPPORT_CLIENTS_POOLED_CONNECTIONS));
    }

    public static int getClientProxyPoolTotalMaxConnections() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS,
                DEFAULT_CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS));
    }

    public static int getClientProxyPoolDefaultMaxConnectionsPerRoute() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE,
                DEFAULT_CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE));
    }

    /**
     * @return true if SSL sockets should close the underlying socket layer when the SSL socket is closed
     */
    public static boolean isUseSslSocketAutoClose() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE,
                DEFAULT_CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE));
    }

    /**
     * @return period in seconds the fastest provider uri should be cached, or 0 to disable
     */
    public static int getClientProxyFastestConnectingSslUriCachePeriod() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD,
                DEFAULT_CLIENTPROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD));
    }

    /**
     * @return the time in milliseconds, after which connections in a pool should be check for validity, ie.
     * after this time, check if pooled connections are still alive, don't just assume they are.
     * Non-positive value disables connection validation. '2000' by default.
     */
    public static int getClientProxyValidatePoolConnectionsAfterInactivityMs() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS,
                DEFAULT_CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS));
    }

    /**
     * @return the {@link #NODE_TYPE} in a cluster for this Server.
     */
    public static NodeType getServerNodeType() {
        return NodeType.fromStringIgnoreCaseOrReturnDefault(System.getProperty(NODE_TYPE));
    }

    public static boolean isHealthCheckEnabled() {
        return getHealthCheckPort() > 0;
    }

    public static String getHealthCheckInterface() {
        return System.getProperty(PROXY_HEALTH_CHECK_INTERFACE, DEFAULT_PROXY_HEALTH_CHECK_INTERFACE);
    }

    public static int getHealthCheckPort() {
        return Integer.parseInt(System.getProperty(PROXY_HEALTH_CHECK_PORT,
                DEFAULT_PROXY_HEALTH_CHECK_PORT));
    }

    /**
     * @return minimum central server global configuration version or default
     */
    public static int getMinimumCentralServerGlobalConfigurationVersion() {
        // read the setting
        int minVersion = Integer.parseInt(System.getProperty(MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION));
        // check that it is a valid looking version number
        checkVersionValidity(minVersion, CURRENT_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION);
        // ignore the versions that are no longer supported
        if (minVersion < MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION) {
            minVersion = MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION;
        }
        return minVersion;
    }

    /**
     * @return minimum configuration proxy global configuration version or default
     */
    public static int getMinimumConfigurationProxyGlobalConfigurationVersion() {
        // read the setting
        int minVersion = Integer.parseInt(System.getProperty(
                MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION));
        // check that it is a valid looking version number
        checkVersionValidity(minVersion, CURRENT_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION);
        // ignore the versions that are no longer supported
        if (minVersion < MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION) {
            minVersion = MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION;
        }
        return minVersion;
    }

    /**
     * @return Serverconf client cache size
     */
    public static long getServerConfClientCacheSize() {
        return Long.getLong(SERVER_CONF_CLIENT_CACHE_SIZE, 100);
    }

    /**
     * @return Serverconf service cache size
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public static long getServerConfServiceCacheSize() {
        return Long.getLong(SERVER_CONF_SERVICE_CACHE_SIZE, 1000);
    }

    /**
     * @return Serverconf access right cache size
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public static long getServerConfAclCacheSize() {
        return Long.getLong(SERVER_CONF_ACL_CACHE_SIZE, 100_000);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static long getServerConfServiceEndpointsCacheSize() {
        return Long.getLong(SERVER_CONF_SERVICE_ENDPOINTS_CACHE_SIZE, 100_000);
    }

    private static void checkVersionValidity(int min, int current, String defaultVersion) {
        if (min > current || min < 1) {
            throw new IllegalArgumentException("Illegal minimum global configuration version in system parameters");
        }
    }

    /**
     * @return Whether to throw an exception about expired or not yet valid certificates, 'false' by default..
     */
    public static boolean isClientIsCertValidityPeriodCheckEnforced() {
        return Boolean.parseBoolean(System.getProperty(ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK,
                DEFAULT_ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK));
    }

    /**
     * @return Whether encryption to security server backup files using server's OpenPGP key is enabled,
     * 'false' by default.
     */
    public static boolean isBackupEncryptionEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROXY_BACKUP_ENCRYPTION_ENABLED,
                DEFAULT_PROXY_BACKUP_ENCRYPTED));
    }

    /**
     * @return Comma-separated list of additional recipient OpenPGP key identifiers
     */
    public static String getBackupEncryptionKeyIds() {
        return System.getProperty(PROXY_BACKUP_ENCRYPTION_KEY_IDS, "");
    }

    /**
     * @return Whether Hardware Security Modules Healthcheck is enabled
     * 'false' by default
     */
    public static boolean isHSMHealthCheckEnabled() {
        return Boolean.parseBoolean(System.getProperty(HSM_HEALTH_CHECK_ENABLED, DEFAULT_HSM_HEALTH_CHECK_ENABLED));
    }

    /**
     * @return Digest name used for signing proxy messages
     * 'SHA-512' by default
     */
    public static DigestAlgorithm getProxyMessageSignDigestName() {
        return Optional.ofNullable(System.getProperty(PROXY_MESSAGE_SIGN_DIGEST_NAME))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    public static Long getProxyMemoryUsageThreshold() {
        return Optional.ofNullable(System.getProperty(PROXY_MEMORY_USAGE_THRESHOLD))
                .map(Long::parseLong)
                .orElse(null);
    }

    /**
     * @return gRPC signer host.
     */
    public static String getGrpcInternalHost() {
        return System.getProperty(GRPC_INTERNAL_HOST, "127.0.0.1");
    }

    /**
     * @return gRPC signer host.
     */
    public static boolean isGrpcInternalTlsEnabled() {
        return Boolean.parseBoolean(System.getProperty(GRPC_INTERNAL_TLS_ENABLED, Boolean.TRUE.toString()));
    }

    /**
     * @return gRPC signer host.
     */
    public static int getGrpcSignerPort() {
        return Integer.parseInt(System.getProperty(GRPC_SIGNER_PORT, String.valueOf(PortNumbers.SIGNER_GRPC_PORT)));
    }

    /**
     * @return gRPC internal key store path. Uses JKS format.
     */
    public static String getGrpcInternalKeyStore() {
        return System.getProperty(GRPC_INTERNAL_KEYSTORE, "/var/run/xroad/xroad-grpc-internal-keystore.p12");
    }

    /**
     * @return gRPC internal key store password.
     */
    public static String getGrpcInternalKeyStorePassword() {
        return System.getProperty(GRPC_INTERNAL_KEYSTORE_PASSWORD, System.getenv().get(GRPC_INTERNAL_KEYSTORE_PASSWORD_ENV));
    }

    /**
     * @return gRPC internal trust store path. Uses JKS format.
     */
    public static String getGrpcInternalTrustStore() {
        return System.getProperty(GRPC_INTERNAL_TRUSTSTORE, "/var/run/xroad/xroad-grpc-internal-keystore.p12");
    }

    /**
     * @return gRPC internal trust store path password.
     */
    public static String getGrpcInternalTruststorePassword() {
        return System.getProperty(GRPC_INTERNAL_TRUSTSTORE_PASSWORD, System.getenv().get(GRPC_INTERNAL_TRUSTSTORE_PASSWORD_ENV));
    }

    /**
     * @return GlobalConf scheduled refresh rate in seconds
     */
    public static String getGlobalConfRefreshRateSeconds() {
        return System.getProperty(GLOBAL_CONF_REFRESH_RATE_SECONDS, "60");
    }

    private static String propertyNameToEnvVariable(String propName) {
        return propName.toUpperCase().replaceAll("[.-]", "_");
    }
}
