//var apiToken = '00_JdmFBMZxZwvkpcrvWW7hpFoUDOB06wGeRoArtAC';
//var oktaBaseUrl = 'https://ycao.trexcloud.com';
//var pushFactorId = 'opf1z4hijzxyRh6ci0g7';
//var apiToken = '00iy18eRe3JMt2H5EYMJKtu3gl7AVWwrUzxJjKMGl7';
//var oktaBaseUrl = 'https://ct10-amex-test.clouditude.com';
//var pushFactorId = 'opfj7f4iP1FxWlrsE4g5';

var apiToken = '00YHCvYFWWEEhicDpFBJU1Y71l0aBnDmUASiNdLzlM';
var oktaBaseUrl = 'https://mmanduri.trexcloud.com';
var allpushFactorIds = ['opf1z5ubhgkWJiT500g7'];
var txId = 'test';

var privateKeyStr = '-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDfdOqotHd55SYO\n0dLz2oXengw/tZ+q3ZmOPeVmMuOMIYO/Cv1wk2U0OK4pug4OBSJPhl09Zs6IwB8N\nwPOU7EDTgMOcQUYB/6QNCI1J7Zm2oLtuchzz4pIb+o4ZAhVprLhRyvqi8OTKQ7kf\nGfs5Tuwmn1M/0fQkfzMxADpjOKNgf0uy6lN6utjdTrPKKFUQNdc6/Ty8EeTnQEwU\nlsT2LAXCfEKxTn5RlRljDztS7Sfgs8VL0FPy1Qi8B+dFcgRYKFrcpsVaZ1lBmXKs\nXDRu5QR/Rg3f9DRq4GR1sNH8RLY9uApMl2SNz+sR4zRPG85R/se5Q06Gu0BUQ3UP\nm67ETVZLAgMBAAECggEADjU54mYvHpICXHjc5+JiFqiH8NkUgOG8LL4kwt3DeBp9\nbP0+5hSJH8vmzwJkeGG9L79EWG4b/bfxgYdeNX7cFFagmWPRFrlxbd64VRYFawZH\nRJt+2cbzMVI6DL8EK4bu5Ux5qTiV44Jw19hoD9nDzCTfPzSTSGrKD3iLPdnREYaI\nGDVxcjBv3Tx6rrv3Z2lhHHKhEHb0RRjATcjAVKV9NZhMajJ4l9pqJ3A4IQrCBl95\nux6Xm1oXP0i6aR78cjchsCpcMXdP3WMsvHgTlsZT0RZLFHrvkiNHlPiil4G2/eHk\nwvT//CrcbO6SmI/zCtMmypuHJqcr+Xb7GPJoa64WoQKBgQDwrfelf3Rdfo9kaK/b\nrBmbu1++qWpYVPTedQy84DK2p3GE7YfKyI+fhbnw5ol3W1jjfvZCmK/p6eZR4jgy\nJ0KJ76z53T8HoDTF+FTkR55oM3TEM46XzI36RppWP1vgcNHdz3U4DAqkMlAh4lVm\n3GiKPGX5JHHe7tWz/uZ55Kk58QKBgQDtrkqdSzWlOjvYD4mq4m8jPgS7v3hiHd+1\nOT8S37zdoT8VVzo2T4SF+fBhI2lWYzpQp2sCjLmCwK9k/Gur55H2kTBTwzlQ6WSL\nTe9Zj+eoMGklIirA+8YdQHXrO+CCw9BTJAF+c3c3xeUOLXafzyW29bASGfUtA7Ax\nQAsR+Rr3+wKBgAwfZxrh6ZWP+17+WuVArOWIMZFj7SRX2yGdWa/lxwgmNPSSFkXj\nhkBttujoY8IsSrTivzqpgCrTCjPTpir4iURzWw4W08bpjd7u3C/HX7Y16Uq8ohEJ\nT5lslveDJ3iNljSK74eMK7kLg7fBM7YDogxccHJ1IHsvInp3e1pmZxOxAoGAO+bS\nTUQ4N/UuQezgkF3TDrnBraO67leDGwRbfiE/U0ghQvqh5DA0QSPVzlWDZc9KUitv\nj8vxsR9o1PW9GS0an17GJEYuetLnkShKK3NWOhBBX6d1yP9rVdH6JhgIJEy/g0Su\nz7TAFiFc8i7JF8u4QJ05C8bZAMhOLotqftQeVOMCgYAid8aaRvaM2Q8a42Jn6ZTT\n5ms6AvNr98sv0StnfmNQ+EYXN0bEk2huSW+w2hN34TYYBTjViQmHbhudwwu8lVjE\nccDmIXsUFbHVK+kTIpWGGchy5cYPs3k9s1nMR2av0Lojtw9WRY76xRXvN8W6R7Eh\nwA2ax3+gEEYpGhjM/lO2Lg==\n-----END PRIVATE KEY-----\n';


