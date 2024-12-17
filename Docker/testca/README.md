# Test CA Docker image

This is a simplified CA instance for testing purposes. It is not meant for production use.
It is based on the ansible test-ca for which more information can be found [here](../../ansible/TESTCA.md). The restart to the ACME server is done somewhat differently compared to the ansible version: `supervisorctl restart uwsgi`.
ACME logs are seen in /var/log/supervisor/uwsgi-stderr*. 

It is also part of the [xrd-dev-stack](../xrd-dev-stack/README.md).
