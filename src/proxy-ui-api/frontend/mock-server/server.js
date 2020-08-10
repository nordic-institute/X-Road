
var express = require('express');
var bodyParser = require('body-parser');
const formidable = require('formidable');
var app = express();

const port = 6069;
const SLEEP_TIME = 200;


function sleep(ms = SLEEP_TIME) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}


function makeid(length) {
  var text = "";
  var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  for (var i = 0; i < length; i++)
    text += possible.charAt(Math.floor(Math.random() * possible.length));

  return text;
}


function makeName(length) {
  var text = "";
  var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  for (var i = 0; i < length; i++)
    text += possible.charAt(Math.floor(Math.random() * possible.length));

  return text;
}



//Allow all requests from all domains & localhost
app.all('/*', function (req, res, next) {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, Accept");
  res.header("Access-Control-Allow-Methods", "POST, GET");
  next();
});

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: false }));


//import mockJson from './fi-all';
//import mockJson from ('./ee-all.json');
//import someObject from ('./somefile.json')
//let mockJson = require('./ee-all.json');
let mockJson = require('./mockClients.json');


app.get('/api/clients', function (req, res) {
  console.log("GET clients");
  console.log(req.body);
  res.send(mockJson);
});


// GET Client
app.get('/api/clients/:id', function (req, res) {
  console.log("GET client");
  if (!req.params.id) {
    res.status(400).send('missing parameter');
    return;
  }

  const mockClient = {
    "id": "CS:GOV:3000:POP2",
    "member_name": makeName(6),
    "member_class": "GOV",
    "member_code": "3000",
    "subsystem_code": "POP2",
    "created": null,
    "status": "global error",
    "connectiontype": "http"
  };

  sleep(300).then(() => {
    res.send(mockClient);
  });

});

// PUT client
app.put('/api/clients/:id', function (req, res) {
  console.log("PUT client");
  console.log(req.body);


  if (!req.params.id) {
    res.status(400).send('missing parameter');
    return;
  }

  const mockClient = {
    "id": "CS:GOV:3000:POP2",
    "member_name": "Acme",
    "member_class": "GOV",
    "member_code": "3000",
    "subsystem_code": "POP2",
    "created": null,
    "status": "global error",
    "connectiontype": req.body.connectiontype
  };


  sleep().then(() => {
    res.status(200).send(mockClient);
  });
});



app.get('/api/system/export', function (req, res) {
  var file = __dirname + '/upload/fake.cer';
  res.download(file, 'mock_cert.cer'); // Set disposition and send it.
});


app.get('/api/system/certificate', function (req, res) {
  console.log("GET SS cert");
  console.log(req.body);

  const mock =
  {
    "hash": "12:34:56:78:90:AB:CD:EF"
  };

  sleep().then(() => {
    res.send(mock);
  });
});


app.get('/api/clients/:id/certificates', function (req, res) {
  console.log("GET certificates");

  if (!req.params.id) {
    res.status(400).send('missing parameter');
    return;
  }

  const mock = [
    {
      "name": "X-Road Test CA CN",
      "csp": "globalsign",
      "serial": 66,
      "state": "in use",
      "expires": "2099"
    },
    {
      "name": "X-Road Test 2",
      "csp": "globalsign",
      "serial": 61,
      "state": "in use",
      "expires": "2022"
    }
  ];

  sleep().then(() => {
    res.send(mock);
  });
});


app.get('/api/clients/:id/tlscertificates', function (req, res) {
  console.log("GET TLS cert");

  if (!req.params.id) {
    res.status(400).send('missing parameter');
    return;
  }

  const mock = [
    {
      "hash": "12:34:56:78:90:AB:CD:EE",
      "details": "string"
    },

    {
      "hash": "55:34:56:78:90:AB:CD:AR",
      "details": "string"
    }
  ];

  sleep().then(() => {
    res.send(mock);
  });

});


app.post('/api/clients/:id/tlscertificates', function (req, res) {
  console.log("POST TLS cert");

  if (!req.params.id) {
    res.status(400).send('missing parameter');
    return;
  }

  console.log(req.body);
  var form = new formidable.IncomingForm().parse(req)
    .on('field', (name, field) => {
      console.log('Field', name, field)
    })
    .on('file', (name, file) => {
      console.log('Uploaded file', name, file)
    })
    .on('aborted', () => {
      console.error('Request aborted by the user')
    })
    .on('error', (err) => {
      console.error('Error', err)
      throw err
    })
    .on('end', () => {
      res.end()
    })


});



app.delete('/api/clients/:id/tlscertificates/:hash', function (req, res) {
  console.log("DELETE TLS cert");

  if (!req.params.id || !req.params.hash) {
    res.status(400).send('missing parameter');
    return;
  }

  sleep().then(() => {
    res.status(200).send('Deleted');
  });
});


