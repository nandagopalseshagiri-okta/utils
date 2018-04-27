var program = require('commander');
program
	.option('-l, --lport <port>', 'left incoming',parseInt)
	.option('-r, --rport <port>', 'right incoming', parseInt)
	.parse(process.argv);

if (program.lport === null || program.rport === null || !program.lport || !program.rport) {
	console.log('please specify valid left and right incoming ports');
	process.exit(1);
}

var net = require('net');

function leftPort(port) {
  var queue = [];
  var server = net.createServer(function(lc) {
    queue.push(lc);
    lc.on('end', function() {
      console.log('[LEFT] client disconnected');
    });
    lc.on('error', function (e) {
      console.log('[LEFT] error occured on incoming end of pipe. Info = ' + e.toString());
    });
    lc.on('timeout', function (e) {
      console.log('[LEFT] timeout trying to connect to remote. ' + e.toString());
    });
  });

  server.on('error', function (e) {
    console.log('[LEFT] server error - ' + e.toString());
  });
  
  server.listen(port, function() {
    console.log('[LEFT] server bound');
  });
  return queue;
}

var MAX_WAIT_COUNT = 5;

function pickAndJoinConnectionFromQueueOrWait(queue, rc, count) {
  if (queue.length <= 0) {
    if (count >= MAX_WAIT_COUNT) {
      console.log('[RIGHT] reached/exceeded max wait count waiting for left connection. waitCount = ' + count);
      rc.end();
      return;
    }
    setTimeout(pickAndJoinConnectionFromQueueOrWait, 1000, queue, rc, ++count);
    return;
  }
  var lc = queue.pop();
  lc.pipe(rc);
  rc.pipe(lc);
}

function rightPort(port, queue) {
  var server = net.createServer(function(rc) {
    rc.on('end', function() {
      console.log('[RIGHT] client disconnected');
    });
    rc.on('error', function (e) {
      console.log('[RIGHT] error occured on incoming end of pipe. Info = ' + e.toString());
    });
    rc.on('timeout', function (e) {
      console.log('[RIGHT] timeout trying to connect to remote. ' + e.toString());
    });

    pickAndJoinConnectionFromQueueOrWait(queue, rc, 0);
  });

  server.on('error', function (e) {
    console.log('[RIGHT] server error - ' + e.toString());
  });
  
  server.listen(port, function() {
    console.log('[RIGHT] server bound');
  });
}

var queue = leftPort(program.lport);
rightPort(program.rport, queue);
