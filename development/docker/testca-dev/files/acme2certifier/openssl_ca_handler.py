# -*- coding: utf-8 -*-
""" handler for an openssl ca, updated version of https://github.com/grindsa/acme2certifier/blob/master/examples/ca_handler/openssl_ca_handler.py
 with added support for eab profiling, header info field and modifying openssl's index and serial files on enrollment """
from __future__ import print_function
import os
import datetime
import json
from typing import List, Tuple, Dict
import base64
import uuid
import re
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.x509 import BasicConstraints, ExtendedKeyUsage, SubjectKeyIdentifier, AuthorityKeyIdentifier, KeyUsage, SubjectAlternativeName
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID
# pylint: disable=e0401
from acme_srv.helper import load_config, build_pem_file, uts_now, uts_to_date_utc, b64_url_recode, cert_serial_get, \
  convert_string_to_byte, convert_byte_to_string, csr_cn_get, csr_san_get, header_info_get, \
  eab_profile_header_info_check, config_headerinfo_load, config_eab_profile_load


class CAhandler(object):
  """ CA  handler """

  def __init__(self, debug: bool = False, logger: object = None):
    self.debug = debug
    self.logger = logger
    self.issuer_dict = {
      'issuing_ca_key': None,
      'issuing_ca_cert': None,
      'issuing_ca_crl': None,
    }
    self.ca_cert_chain_list = []
    self.cert_validity_days = 365
    self.cert_validity_adjust = False
    self.openssl_conf = None
    self.cert_save_path = None
    self.cert_db_index_file = None
    self.cert_serial_file = None
    self.save_cert_as_hex = False
    self.allowed_domainlist = []
    self.blocked_domainlist = []
    self.cn_enforce = False
    self.profile_id = None
    self.header_info_field = False
    self.eab_handler = None
    self.eab_profiling = False

  def __enter__(self):
    """ Makes ACMEHandler a Context Manager """
    if not self.issuer_dict['issuing_ca_key']:
      self._config_load()
    return self

  def __exit__(self, *args):
    """ cose the connection at the end of the context """

  def _ca_load(self) -> Tuple[object, object]:
    """ load ca key and cert """
    self.logger.debug('CAhandler._ca_load()')
    ca_key = None
    ca_cert = None
    # open key and cert
    if 'issuing_ca_key' in self.issuer_dict:
      if os.path.exists(self.issuer_dict['issuing_ca_key']):
        with open(self.issuer_dict['issuing_ca_key'], 'rb') as fso:
          ca_key = serialization.load_pem_private_key(
            fso.read(),
            password=self.issuer_dict.get('passphrase', None),
            backend=default_backend()
          )
    if 'issuing_ca_cert' in self.issuer_dict:
      if os.path.exists(self.issuer_dict['issuing_ca_cert']):
        with open(self.issuer_dict['issuing_ca_cert'], 'rb') as fso:
          ca_cert = x509.load_pem_x509_certificate(
            fso.read(),
            backend=default_backend()
          )
    self.logger.debug('CAhandler._ca_load() ended')
    return (ca_key, ca_cert)

  def _cert_extension_ku_parse(self, ext: str) -> Dict[str, str]:
    self.logger.debug('CAhandler._cert_extension_ku_parse()')

    template_dic = {'digital_signature': False, 'content_commitment': False, 'key_encipherment': False, 'data_encipherment': False, 'key_agreement': False, 'key_cert_sign': False, 'crl_sign': False, 'encipher_only': False, 'decipher_only': False}
    ku_mapping_dic = {
      'digitalsignature': 'digital_signature',
      'nonrepudiation': 'content_commitment',
      'keyencipherment': 'key_encipherment',
      'dataencipherment': 'data_encipherment',
      'keyagreement': 'key_agreement',
      'keycertsign': 'key_cert_sign',
      'crlsign': 'crl_sign',
      'encipheronly': 'encipher_only',
      'decipheronly': 'decipher_only',
    }
    for attribute in ext.split(','):
      if attribute.strip().lower() in ku_mapping_dic:
        self.logger.debug('CAhandler._cert_extension_ku_parse(): found {0}'.format(attribute))
        template_dic[ku_mapping_dic[attribute.strip().lower()]] = True

    self.logger.debug('CAhandler._cert_extension_ku_parse() ended')
    return template_dic

  def _cert_extension_eku_parse(self, ext: str) -> List[str]:
    self.logger.debug('CAhandler._cert_extension_eku_parse()')

    # eku included in tempalate
    eku_mapping_dic = {
      'clientauth': ExtendedKeyUsageOID.CLIENT_AUTH,
      'serverauth': ExtendedKeyUsageOID.SERVER_AUTH,
      'codesigning': ExtendedKeyUsageOID.CODE_SIGNING,
      'emailprotection': ExtendedKeyUsageOID.EMAIL_PROTECTION,
      'timestamping': ExtendedKeyUsageOID.TIME_STAMPING,
      'ocspsigning': ExtendedKeyUsageOID.OCSP_SIGNING,
      'ekeyuse': 'eKeyUse'  # this is just for testing
    }

    # backwards compatibility with cryptography module coming Ubuntu 22.04
    if hasattr(ExtendedKeyUsageOID, 'KERBEROS_PKINIT_KDC'):
      eku_mapping_dic['pkInitKDC'] = ExtendedKeyUsageOID.KERBEROS_PKINIT_KDC

    eku_list = []
    for attribute in ext.split(','):
      if attribute.strip().lower() in eku_mapping_dic:
        self.logger.debug('CAhandler._cert_extension_eku_parse(): found {0}'.format(attribute))
        eku_list.append(eku_mapping_dic[attribute.strip().lower()])

    self.logger.debug('CAhandler._cert_extension_eku_parse() ended')
    return eku_list

  def _cert_extension_dic_parse(self, cert_extension_dic: Dict[str, str], cert: str, ca_cert: str) -> List[object]:
    """ parse certificate exteions loaded from config file """
    self.logger.debug('CAhandler.cert_extesion_dic_parse()')

    extension_list = []
    for ext_name, ext in cert_extension_dic.items():
      _tmp_dic = {'critical': ext['critical']}

      if ext_name.lower() == 'basicconstraints':
        self.logger.info('CAhandler.cert_extesion_dic_parse(): basicConstraints')
        _tmp_dic['name'] = BasicConstraints(ca=False, path_length=None)
      elif ext_name.lower() == 'subjectkeyidentifier':
        self.logger.info('CAhandler.cert_extesion_dic_parse(): subjectKeyIdentifier')
        _tmp_dic['name'] = SubjectKeyIdentifier.from_public_key(cert.public_key())
      elif ext_name.lower() == 'authoritykeyidentifier':
        self.logger.info('CAhandler.cert_extesion_dic_parse(): authorityKeyIdentifier')
        _tmp_dic['name'] = AuthorityKeyIdentifier.from_issuer_public_key(ca_cert.public_key())
      elif ext_name.lower() == 'keyusage':
        self.logger.info('CAhandler.cert_extesion_dic_parse(): keyUsage')
        _tmp_dic['name'] = KeyUsage(**self._cert_extension_ku_parse(ext['value']))
      elif ext_name.lower() == 'extendedkeyusage':
        self.logger.info('CAhandler.cert_extesion_dic_parse(): extendedKeyUsage')
        _tmp_dic['name'] = ExtendedKeyUsage(self._cert_extension_eku_parse(ext['value']))

      extension_list.append(_tmp_dic)

    self.logger.debug('CAhandler.cert_extesion_dic_parse() ended.')
    return extension_list

  def _certificate_extensions_load(self) -> Dict[str, str]:
    """ verify certificate chain """
    self.logger.debug('CAhandler._certificate_extensions_load()')

    file_dic = dict(load_config(self.logger, cfg_file=self.openssl_conf))

    extensions_block_name = 'extensions'
    if self.profile_id:
      extensions_block_name = 'extensions_' + self.profile_id

    cert_extention_dic = {}
    if extensions_block_name in file_dic:
      for extension in file_dic[extensions_block_name]:

        cert_extention_dic[extension] = {}
        parameters = file_dic[extensions_block_name][extension].split(',')

        # set crititcal task if applicable
        if parameters[0] == 'critical':
          cert_extention_dic[extension]['critical'] = bool(parameters.pop(0))
        else:
          cert_extention_dic[extension]['critical'] = False

        # remove leading blank from first element
        parameters[0] = parameters[0].lstrip()

        # check if we have an issuer option (if so remove it and mark it as to be set)
        if 'issuer:' in parameters[-1]:
          cert_extention_dic[extension]['issuer'] = bool(parameters.pop(-1))

        # check if we have an issuer option (if so remove it and mark it as to be set)
        if 'subject:' in parameters[-1]:
          cert_extention_dic[extension]['subject'] = bool(parameters.pop(-1))

        # combine the remaining items and put them in as values
        cert_extention_dic[extension]['value'] = ','.join(parameters)

    self.logger.debug('CAhandler._certificate_extensions_load() ended')
    return cert_extention_dic

  def _certificate_store(self, cert: object):
    """ store certificate on disk """
    self.logger.debug('CAhandler._certificate_store()')
    serial = cert.serial_number

    # save cert if needed
    if self.cert_save_path and self.cert_save_path is not None:
      # create cert-store dir if not existing
      if not os.path.isdir(self.cert_save_path):
        self.logger.debug('create certsavedir {0}'.format(self.cert_save_path))
        os.mkdir(self.cert_save_path)

      # determine filename
      if self.save_cert_as_hex:
        serial_formatted = self.serial_in_hex_format(serial)
        self.logger.info('convert serial to hex: {0}: {1}'.format(serial, serial_formatted))
        cert_file = serial_formatted
      else:
        cert_file = str(serial)
      with open('{0}/{1}.pem'.format(self.cert_save_path, cert_file), 'wb') as fso:
        fso.write(cert.public_bytes(serialization.Encoding.PEM))
    else:
      self.logger.error('CAhandler._certificate_store() handler configuration incomplete: cert_save_path is missing')

    self.logger.debug('CAhandler._certificate_store() ended')

  def serial_in_hex_format(self, serial):
    serial_formatted = f'{serial:X}'
    return serial_formatted.zfill(len(serial_formatted) + len(serial_formatted) % 2)

  def _config_check_issuer(self) -> str:
    """ check issuing CA configuration """
    self.logger.debug('CAhandler._config_check_issuer()')

    error = None
    if 'issuing_ca_key' in self.issuer_dict and self.issuer_dict['issuing_ca_key']:
      if not os.path.exists(self.issuer_dict['issuing_ca_key']):
        error = 'issuing_ca_key {0} does not exist'.format(self.issuer_dict['issuing_ca_key'])
    else:
      error = 'issuing_ca_key not specfied in config_file'

    if not error:
      if 'issuing_ca_cert' in self.issuer_dict and self.issuer_dict['issuing_ca_cert']:
        if not os.path.exists(self.issuer_dict['issuing_ca_cert']):
          error = 'issuing_ca_cert {0} does not exist'.format(self.issuer_dict['issuing_ca_cert'])
      else:
        error = 'issuing_ca_cert must be specified in config file'

    self.logger.debug('CAhandler._config_check_issuer() ended with:  {0}'.format(error))
    return error

  def _config_check_crl(self, error: str = None) -> str:
    """ check crl config """
    self.logger.debug('CAhandler._config_check_crl()')

    if not error:
      if 'issuing_ca_crl' in self.issuer_dict and self.issuer_dict['issuing_ca_crl']:
        if not os.path.exists(self.issuer_dict['issuing_ca_crl']):
          self.logger.info('CAhandler._config_check_crl(): issuing_ca_crl {0} does not exist.'.format(self.issuer_dict['issuing_ca_crl']))
      else:
        error = 'issuing_ca_crl must be specified in config file'

    self.logger.debug('CAhandler._config_check_crl() ended with:  {0}'.format(error))
    return error

  def _config_parameters_check(self, error: str = None) -> str:
    """ check remaining configuration """
    self.logger.debug('CAhandler._config_parameters_check()')

    if not error:
      if self.cert_save_path:
        if not os.path.exists(self.cert_save_path):
          error = 'cert_save_path {0} does not exist'.format(self.cert_save_path)
      else:
        error = 'cert_save_path must be specified in config file'

    if not error and self.openssl_conf and not os.path.exists(self.openssl_conf):
      error = 'openssl_conf {0} does not exist'.format(self.openssl_conf)

    if not error and not self.ca_cert_chain_list:
      error = 'ca_cert_chain_list must be specified in config file'

    self.logger.debug('CAhandler._config_parameters_check() ended with:  {0}'.format(error))
    return error

  def _config_check(self) -> str:
    """ check config for consitency """
    self.logger.debug('CAhandler._config_check()')

    # run checks
    error = self._config_check_issuer()
    error = self._config_check_crl(error)
    error = self._config_parameters_check(error)

    if error:
      self.logger.error('CAhandler config error: {0}'.format(error))

    self.logger.debug('CAhandler._config_check() ended')
    return error

  def _config_domainlists_load(self, config_dic: Dict[str, str]):
    """" load config from file """
    self.logger.debug('CAhandler._config_domainlists_load()')
    if 'openssl_conf' in config_dic['CAhandler']:
      self.openssl_conf = config_dic['CAhandler']['openssl_conf']
    if 'allowed_domainlist' in config_dic['CAhandler']:
      self.allowed_domainlist = json.loads(config_dic['CAhandler']['allowed_domainlist'])
    if 'blocked_domainlist' in config_dic['CAhandler']:
      self.blocked_domainlist = json.loads(config_dic['CAhandler']['blocked_domainlist'])
    if 'whitelist' in config_dic['CAhandler']:
      self.allowed_domainlist = json.loads(config_dic['CAhandler']['whitelist'])
      self.logger.error('CAhandler._config_load() found "whitelist" parameter in configfile which should be renamed to "allowed_domainlist"')
    if 'blacklist' in config_dic['CAhandler']:
      self.blocked_domainlist = json.loads(config_dic['CAhandler']['blacklist'])
      self.logger.error('CAhandler._config_load() found "blacklist" parameter in configfile which should be renamed to "blocked_domainlist"')
    self.logger.debug('CAhandler._config_domainlists_load() ended')

  def _config_credentials_load(self, config_dic: Dict[str, str]):
    """ load credential config """
    self.logger.debug('CAhandler._config_credentials_load()')

    if 'issuing_ca_key' in config_dic['CAhandler']:
      self.issuer_dict['issuing_ca_key'] = config_dic['CAhandler']['issuing_ca_key']
    if 'issuing_ca_cert' in config_dic['CAhandler']:
      self.issuer_dict['issuing_ca_cert'] = config_dic['CAhandler']['issuing_ca_cert']
    if 'issuing_ca_key_passphrase_variable' in config_dic['CAhandler']:
      try:
        self.issuer_dict['passphrase'] = os.environ[config_dic['CAhandler']['issuing_ca_key_passphrase_variable']]
      except Exception as err:
        self.logger.error('CAhandler._config_load() could not load issuing_ca_key_passphrase_variable:{0}'.format(err))
    if 'issuing_ca_key_passphrase' in config_dic['CAhandler']:
      if 'passphrase' in self.issuer_dict and self.issuer_dict['passphrase']:
        self.logger.info('CAhandler._config_load() overwrite issuing_ca_key_passphrase_variable')
      self.issuer_dict['passphrase'] = config_dic['CAhandler']['issuing_ca_key_passphrase']

    # convert passphrase
    if 'passphrase' in self.issuer_dict:
      self.issuer_dict['passphrase'] = self.issuer_dict['passphrase'].encode('ascii')

    self.logger.debug('CAhandler._config_credentials_load() ended')

  def _config_policy_load(self, config_dic: Dict[str, str]):
    """ load certificate policy """
    self.logger.debug('CAhandler._config_policy_load()')

    if 'ca_cert_chain_list' in config_dic['CAhandler']:
      self.ca_cert_chain_list = json.loads(config_dic['CAhandler']['ca_cert_chain_list'])
    if 'cert_validity_days' in config_dic['CAhandler']:
      self.cert_validity_days = int(config_dic['CAhandler']['cert_validity_days'])
    if 'cert_save_path' in config_dic['CAhandler']:
      self.cert_save_path = config_dic['CAhandler']['cert_save_path']
    if 'issuing_ca_crl' in config_dic['CAhandler']:
      self.issuer_dict['issuing_ca_crl'] = config_dic['CAhandler']['issuing_ca_crl']
    if 'cert_db_index_file' in config_dic['CAhandler']:
      self.cert_db_index_file = config_dic['CAhandler']['cert_db_index_file']
    if 'cert_serial_file' in config_dic['CAhandler']:
      self.cert_serial_file = config_dic['CAhandler']['cert_serial_file']
    try:
      self.cn_enforce = config_dic.getboolean('CAhandler', 'cn_enforce', fallback=False)
    except Exception:
      self.logger.error('CAhandler._config_load() variable cn_enforce cannot be parsed')
    try:
      self.cert_validity_adjust = config_dic.getboolean('CAhandler', 'cert_validity_adjust', fallback=False)
    except Exception:
      self.logger.error('CAhandler._config_load() variable cert_validity_adjust cannot be parsed')

    self.logger.debug('CAhandler._config_policy_load() ended')

  def _config_load(self):
    """" load config from file """
    self.logger.debug('CAhandler._config_load()')
    config_dic = load_config(self.logger, 'CAhandler')

    # load credentials
    self._config_credentials_load(config_dic)

    # load policy options
    self._config_policy_load(config_dic)

    # load allow/block lists
    self._config_domainlists_load(config_dic)

    # load profiling
    self.eab_profiling, self.eab_handler = config_eab_profile_load(self.logger, config_dic)

    # load headerinfo
    self.header_info_field = config_headerinfo_load(self.logger, config_dic)

    self.save_cert_as_hex = config_dic.getboolean('CAhandler', 'save_cert_as_hex', fallback=False)
    self.logger.debug('CAhandler._config_load() ended')

  def _chk_san_lists_get(self, csr: str) -> Tuple[List[str], List[bool]]:
    """ check lists  """
    self.logger.debug('CAhandler._chk_san_lists_get()')

    # get sans and build a list
    _san_list = csr_san_get(self.logger, csr)

    check_list = []
    san_list = []

    if _san_list:
      for san in _san_list:
        try:
          # SAN list must be modified/filtered)
          (_san_type, san_value) = san.lower().split(':')
          san_list.append(san_value)
        except Exception:
          # force check to fail as something went wrong during parsing
          check_list.append(False)
          self.logger.debug('CAhandler._csr_check(): san_list parsing failed at entry: {0}'.format(san))

    self.logger.debug('CAhandler._chk_san_lists_get() ended')
    return (san_list, check_list)

  def _cn_add(self, csr: str, san_list: List[str]) -> Tuple[List[str], str]:
    """ add CN if required """
    self.logger.debug('CAhandler._cn_add()')

    # get common name and attach it to san_list
    cn_ = csr_cn_get(self.logger, csr)

    if not cn_ and san_list:
      enforced_cn = san_list[0]
      self.logger.info('CAhandler._csr_check(): enforce CN to {0}'.format(enforced_cn))
    else:
      enforced_cn = None

    if cn_:
      cn_ = cn_.lower()
      if cn_ not in san_list:
        # append cn to san_list
        self.logger.debug('Ahandler._csr_check(): append cn to san_list')
        san_list.append(cn_)

    self.logger.debug('CAhandler._cn_add() ended with: {0}'.format(enforced_cn))
    return (san_list, enforced_cn)

  def _csr_check(self, csr: str) -> Tuple[bool, str]:
    """ check CSR against definied allowed_domainlists """
    self.logger.debug('CAhandler._csr_check()')

    (san_list, check_list) = self._chk_san_lists_get(csr)
    (san_list, enforced_cn) = self._cn_add(csr, san_list)

    if self.allowed_domainlist or self.blocked_domainlist:
      result = False

      # go over the san list and check each entry
      for san in san_list:
        check_list.append(self._string_wlbl_check(san, self.allowed_domainlist, self.blocked_domainlist))

      if check_list:
        # cover a cornercase with empty checklist (no san, no cn)
        if False in check_list:
          result = False
        else:
          result = True
    else:
      result = True

    self.logger.debug('CAhandler._csr_check() ended with: {0} enforce_cn: {1}'.format(result, enforced_cn))
    return (result, enforced_cn)

  def _list_regex_check(self, entry: str, list_: List[str]) -> bool:
    """ check entry against regex """
    self.logger.debug('CAhandler._list_regex_check()')

    check_result = False
    for regex in list_:
      if regex.startswith('*.'):
        regex = regex.replace('*.', '.')
      regex_compiled = re.compile(regex)
      if bool(regex_compiled.search(entry)):
        # parameter is in set flag accordingly and stop loop
        check_result = True

    self.logger.debug('CAhandler._list_regex_check() ended with: {0}'.format(check_result))
    return check_result

  def _list_check(self, entry: str, list_: List[str], toggle: bool = False) -> bool:
    """ check string against list """
    self.logger.debug('CAhandler._list_check({0}:{1})'.format(entry, toggle))
    self.logger.debug('check against list: {0}'.format(list_))

    # default setting
    check_result = False

    if entry:
      if list_:
        check_result = self._list_regex_check(entry, list_)
      else:
        # empty list, flip parameter to make the check successful
        check_result = True

    if toggle:
      # toggle result if this is a blocked_domainlist
      check_result = not check_result

    self.logger.debug('CAhandler._list_check() ended with: {0}'.format(check_result))
    return check_result

  def _pemcertchain_generate(self, ee_cert: str, issuer_cert: str) -> str:
    """ build pem chain """
    self.logger.debug('CAhandler._pemcertchain_generate()')

    if issuer_cert:
      pem_chain = '{0}{1}'.format(ee_cert, issuer_cert)
    else:
      pem_chain = ee_cert
    for cert in self.ca_cert_chain_list:
      if os.path.exists(cert):
        with open(cert, 'r', encoding='utf8') as fso:
          cert_pem = fso.read()
        pem_chain = '{0}{1}'.format(pem_chain, cert_pem)

    self.logger.debug('CAhandler._pemcertchain_generate() ended')
    return pem_chain

  def _string_wlbl_check(self, entry: str, white_list: List[str], black_list: List[str]) -> bool:
    """ check single against allowed_domainlist and blocked_domainlist """
    self.logger.debug('CAhandler._string_wlbl_check({0})'.format(entry))

    # default setting
    chk_result = False

    # check if entry is in white_list
    wl_check = self._list_check(entry, white_list)
    if wl_check:
      self.logger.debug('{0} in white_list'.format(entry))
      if black_list:
        # we need to check blocked_domainlist if there is a blocked_domainlist and wl check passed
        if self._list_check(entry, black_list):
          self.logger.debug('{0} in black_list'.format(entry))
        else:
          self.logger.debug('{0} not in black_list'.format(entry))
          chk_result = True
      else:
        chk_result = wl_check
    else:
      self.logger.debug('{0} not in white_list'.format(entry))

    self.logger.debug('CAhandler._string_wlbl_check({0}) ended with: {1}'.format(entry, chk_result))
    return chk_result

  def _cert_expiry_get(self, cert):
    """ get expiry date of certificate """
    self.logger.debug('CAhandler._cert_expiry_get()')

    expiry_date = cert.not_valid_after

    self.logger.debug('CAhandler._cert_expiry_get() ended')
    return expiry_date

  def _cacert_expiry_get(self):
    """ get closesd expiry date of issuing CA """
    self.logger.debug('CAhandler._cacert_expiry_get()')

    ca_list = self.ca_cert_chain_list
    if self.issuer_dict['issuing_ca_cert']:
      ca_list.append(self.issuer_dict['issuing_ca_cert'])

    expiry_days = 0
    cert = None

    for ca_cert in ca_list:
      if ca_cert:
        if os.path.exists(ca_cert):
          with open(ca_cert, 'rb') as fso:
            ca_cert = x509.load_pem_x509_certificate(fso.read(), backend=default_backend())
            _tmp_expiry_days = (self._cert_expiry_get(ca_cert) - datetime.datetime.now()).days
            if not expiry_days or _tmp_expiry_days < expiry_days:
              self.logger.debug('CAhandler._cacert_expiry_get(): set expiry_days to {0}'.format(_tmp_expiry_days))
              expiry_days = _tmp_expiry_days
              cert = ca_cert
        else:
          self.logger.error('CAhandler._cacert_expiry_get(): file {0} does not exist'.format(ca_cert))

    self.logger.debug('CAhandler._cacert_expiry_get() ended')
    return expiry_days, cert

  def _certexpiry_date_default(self) -> datetime.datetime:
    """ set certificate validity """
    self.logger.debug('CAhandler._certexpiry_date_default()')

    # default cert validity is taken from config
    cert_validity = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=self.cert_validity_days)

    self.logger.debug('CAhandler._certexpiry_date_default() ended')
    return cert_validity

  def _certexpiry_date_set(self) -> datetime.datetime:
    """ set certificate validity """
    self.logger.debug('CAhandler._certexpiry_date_set()')

    # default cert validity is taken from config
    cert_validity = self._certexpiry_date_default()

    if self.cert_validity_adjust:
      # adjust validity to match the validity of the issuing CA

      (ca_cert_validity, cert) = self._cacert_expiry_get()
      if ca_cert_validity < self.cert_validity_days:
        self.logger.info('CAhandler._certexpiry_date_set(): adjust validity to {0} days.'.format(ca_cert_validity))
        cert_validity = cert.not_valid_after

    self.logger.debug('CAhandler._certexpiry_date_set() ended')
    return cert_validity

  def _cert_signing_prep(self, ca_cert: object, req: object, subject: str) -> object:
    """ enroll certificate """
    # pylint: disable=R0914, R0915
    self.logger.debug('CAhandler._cert_signing_prep()')

    cert_validity = self._certexpiry_date_set()

    # sign csr
    builder = x509.CertificateBuilder()
    builder = builder.not_valid_before(datetime.datetime.now(datetime.timezone.utc))
    builder = builder.not_valid_after(cert_validity)
    builder = builder.issuer_name(ca_cert.subject)
    builder = builder.subject_name(subject)
    serial = uuid.uuid4().int
    if os.path.exists(self.cert_serial_file):
      with open(self.cert_serial_file, 'r+') as serial_file:
        content = serial_file.read()
        serial = int(content.strip(), base=16)
        next_serial = serial + 1
        next_serial_formatted = self.serial_in_hex_format(next_serial)
        self.logger.debug('CAhandler.enroll() next serial in hex: {0}'.format(next_serial_formatted))
        serial_file.seek(0)
        serial_file.truncate()
        serial_file.write(next_serial_formatted)
    builder = builder.serial_number(serial)
    builder = builder.public_key(req.public_key())

    self.logger.debug('CAhandler._cert_signing_prep() ended')
    return builder

  def _cert_extension_default(self, ca_cert: object, req: object) -> List[str]:
    """ add default extensions """
    self.logger.debug('CAhandler._cert_extension_default()')

    default_extension_list = [
      {'name': BasicConstraints(ca=False, path_length=None), 'critical': True},
      {'name': ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH, ExtendedKeyUsageOID.CLIENT_AUTH]), 'critical': False},
      {'name': KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=True, data_encipherment=False, key_agreement=False, key_cert_sign=False, crl_sign=False, encipher_only=False, decipher_only=False), 'critical': True},
    ]
    if req:
      default_extension_list.append({'name': SubjectKeyIdentifier.from_public_key(req.public_key()), 'critical': False},)
    if ca_cert:
      default_extension_list.append({'name': AuthorityKeyIdentifier.from_issuer_public_key(ca_cert.public_key()), 'critical': False})

    self.logger.debug('CAhandler._cert_extension_default() ended')
    return default_extension_list

  def _cert_extension_apply(self, builder: object, ca_cert: object, req: object) -> object:
    """ add cert extensions """
    self.logger.debug('CAhandler._cert_extension_apply()')

    # load certificate_profile (if applicable)
    if self.openssl_conf:
      cert_extension_dic = self._certificate_extensions_load()
      extension_list = self._cert_extension_dic_parse(cert_extension_dic, req, ca_cert)
    else:
      extension_list = self._cert_extension_default(ca_cert, req)

    # add subject alternative names
    if req:
      for ext in req.extensions:
        if ext.oid._name == 'subjectAltName':  # pylint: disable=W0212
          extension_list.append({'name': SubjectAlternativeName(ext.value), 'critical': False})

    for extension in extension_list:
      # add extensions to csr
      builder = builder.add_extension(extension['name'], critical=extension['critical'])

    self.logger.debug('CAhandler._cert_extension_apply() ended')
    return builder

  def enroll(self, csr: str) -> Tuple[str, str, str, str]:
    """ enroll certificate """
    # pylint: disable=R0914, R0915
    self.logger.debug('CAhandler.enroll()')

    cert_bundle = None
    cert_raw = None

    error = self._config_check()

    if not error:
      # check for eab profiling and header_info
      error = eab_profile_header_info_check(self.logger, self, csr, 'profile_id')

    if not error:
      try:
        # check CN and SAN against black/whitlist
        (result, enforce_cn) = self._csr_check(csr)

        if result:
          # prepare the CSR
          csr = build_pem_file(self.logger, None, b64_url_recode(self.logger, csr), None, True)

          # load ca cert and key
          (ca_key, ca_cert) = self._ca_load()

          # creating a rest from CSR
          req = x509.load_pem_x509_csr(convert_string_to_byte(csr), default_backend())
          subject = req.subject

          if self.cn_enforce and enforce_cn:
            self.logger.info('CAhandler.enroll(): overwrite CN with {0}'.format(enforce_cn))
            subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, enforce_cn)])

          builder = self._cert_signing_prep(ca_cert, req, subject)
          builder = self._cert_extension_apply(builder, ca_cert, req)

          # sign certificate
          cert = builder.sign(private_key=ca_key, algorithm=hashes.SHA256(), backend=default_backend())

          # store certifiate
          self._certificate_store(cert)
          # create bundle and raw cert
          with open(self.issuer_dict['issuing_ca_cert'], 'r', encoding='utf8') as ca_fso:
            cert_bundle = self._pemcertchain_generate(convert_byte_to_string(cert.public_bytes(serialization.Encoding.PEM)), ca_fso.read())
            cert_raw = convert_byte_to_string(base64.b64encode(cert.public_bytes(serialization.Encoding.DER)))
          if os.path.exists(self.cert_db_index_file):
            with open(self.cert_db_index_file, 'a', encoding='utf8') as index_file:
              dn = cert.subject.rfc4514_string({NameOID.SERIAL_NUMBER: "serialNumber"}).replace("/", "\\/").replace(",", "/")
              index_file.write('V\t')
              index_file.write(cert.not_valid_after.strftime('%y%m%d%H%M%SZ') + '\t\t')
              serial_formatted = self.serial_in_hex_format(cert.serial_number)
              index_file.write(serial_formatted + '\t')
              index_file.write('unknown\t')
              index_file.write(dn + '\n')
        else:
          error = 'urn:ietf:params:acme:badCSR'

      except Exception as err:
        self.logger.error('CAhandler.enroll() error: {0}'.format(err))
        error = 'Unknown exception'

    self.logger.debug('CAhandler.enroll() ended')
    return (error, cert_bundle, cert_raw, None)

  def poll(self, _cert_name: str, poll_identifier: str, _csr: str) -> Tuple[str, str, str, str, bool]:
    """ poll status of pending CSR and download certificates """
    self.logger.debug('CAhandler.poll()')

    error = 'Method not implemented.'
    cert_bundle = None
    cert_raw = None
    rejected = False

    self.logger.debug('CAhandler.poll() ended')
    return (error, cert_bundle, cert_raw, poll_identifier, rejected)

  def _crlobject_build(self, ca_cert: object, serial: int) -> Tuple[x509.CertificateRevocationListBuilder, object]:
    self.logger.debug('CAhandler._crlobject_build()')

    if os.path.exists(self.issuer_dict['issuing_ca_crl']):
      self.logger.info('CAhandler.revoke(): load existing crl {0})'.format(self.issuer_dict['issuing_ca_crl']))
      # load  existing CRL
      with open(self.issuer_dict['issuing_ca_crl'], 'rb') as fso:
        crl_data = fso.read()
        crl = x509.load_pem_x509_crl(crl_data, default_backend())
      builder = x509.CertificateRevocationListBuilder()
      builder = builder.issuer_name(crl.issuer)
      # add crl certificates from file to the new crl object
      for revserial in crl:
        builder = builder.add_revoked_certificate(revserial)  # pragma: no cover

      # see if the cert to be revokek already in the list
      ret = crl.get_revoked_certificate_by_serial_number(serial)

    else:
      self.logger.info('CAhandler._crlobject_build(): create new crl {0})'.format(self.issuer_dict['issuing_ca_crl']))
      builder = x509.CertificateRevocationListBuilder()
      builder = builder.issuer_name(ca_cert.issuer)
      ret = None

    self.logger.debug('CAhandler._crlobject_build() ended')
    return (builder, ret)

  def revoke(self, cert_pem: str, rev_reason: str = 'unspecified', rev_date: str = None) -> Tuple[int, str, str]:
    """ revoke certificate """
    self.logger.debug('CAhandler.revoke({0}: {1})'.format(rev_reason, rev_date))
    code = None
    message = None
    detail = None

    rev_date_format = '%y%m%d%H%M%SZ'

    # overwrite revocation date - we ignore what has been submitted
    rev_date = uts_to_date_utc(uts_now(), rev_date_format)

    if 'issuing_ca_crl' in self.issuer_dict and self.issuer_dict['issuing_ca_crl']:
      # load ca cert and key
      (ca_key, ca_cert) = self._ca_load()

      # turn of chain_check due to issues in pyopenssl (check is not working if key-usage is set)
      # result = self._certificate_chain_verify(cert, ca_cert)

      # get serial number from certicate to be revoked
      serial = cert_serial_get(self.logger, cert_pem)

      if ca_key and ca_cert and serial:

        # build crl object
        (builder, ret) = self._crlobject_build(ca_cert, serial)

        if not isinstance(ret, x509.RevokedCertificate):
          # this is the revocation operation
          # Set up the revoked entry
          revoked_entry = x509.RevokedCertificateBuilder().serial_number(serial).revocation_date(datetime.datetime.strptime(rev_date, rev_date_format)).build(default_backend())
          builder = builder.add_revoked_certificate(revoked_entry)

          # Sign the CRL
          crl = builder.last_update(datetime.datetime.strptime(rev_date, rev_date_format)).next_update(datetime.datetime.strptime(rev_date, rev_date_format)).sign(ca_key, hashes.SHA256())

          # Save CRL
          with open(self.issuer_dict['issuing_ca_crl'], 'wb') as fso:
            fso.write(crl.public_bytes(serialization.Encoding.PEM))
          code = 200
        else:
          code = 400
          message = 'urn:ietf:params:acme:error:alreadyRevoked'
          detail = 'Certificate has already been revoked'
      else:
        code = 400
        message = 'urn:ietf:params:acme:error:serverInternal'
        detail = 'configuration error'
    else:
      code = 400
      message = 'urn:ietf:params:acme:error:serverInternal'
      detail = 'Unsupported operation'

    self.logger.debug('CAhandler.revoke() ended')
    return (code, message, detail)

  def trigger(self, _payload: str) -> Tuple[str, str, str]:
    """ process trigger message and return certificate """
    self.logger.debug('CAhandler.trigger()')

    error = 'Method not implemented.'
    cert_bundle = None
    cert_raw = None

    self.logger.debug('CAhandler.trigger() ended with error: {0}'.format(error))
    return (error, cert_bundle, cert_raw)
