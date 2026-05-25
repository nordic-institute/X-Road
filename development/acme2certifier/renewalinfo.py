# -*- coding: utf-8 -*-
"""Renewalinfo class: ACME renewal info handler with separated config and repository helpers."""
from __future__ import print_function
from typing import Dict
from dataclasses import dataclass
from acme_srv.db_handler import DBstore
from acme_srv.message import Message
from acme_srv.helper import (
    string_sanitize,
    certid_hex_get,
    uts_to_date_utc,
    error_dic_get,
    load_config,
    ca_handler_load,
    uts_now,
    cert_serial_get,
    cert_aki_get,
    b64_url_recode,
    b64_decode,
)


@dataclass
class RenewalinfoConfig:
    """configuration dataclass for Renewalinfo handler"""

    renewal_force: bool = False
    renewalthreshold_pctg: float = 85.0
    retry_after_timeout: int = 86400
    renewalinfo_lookup: bool = False


class RenewalinfoRepository:
    """Renewalinfo repository helper with database access methods."""

    def __init__(self, dbstore, logger):
        self.dbstore = dbstore
        self.logger = logger

    def get_certificate_by_certid(self, certid_hex):
        """Retrieve certificate by certid from database."""
        self.logger.debug(
            "RenewalinfoRepository.get_certificate_by_certid(%s)", certid_hex
        )
        try:
            return self.dbstore.certificate_lookup(
                "renewal_info",
                certid_hex,
                (
                    "id",
                    "name",
                    "cert",
                    "cert_raw",
                    "expire_uts",
                    "issue_uts",
                    "created_at",
                ),
            )
        except Exception as err_:
            self.logger.critical(
                "Database error: failed to look up certificate for renewal info (draft01): %s",
                err_,
            )
            return None

    def get_certificates_by_serial(self, serial):
        """Retrieve certificates by serial from database."""
        self.logger.debug(
            "RenewalinfoRepository.get_certificates_by_serial(%s)", serial
        )
        try:
            return self.dbstore.certificates_search(
                "serial",
                serial,
                operant="is",
                vlist=[
                    "id",
                    "name",
                    "cert",
                    "cert_raw",
                    "expire_uts",
                    "issue_uts",
                    "aki",
                    "created_at",
                ],
            )
        except Exception as err_:
            self.logger.critical(
                "Database error: failed to look up certificate for renewal info (draft02): %s",
                err_,
            )
            return []

    def add_certificate(self, data_dic):
        """Add or update certificate in database."""
        self.logger.debug("RenewalinfoRepository.add_certificate()")
        return self.dbstore.certificate_add(data_dic)

    def get_housekeeping_param(self, name):
        """Retrieve housekeeping parameter by name from database."""
        self.logger.debug("RenewalinfoRepository.get_housekeeping_param(%s)", name)
        return self.dbstore.hkparameter_get(name)

    def add_housekeeping_param(self, param):
        """Add or update housekeeping parameter in database."""
        self.logger.debug("RenewalinfoRepository.add_housekeeping_param()")
        return self.dbstore.hkparameter_add(param)


