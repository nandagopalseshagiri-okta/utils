var args = process.argv;

if (args.length != 4) {
	console.log("Usage: need to provide the key file and ip as command line arguments (in that order).")
	process.exit(-1);
}

var keyFile = args[2];
var ip = args[3];

var crypto = require('crypto');
var fs = require('fs');

var pem = fs.readFileSync(keyFile);
var key = pem.toString('ascii');

var sign = crypto.createSign('RSA-SHA1');
sign.update(ip);  // data from your file would go here
var sig = sign.sign(key, 'hex');
console.log(sig);