var rs = require('jsrsasign');
var request = require('request');

function generateJwtToken(event, context) {
  var request = event.body;
  var timestamp = Math.floor(Date.now() / 1000);
  var payload = {
    iss: request.factorId,
    sub: request.userId,
    aud: request.domain,
    jti: timestamp, // not validated, should be UUID
    iat: timestamp,
    exp: (timestamp + (2 * 60)),
    nbf: (timestamp - (2 * 60)),
    tx: request.txId,
    result: 'APPROVE'
  };

  var sClaim = JSON.stringify(payload);
  var pHeader = {'alg': 'RS256', 'typ': 'JWT'};
  var sHeader = JSON.stringify(pHeader);
  var sJWS = rs.jws.JWS.sign(null, sHeader, sClaim, request.privateKey);
  var responseBody = {
    jwt_token: sJWS
  };
  return responseBody;
}


function getJwtToken(transactionId, pushFactorId) {
  var event = {
    body: {
      factorId: pushFactorId,
      userId: "me",
      txId: transactionId,
      privateKey: privateKeyStr,
      domain: oktaBaseUrl
    }
  }
  var result = generateJwtToken(event, null);
  //console.log("Successfull created jwt token " + result.jwt_token);
  return result.jwt_token;
}

function getBootStrapJwtToken(pushFactorId) {
  return getJwtToken('test', pushFactorId);
}

function getPendingNotifications(callback, pushFactorId) {
  var options = {
    url: oktaBaseUrl + '/api/v1/users/me/factors/'+pushFactorId +'/notifications',
    headers: {
      'Authorization': 'SSWS ' + getBootStrapJwtToken(pushFactorId)
    }
  };
  console.log("Sending request to get notifications");
  request(options, function(e, r, b) {
    var notes = notificationsReader(e, r, b);
    if (notes === null ) { //|| notes.length == null || notes.length <= 0) {
      console.log("returned notifications is null or empty");
      return;
    }
    if (!callback) {
      return;
    }

    for (var a in notes) {
      callback(notes[a], pushFactorId);
    }
  });
}

function notificationsReader(error, response, body) {
  if (error || response.statusCode != 200) {
    console.log("Error getting notifications " + error + " response code = " + response.statusCode);
    return;
  }

  console.log("notifications response body = " + body);
  return JSON.parse(body);
}

function notificationsActor(note, pushFactorId) {
  if (note === null || note.transactionId === null) {
    return;
  }
  var jwtToken = getJwtToken(note.transactionId, pushFactorId);

  var bodyJson = {
    "transactionId": note.transactionId,
    "transactionType": "LOGIN",
    "factorId": pushFactorId,
    "result": "APPROVE"
  };

  var options = {
    url: oktaBaseUrl + '/api/v1/authn/factors/' + pushFactorId + '/transactions/' + note.transactionId + '/verify',
    headers: {
      'Authorization': 'SSWS ' + jwtToken,
      'Content-Type' : 'application/json'

    },
    body: JSON.stringify(bodyJson)
  };
  request.post(options, function (e, r, b) {
    if (e || r.statusCode != 204) {
      console.log('failed to send verification response. error = ' + e + ' statusCode=' + r.statusCode);
      return;
    }
    console.log("successfully sent transaction verification for push");
  });
}
setInterval(function() {
    for (var i = 0; i < allpushFactorIds.length; ++i) {
      getPendingNotifications(notificationsActor, allpushFactorIds[i]);
    }
  }
  , 1000);
