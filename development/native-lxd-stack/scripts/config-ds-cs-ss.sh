#!/bin/bash

######
### TODO: Temporary script to initialize the DS environment. Should be refactored.
######

set -e

ensure_file_exists() {
  if [ ! -f "/etc/xroad/conf.d/$1" ]; then
    echo "Creating /etc/xroad/conf.d/$1"
    echo "edc:" > /etc/xroad/conf.d/"$1"
  fi
}

configure_cs_edc_signing_key() {
  ensure_file_exists ds-catalog-service-override.yaml
  ensure_file_exists ds-credential-service-override.yaml
  ensure_file_exists edc-identity-hub-override.yaml

  HOSTNAME=$(hostname)
  EDC_DID="did:web:${HOSTNAME}%3A9396"
  KEY_ID=$(grep -oPz '(?s)<key usage=\"SIGNING\">.*?<label>Internal signing key</label>.*?</key>' /etc/xroad/signer/keyconf.xml | grep -oPa '<keyId>\K\w+(?=</keyId>)')
  MEMBER_ID="$1"

  yq -Y -i ".edc.hostname = \"${HOSTNAME}\"" /etc/xroad/conf.d/ds-catalog-service-override.yaml
  yq -Y -i ".edc.iam.sts.privatekey.alias = \"${KEY_ID}\"" /etc/xroad/conf.d/ds-catalog-service-override.yaml
  yq -Y -i ".edc.iam.sts.publickey.id = \"${EDC_DID}#${KEY_ID}\"" /etc/xroad/conf.d/ds-catalog-service-override.yaml
  yq -Y -i ".edc.participant.id = \"${EDC_DID}\"" /etc/xroad/conf.d/ds-catalog-service-override.yaml
  yq -Y -i ".edc.iam.issuer.id = \"${EDC_DID}\"" /etc/xroad/conf.d/ds-catalog-service-override.yaml

  yq -Y -i ".edc.did.key.id = \"${KEY_ID}\"" /etc/xroad/conf.d/ds-credential-service-override.yaml
  yq -Y -i ".edc.participant.id = \"${EDC_DID}\"" /etc/xroad/conf.d/ds-credential-service-override.yaml

  yq -Y -i ".edc.hostname = \"${HOSTNAME}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml
  yq -Y -i ".edc.did.key.id = \"${KEY_ID}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml
  yq -Y -i ".edc.participant.id = \"${EDC_DID}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml
  yq -Y -i ".edc.\"xroad-member-id\" = \"${MEMBER_ID}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml


  echo "Restarting services"
  systemctl restart xroad-edc-catalog-service xroad-edc-credential-service xroad-edc-identity-hub
}

configure_ss_edc_signing_key() {
  ensure_file_exists edc-control-plane-override.yaml
  ensure_file_exists edc-data-plane-override.yaml
  ensure_file_exists edc-identity-hub-override.yaml

  HOSTNAME=$(hostname)
  EDC_DID="did:web:${HOSTNAME}%3A9396"
  KEY_ID=$(grep -oPz '(?s)<key usage=\"SIGNING\">.*?<label>Sign key</label>.*?</key>' /etc/xroad/signer/keyconf.xml | grep -oPa '<keyId>\K\w+(?=</keyId>)')
  MEMBER_ID="$1"

  yq -Y -i ".edc.hostname = \"${HOSTNAME}\"" /etc/xroad/conf.d/edc-control-plane-override.yaml
  yq -Y -i ".edc.iam.sts.privatekey.alias = \"${KEY_ID}\"" /etc/xroad/conf.d/edc-control-plane-override.yaml
  yq -Y -i ".edc.iam.sts.publickey.id = \"${EDC_DID}#${KEY_ID}\"" /etc/xroad/conf.d/edc-control-plane-override.yaml
  yq -Y -i ".edc.participant.id = \"${EDC_DID}\"" /etc/xroad/conf.d/edc-control-plane-override.yaml
  yq -Y -i ".edc.iam.issuer.id = \"${EDC_DID}\"" /etc/xroad/conf.d/edc-control-plane-override.yaml

  yq -Y -i ".edc.hostname = \"${HOSTNAME}\"" /etc/xroad/conf.d/edc-data-plane-override.yaml
  yq -Y -i ".edc.transfer.proxy.token.signer.privatekey.alias = \"${KEY_ID}\"" /etc/xroad/conf.d/edc-data-plane-override.yaml
  yq -Y -i ".edc.transfer.proxy.token.verifier.publickey.alias = \"${KEY_ID}\"" /etc/xroad/conf.d/edc-data-plane-override.yaml

  yq -Y -i ".edc.hostname = \"${HOSTNAME}\""  /etc/xroad/conf.d/edc-identity-hub-override.yaml
  yq -Y -i ".edc.did.key.id = \"${KEY_ID}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml
  yq -Y -i ".edc.participant.id = \"${EDC_DID}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml
  yq -Y -i ".edc.\"xroad-member-id\" = \"${MEMBER_ID}\"" /etc/xroad/conf.d/edc-identity-hub-override.yaml

  echo "Restarting services"
  systemctl restart xroad-edc-data-plane xroad-edc-control-plane xroad-edc-identity-hub
}

apt-get -qq update && apt-get -qq install yq

case $1 in
  cs)
    echo "Configuring Central Server EDC keys"
    configure_cs_edc_signing_key "$2"
    ;;
  ss)
    echo "Configuring Security Server EDC keys"
    configure_ss_edc_signing_key "$2"
    ;;
esac

