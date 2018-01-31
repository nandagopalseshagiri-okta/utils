var program = require('commander');
program
  .option('-l, --left <host:port>', 'left host to pipe to')
  .option('-r, --right <host:port>', 'right host to pipe to')
  .parse(process.argv);

function validated(argName) {
  var item = program[argName] != null ? program[argName].toString().split(':') : [];
  if (item.length != 2 || parseInt(item[1]) <= 0) {
    console.log('please specify a ' + argName + ' host to pipe to');
    process.exit(1);
  }
  return item;
}

var right = validated("right");
var left = validated("left");

var net = require('net');

function connect(remote, ondata, onconnect) {
  var rm = new net.Socket();
	rm.on('error', function (e) {
		console.log('error connecting to remote. ' + e.toString());
	});
	rm.connect(parseInt(remote[1]), remote[0],
		function () {
			if (onconnect) { onconnect(rm) };
    });
  rm.on('data', function(data) {
    if (ondata) {
      console.log('Reacting to data arrival');
      ondata(data, rm) };
  });
  rm.on('end', function () {
    console.log('connection ended - starting one more connection');
    connectRight();
  });
}

function connectRight() {
  connect(right, function (data, rightConnection) {
    rightConnection.removeAllListeners('data');
    connect(left, null, function (leftConnection) {
      leftConnection.write(data);
      rightConnection.pipe(leftConnection);
      leftConnection.pipe(rightConnection);
    });
    setImmediate(connectRight);
  });
}

connectRight();