class Renewalinfo(object):
    """Renewalinfo handler with business logic, config, and repository helpers."""

    def __init__(
        self, debug: bool = False, srv_name: str = None, logger: object = None
    ):
        self.debug = debug
        self.logger = logger
        self.server_name = srv_name
        self.path_dic = {"renewalinfo": "/acme/renewal-info/"}
        self.dbstore = DBstore(self.debug, self.logger)
        self.message = Message(self.debug, self.server_name, self.logger)
        self.err_msg_dic = error_dic_get(self.logger)
        self.config = RenewalinfoConfig()
        self.repository = RenewalinfoRepository(self.dbstore, self.logger)
        self.cahandler = None

    def _load_configuration(self):
        """Load renewalinfo configuration from file (harmonized approach)"""
        self.logger.debug("Renewalinfo._load_configuration()")

        config_dic = load_config()

        if "Renewalinfo" in config_dic:
            try:
                self.config.renewal_force = config_dic.getboolean(
                    "Renewalinfo", "renewal_force", fallback=False
                )
            except Exception as err_:
                self.logger.error("renewal_force parsing error: %s", err_)
                self.config.renewal_force = False
            try:
                self.config.renewalthreshold_pctg = float(
                    config_dic.get(
                        "Renewalinfo", "renewalthreshold_pctg", fallback=85.0
                    )
                )
            except Exception as err_:
                self.logger.error("renewalthreshold_pctg parsing error: %s", err_)
                self.config.renewalthreshold_pctg = 85.0
            try:
                self.config.retry_after_timeout = int(
                    config_dic.get("Renewalinfo", "retry_after_timeout", fallback=86400)
                )
            except Exception as err_:
                self.logger.error("retry_after_timeout parsing error: %s", err_)
                self.config.retry_after_timeout = 86400

        self._load_ca_handler(config_dic)
        self._parse_cahandler_section(config_dic)

        self.logger.debug("Renewalinfo._load_configuration() ended.")

    def _parse_cahandler_section(self, config_dic: object) -> None:
        """Parse the [CAHandler] section for ACME URL and profile sync settings."""
        self.logger.debug("Directory._parse_cahandler_section()")
        if "CAhandler" in config_dic:
            cfg_dic = dict(config_dic["CAhandler"])
            self.config.acme_url = cfg_dic.get("acme_url", None)

            try:
                self.config.renewalinfo_lookup = config_dic.getboolean(
                    "CAhandler", "renewalinfo_lookup", fallback=False
                )
            except Exception as err_:
                self.logger.error("renewalinfo_lookup parsing error: %s", err_)
                self.config.renewalinfo_lookup = False

        if self.config.renewalinfo_lookup and not self.config.acme_url:
            self.logger.error("CAhandler section incomplete for renewalinfo lookup")
            self.config.renewalinfo_lookup = False

        self.logger.debug("Directory._parse_cahandler_section() ended")

    def _load_ca_handler(self, config_dic: object) -> None:
        """Load the CA handler module as configured."""
        ca_handler_module = ca_handler_load(self.logger, config_dic)
        if ca_handler_module:
            self.cahandler = ca_handler_module.CAhandler
        else:
            self.logger.critical("No ca_handler loaded")

    def __enter__(self):
        self._load_configuration()
        return self

    def __exit__(self, *args):
        pass

    # --- Business logic methods ---

    def _lookup_certificate_by_renewalinfo(
        self, renewalinfo_string: str
    ) -> Dict[str, str]:
        self.logger.debug(
            "Renewalinfo._lookup_certificate_by_renewalinfo(%s)", renewalinfo_string
        )
        if "." in renewalinfo_string:
            serial, aki = self._extract_serial_and_aki_from_string(renewalinfo_string)
            cert_dic = self._lookup_certificate_by_serial_and_aki(serial, aki)
        else:
            _mda, certid_hex = certid_hex_get(self.logger, renewalinfo_string)
            cert_dic = self._lookup_certificate_by_certid(certid_hex)
        self.logger.debug(
            "Renewalinfo._lookup_certificate_by_renewalinfo(%s) - ended with: %s",
            renewalinfo_string,
            bool(cert_dic),
        )
        return cert_dic

    def _update_certificate_table_with_serial_and_aki(self):
        self.logger.debug("Renewalinfo._update_certificate_table_with_serial_and_aki()")
        try:
            certificate_list = self.dbstore.certificates_search(
                "serial",
                None,
                operant="is",
                vlist=["id", "name", "cert", "cert_raw", "serial", "aki"],
            )
        except Exception as err_:
            self.logger.critical(
                "Database error: failed to retrieve certificate list for renewal info update: %s",
                err_,
            )
            certificate_list = []
        update_cnt = 0
        for cert in certificate_list:
            if (
                "cert_raw" in cert
                and cert["cert_raw"]
                and "name" in cert
                and "cert" in cert
            ):
                serial = cert_serial_get(self.logger, cert["cert_raw"], hexformat=True)
                aki = cert_aki_get(self.logger, cert["cert_raw"])
                data_dic = {
                    "serial": serial,
                    "aki": aki,
                    "name": cert["name"],
                    "cert_raw": cert["cert_raw"],
                    "cert": cert["cert"],
                }
                self.repository.add_certificate(data_dic)
                update_cnt += 1
        self.logger.debug(
            "Renewalinfo._update_certificate_table_with_serial_and_aki(%s) - done",
            update_cnt,
        )

    def _lookup_certificate_by_certid(self, certid_hex: str) -> Dict[str, str]:
        self.logger.debug("Renewalinfo._lookup_certificate_by_certid()")
        return self.repository.get_certificate_by_certid(certid_hex)

    def _lookup_certificate_by_serial_and_aki(
        self, serial: str, aki: str
    ) -> Dict[str, str]:
        self.logger.debug("Renewalinfo._lookup_certificate_by_serial_and_aki()")
        cert_dic = {}
        cert_list = self.repository.get_certificates_by_serial(serial)
        if not cert_list and serial and serial.startswith("0"):
            cert_list = self.repository.get_certificates_by_serial(serial.lstrip("0"))
        for cert in cert_list:
            if cert.get("aki") == aki:
                cert_dic = cert
                break
        self.logger.debug(
            "Renewalinfo._lookup_certificate_by_serial_and_aki() ended with: %s",
            bool(cert_dic),
        )
        return cert_dic

    def _generate_renewalinfo_window(self, cert_dic: Dict[str, str]) -> Dict[str, str]:
        self.logger.debug("Renewalinfo._generate_renewalinfo_window()")
        if "expire_uts" in cert_dic and cert_dic["expire_uts"]:
            if "issue_uts" not in cert_dic or not cert_dic["issue_uts"]:
                cert_dic["issue_uts"] = uts_now()
            if self.config.renewal_force:
                self.logger.debug("Renewalinfo.get() - force renewal")
                cert_dic["expire_uts"] = uts_now() + 86400
                start_uts = int(cert_dic["expire_uts"] - (365 * 86400))
            else:
                start_uts = (
                    int(
                        (cert_dic["expire_uts"] - cert_dic["issue_uts"])
                        * self.config.renewalthreshold_pctg
                        / 100
                    )
                    + cert_dic["issue_uts"]
                )
            renewalinfo_dic = {
                "suggestedWindow": {
                    "start": uts_to_date_utc(start_uts),
                    "end": uts_to_date_utc(cert_dic["expire_uts"]),
                }
            }
        else:
            renewalinfo_dic = {}
        self.logger.debug("Renewalinfo._generate_renewalinfo_window() ended")
        return renewalinfo_dic

    def _get_renewalinfo_data(self, renewalinfo_string: str) -> Dict[str, str]:
        self.logger.debug("Renewalinfo._get_renewalinfo_data()")
        cert_dic = self._lookup_certificate_by_renewalinfo(renewalinfo_string)
        renewalinfo_dic = self._generate_renewalinfo_window(cert_dic)
        self.logger.debug(
            "Renewalinfo._get_renewalinfo_data() ended with: %s", renewalinfo_dic
        )
        return renewalinfo_dic

    def _parse_renewalinfo_string_from_url(self, url: str) -> str:
        self.logger.debug("Renewalinfo._parse_renewalinfo_string_from_url()")
        url = url.replace(
            f'{self.server_name}{self.path_dic["renewalinfo"].rstrip("/")}', ""
        )
        url = url.lstrip("/")
        renewalinfo_string = string_sanitize(self.logger, url)
        self.logger.debug(
            "Renewalinfo._parse_renewalinfo_string_from_url() - renewalinfo_string: %s",
            renewalinfo_string,
        )
        return renewalinfo_string

    def _extract_serial_and_aki_from_string(
        self, renewalinfo_string: str
    ) -> (str, str):
        self.logger.debug("Renewalinfo._extract_serial_and_aki_from_string()")
        renewalinfo_list = renewalinfo_string.split(".")
        if len(renewalinfo_list) == 2:
            serial = b64_decode(
                self.logger, b64_url_recode(self.logger, renewalinfo_list[1])
            ).hex()
            aki = b64_decode(
                self.logger, b64_url_recode(self.logger, renewalinfo_list[0])
            ).hex()
        else:
            serial = None
            aki = None
        self.logger.debug(
            "Renewalinfo._extract_serial_and_aki_from_string() - serial: %s, aki: %s",
            serial,
            aki,
        )
        return (serial, aki)

    def get(self, url: str) -> Dict[str, str]:
        """Get renewal information (backwards compatible public method)"""
        self.logger.debug("Renewalinfo.get()")

        renewalinfo_string = self._parse_renewalinfo_string_from_url(url)
        if self.config.renewalinfo_lookup and hasattr(
            self.cahandler, "lookup_renewalinfo"
        ):
            with self.cahandler(None, self.logger) as ca_handler:
                # get renewal info from CA handler
                rc_code, renewalinfo_dic = ca_handler.lookup_renewalinfo(
                    self.config.acme_url, renewalinfo_string
                )
        else:
            # ensure serial and aki fields are populated in certificate table
            if not self.repository.get_housekeeping_param("cert_aki_serial_update"):
                self._update_certificate_table_with_serial_and_aki()
                self.logger.debug("Renewalinfo.get() - update housekeeping")
                self.repository.add_housekeeping_param(
                    {"name": "cert_aki_serial_update", "value": True}
                )

            try:
                renewalinfo_dic = self._get_renewalinfo_data(renewalinfo_string)
                rc_code = 200 if renewalinfo_dic else 404
            except Exception as err_:
                self.logger.error("Error when getting renewal information: %s", err_)
                renewalinfo_dic = {}
                rc_code = 400
        response_dic = {"code": rc_code}
        if renewalinfo_dic:
            response_dic["data"] = renewalinfo_dic
            response_dic["header"] = {
                "Retry-After": f"{self.config.retry_after_timeout}"
            }
        else:
            response_dic["data"] = self.err_msg_dic["malformed"]
        return response_dic

    def update(self, content: str) -> Dict[str, str]:
        """Update renewal info (backwards compatible public method)"""
        self.logger.debug("Renewalinfo.update()")
        (
            code,
            _message,
            _detail,
            _protected,
            payload,
            _account_name,
        ) = self.message.check(content)
        response_dic = {}
        if code == 200 and "certid" in payload and "replaced" in payload:
            cert_dic = self._lookup_certificate_by_renewalinfo(payload["certid"])
            if cert_dic and payload["replaced"]:
                cert_dic["replaced"] = True
                cert_id = self.repository.add_certificate(cert_dic)
                response_dic["code"] = 200 if cert_id else 400
            else:
                response_dic["code"] = 400
        else:
            response_dic["code"] = 400
        return response_dic