app.post('/api/submit-form', (req, res) => {
  console.log(req.body);
  var form = new formidable.IncomingForm().parse(req)
    .on('field', (name, field) => {
      console.log('Field', name, field)
    })
    .on('file', (name, file) => {
      console.log('Uploaded file', name, file)
    })
    .on('aborted', () => {
      console.error('Request aborted by the user')
    })
    .on('error', (err) => {
      console.error('Error', err)
      throw err
    })
    .on('end', () => {
      res.end()
    })
});



app.get('/api/user', function (req, res) {
  console.log(req.body);

  const userData =
  {
    "username": "username is unknown (TODO)",
    "roles": ["ROLE_XROAD_SYSTEM_ADMINISTRATOR",
      "ROLE_XROAD_SERVICE_ADMINISTRATOR",
      "ROLE_XROAD_REGISTRATION_OFFICER",
      "ROLE_XROAD_SECURITY_OFFICER",
      "ROLE_XROAD_SECURITYSERVER_OBSERVER"],
    "permissions": [
      "DELETE_KEY",

      "VIEW_TSPS", "RESTORE_CONFIGURATION",
      "SEND_AUTH_CERT_DEL_REQ", "DELETE_SIGN_KEY", "VIEW_ANCHOR",
      "GENERATE_INTERNAL_SSL_CSR", "INIT_CONFIG", "ACTIVATE_DEACTIVATE_TOKEN",

      "GENERATE_AUTH_CERT_REQ", "DELETE_AUTH_KEY", "GENERATE_SIGN_CERT_REQ", "ADD_TSP",

      "ADD_WSDL", "VIEW_PROXY_INTERNAL_CERT",

      "DOWNLOAD_ANCHOR", "EXPORT_PROXY_INTERNAL_CERT",
      "SEND_CLIENT_DEL_REQ", "ADD_LOCAL_GROUP", "VIEW_ACL_SUBJECT_OPEN_SERVICES",
      "IMPORT_AUTH_CERT", "EDIT_SERVICE_ACL", "BACKUP_CONFIGURATION",
      "ADD_CLIENT_INTERNAL_CERT", "DELETE_TSP", "ACTIVATE_DISABLE_AUTH_CERT",
      "UPLOAD_ANCHOR", "DELETE_SIGN_CERT", "DIAGNOSTICS", "EDIT_ACL_SUBJECT_OPEN_SERVICES",
      "EDIT_LOCAL_GROUP_DESC",

      "DELETE_WSDL",
      "EDIT_KEY_FRIENDLY_NAME",
      "EDIT_TOKEN_FRIENDLY_NAME",
      "DELETE_CLIENT",
      "REFRESH_WSDL",
      "ACTIVATE_DISABLE_SIGN_CERT",
      "SEND_AUTH_CERT_REG_REQ", "GENERATE_KEY",


      "EDIT_SERVICE_PARAMS", "EDIT_WSDL", "VIEW_KEYS",
      "VIEW_SERVICE_ACL",
      "DELETE_AUTH_CERT",
      "SEND_CLIENT_REG_REQ", "GENERATE_INTERNAL_CERT_REQ", "IMPORT_SIGN_CERT", "ADD_CLIENT",
      "DELETE_LOCAL_GROUP", "ENABLE_DISABLE_WSDL", "EDIT_LOCAL_GROUP_MEMBERS",

      "GENERATE_INTERNAL_SSL", "VIEW_SYS_PARAMS",


      "VIEW_CLIENTS",

      // -- Client tabs
      "VIEW_CLIENT_DETAILS",
      "VIEW_CLIENT_LOCAL_GROUPS",
      "VIEW_CLIENT_ACL_SUBJECTS",
      "VIEW_CLIENT_SERVICES",
      "VIEW_CLIENT_INTERNAL_CERTS",


      "EDIT_CLIENT_INTERNAL_CONNECTION_TYPE",
      "VIEW_CLIENT_INTERNAL_CONNECTION_TYPE",

      "VIEW_CLIENT_INTERNAL_CERT_DETAILS",
      "DELETE_CLIENT_INTERNAL_CERT",
      "VIEW_INTERNAL_SSL_CERT",
      "IMPORT_INTERNAL_SSL_CERT",
      "EXPORT_INTERNAL_SSL_CERT",
    ]
  };

  res.status(200).send(userData);
});


app.post('/login', function (req, res) {
  console.log("Login");

  var loginData = req.body;
  console.log(loginData);

  // Some waiting here to "simulate" real backend and show spinners/progressbars etc. in UI
  setTimeout(function () {
    if (loginData.username === 'xrd') {
      res.status(200).send("Successfully logged in");
    }
    else if (loginData.username === 'admin') {
      res.status(200).send("Successfully logged in");
    }
    else {
      res.status(401).send("Oh noes!");
    }
  }, 1000);

});


app.get('*', function (req, res) {
  console.log("GET From SERVER");

  // Some waiting here to "simulate" real backend and show spinners/progressbars etc. in UI
  setTimeout(function () {
    res.send('ok');
    //res.status(501).send("Oh noes!");
  }, 2000);
});

app.listen(port, () => console.log(`Mock server listening on port ${port}`))

