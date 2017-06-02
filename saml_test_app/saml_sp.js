var http = require('http');
var fs = require('fs');

if (process.argv.length < 3) {
	console.log('please specify the URL to post saml request to');
	process.exit();
}

var samlAuthn = new Buffer(fs.readFileSync('saml_authn.xml', 'utf8')).toString('base64');
var postURL = process.argv[2];
//'http://rain.okta1.com:1802/app/raincloud59_testsamlsp_1/exki9q0DKydC2p8xO0g3/sso/saml';

var port = 3000;
var htmlHead = '<html xmlns="http://www.w3.org/1999/xhtml">' + 
'<body onload="document.forms.samlform.submit()"><form id="samlform" action="'+ postURL +'" method="post">' +
'<div><input type="hidden" name="password" value="OhMyGodIsVisible"/><input type="hidden" name="SAMLRequest" value="'
var htmlTail = '"/></div><noscript><div><input type="submit" value="Continue"/></div></noscript></form></body></html>'
http.createServer(function (request, response) {
	console.log(request.url);
	var samlResponse = [];
	request.setEncoding('utf8');
	request.on('data', function (data) {
		samlResponse.push(data);
		process.stdout.write(data);
	});
	request.on('end', function () {
		samlResponse = samlResponse.join('');
		var ename = 'SAMLResponse='
		var i = samlResponse.indexOf(ename);
		var hasSignature = false;
		if (i >= 0) {
			var j = samlResponse.indexOf('&');
			i = i + ename.length;
			samlResponse = samlResponse.substring(i, j > i ? j : samlResponse.length);
			samlResponse = new Buffer(unescape(samlResponse), 'base64').toString('utf8');
			hasSignature = samlResponse.indexOf('signature') > 0;
		}

		// console.log(samlResponse);

	  response.writeHead(200, {'Content-Type': 'text/html'});
	  if (hasSignature) {
	  	console.log('\nhas signature. not sending the request.')
	  } else {
		  response.write(htmlHead);
		  response.write(samlAuthn);
		  response.write(htmlTail);
		}
	  response.end();
	});
}).listen(port);

console.log('Server running at http://127.0.0.1:%d/', port);
