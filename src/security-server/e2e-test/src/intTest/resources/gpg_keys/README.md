
GPG keys (using rsa4096) for message log encryption tests.:

| Label   | Full key id                              | Short key id     |
|---------|------------------------------------------|------------------|
| key1    | D6C045E3C686322D433061668A4BB80EEE081BDE | 8A4BB80EEE081BDE |
| key2    | 68924DB4BE7A70A6D3551481E93952B01C2D2EA5 | E93952B01C2D2EA5 |
| general | 446A59027911574F3F4953673BD9C292C63580F8 | 3BD9C292C63580F8 |

passphrase: secret

[public-keys.asc](public-keys.asc) - contains all public keys. <br/>
[secret-keys.asc](secret-keys.asc) - contains all secret keys.

Secret keys extracted to separate files:
- [8A4BB80EEE081BDE.asc](8A4BB80EEE081BDE.asc)
- [E93952B01C2D2EA5.asc](E93952B01C2D2EA5.asc)
- [3BD9C292C63580F8.asc](3BD9C292C63580F8.asc)

Sample configuration used in SS1 for message log encryption ([application.yaml](../msglog_encryption_config/application.yaml)):
```yaml
xroad:
  proxy:
    message-log:
      database-encryption:
        enabled: true
      archiver:
        encryption-enabled: true
        archive-interval: 0 * * * * ?
        default-key-id: 3BD9C292C63580F8
        grouping-strategy: SUBSYSTEM
        grouping-keys:
          "DEV/COM/4321": 8A4BB80EEE081BDE,E93952B01C2D2EA5
```

GPG keys generated on Ubuntu 24.04 using the following commands:
```bash
gpg --homedir gpg --quick-generate-key key1 rsa4096 default never
gpg --homedir gpg --quick-generate-key key2 rsa4096 default never
gpg --homedir gpg --quick-generate-key general rsa4096 default never
```

Some useful commands with GPG encryption:
```bash
gpg [--homedir <dir>] --export-secret-keys --armor --output <output file name> # export all secret keys
ppg [--homedir <dir>] --export-secret-keys --armor --output <output file name> <keyid> # export secret key by id
gpg [--homedir <dir>] --export --armor --output <output file name> # export all public keys
gpg [--homedir <dir>] --export --armor --output <output file name> <keyid> # export public key by id
gpg --list-packets <gpg file> # list packets in the gpg file
```
