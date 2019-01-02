
var express = require('express');
var bodyParser = require('body-parser');
var app = express();

//Allow all requests from all domains & localhost
app.all('/*', function (req, res, next) {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, Accept");
  res.header("Access-Control-Allow-Methods", "POST, GET");
  next();
});

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: false }));

var cities = [
  { "id": 1, "name": "Tampere" }, { "id": 2, "name": "Ylojarvi" }, { "id": 3, "name": "Helsinki" }, { "id": 4, "name": "Vantaa" }, { "id": 5, "name": "Nurmes" }
];


app.get('/test-api/cities', function (req, res) {
  console.log("GET From SERVER");
  res.send(cities);
});

app.get('*', function (req, res) {
  console.log("GET From SERVER");

  // Some waiting here to "simulate" real backend and show spinners/progressbars etc. in UI
  setTimeout(function () {
    res.send(cities);
    //res.status(501).send("Oh noes!");
  }, 2000);
});

app.post('/login', function (req, res) {

  var loginData = req.body;
  console.log(loginData);

  // Some waiting here to "simulate" real backend and show spinners/progressbars etc. in UI
  setTimeout(function () {

    if (loginData.username === 'user') {
      res.status(200).send("Successfully logged in");
    }
    else {
      res.status(401).send("Oh noes!");
    }
  }, 1000);

});

app.listen(6069);