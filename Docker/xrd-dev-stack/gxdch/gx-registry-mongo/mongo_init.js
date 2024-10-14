db.auth('mongoroot', 'password123')

db = db.getSiblingDB('trust-anchor-registry')


db.createUser(
    {
        user: "mongoadmin",
        pwd: "password123",
        roles: [
            { role: "userAdmin", db: "trust-anchor-registry" },
            { role: "readWrite", db: "trust-anchor-registry" },
            { role: "dbAdmin", db: "trust-anchor-registry" }
        ]
    }
)

db.trustanchors.insertOne({
    _id: ObjectId('67cb89b164e3465dca1de1bd'),
    certificate: 'MIIFMzCCAxugAwIBAgIUSH/UL7DARntl8uC64ERCod8QDRowDQYJKoZIhvcNAQELBQAwITENMAsGA1UECgwEVGVzdDEQMA4GA1UEAwwHVGVzdCBDQTAeFw0yNDA1MDYwOTUwNTNaFw00NDA1MDEwOTUwNTNaMCExDTALBgNVBAoMBFRlc3QxEDAOBgNVBAMMB1Rlc3QgQ0EwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQCgJfKh/h8bKHn09/BzZBerRHYg8s/r8ZBWmyDms1AFzvrt5JWbHQZil56m+ikMw5dKN58ewG6696UB6T1zvVCEscImrVeTuYRtgv7D+jhyEsLlWJRJf9Yilj98xbkh2TUtSRzYqSBKHBS4lI9W7fG1MeDm21ZsRnwmVllx+T1rvbH5zM3QdSLXWkls0vEthGuNbxyeKygEIWVZWD5xv2C3xu0kNJCDqRICo2pAH/Wmeg4ChHM+0Icy4nm2iVgP5VJCkfgqoQtWKj+2+GoZiCtMOedhvfw8UkEI8p4fsKNOhjdD8HOFJRy9Li08h96pr+i3FTGRfLpBunBqlBey5CnvhFGLlyhdISN3y8rHEncQ/7bIaHrTWX4qUJWStxEQX7pOHIVsxhdB/iHaHQhMq/wgKo76wuiNbV/Avb2MQsWWbek7P94o8RtVbIViMA2c7zUzLuHA9AkwEkBOrnOWma6NzZALTTCXocWvj3kmW4JrBM/PTfnhOI+++lmvHgtfpf6+mnBsxQNagzt83c5Zvh3k+BHzNASbr5RQ/cw4BjCyF3Ce4u1P9c3dxhlJD2v3Cv1XA3w2NNnjUvAUY6+ZOcbREnD8U33gPz1UwGf63foSBSNsu5WeijWoY1jCZldyZIvBb5AzfUuYESMmyOg4+XCqG6fJpxmobZIJUTi1bbzsswIDAQABo2MwYTAdBgNVHQ4EFgQUGyUhDB2RZUvJoDQvYKLhvqZgYgUwHwYDVR0jBBgwFoAUGyUhDB2RZUvJoDQvYKLhvqZgYgUwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAYYwDQYJKoZIhvcNAQELBQADggIBAGHNy/CDBKEBxJJoALuwBqjUTgKZAq9Z1HFRaEpVa76DCUTslm5FjY/RRkzOP4YZPfkO+UD7i/UC1+d0uRUpEAqln9VVBhXlwNIlzeqIYgppqRqIg82gYZtGpbCFys7B3w5d5//3WB5iVVYMn1MpHmci60d09qlH2q4qPDIPdPot8HZr8vjxC3+iMr3NunpCgmkx1Pi2cwEU0gLHqtum0oWAg4TxeNdWPJciRSd51R9z4E5Dn/b+N1WjiOspbgnzzbMYgvCj9hdDev7/QDs3tXW192HmTZp6Luc1HjHjwxt3oSbUX0mFI4dNzfHNiQpNGpbCUMcBI75hzyFvJxlgMT4Xc/GAiBVvQ0Zh+oUf8dMFan49GaSCYUFSP44v6Qmyh/ujPSzDeiDYr/RfmAdi91p98AoM1cyS15itE1eDlJG+6dhzlNNuXaXTYh/PPUjwZZ2sn/tYH7ipTBnELOVmWxC1iJdoMicvfnn0lNTEN94B6J7XCOFJMmijOlJgLbcPJOVR6F9HI6dprDkl0zr8qXEIkPy1m2bCFef0XFt8vdDxfe7i7R+952skwjYxb4W2MKdZD5L2f1iA1Y+eEHvRPeoQamsJW8Xn6n59reTLg/GY1R3bsIa67ewxxCe+tAU+l7xuxNytXjym0Jh00kSgO7WSPOvfIKEpAm5SUy8n5/f0',
    _list: ObjectId('65b39f8228c7f350c5e2039b'),
    createdAt: new Date(),
    name: 'keep_X-Road Test CA',
    updatedAt: new Date()
});
