# Central Server Registration Web Service

This central server component provides management service SOAP interface for authentication certificate
registration requests from security servers. It validates the requests, and calls the central server admin service API
_management-requests_ on behalf of the security servers.

When deployed, the component depends on the global configuration (configuration client) and network access to the
central server admin service API.
