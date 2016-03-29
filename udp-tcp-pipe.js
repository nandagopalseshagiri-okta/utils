const dgram = require('dgram');
// var radius = require('radius');
var program = require('commander');
var net = require('net');


function createServerStream(ip, port, cb) {
  net.createServer(function (stream) {
    cb(stream);
  }).listen(port);
}

function createClientStream(ip, port, cb) {
  var client = new net.Socket();
  client.connect(port, ip, function() {
    cb(client);
  });
}

function createStream(ip, port, cb) {
  if (!ip) {
    createServerStream(ip, port, cb);
    return;
  }
  createClientStream(ip, port, cb);
}

function setupDataExchange(udpSock, delimiterBytes, ipArg, portArg, stream) {
  var udpRemote = [ipArg, portArg];

  udpSock.on('message', (msg, rinfo) => {
    if (!!udpRemote[0]) {
      if (rinfo.address != udpRemote[0] && rinfo.port != udpRemote[1]) {
        console.log(`udp message from from ${rinfo.address}:${rinfo.port} unexpected`);
        return;
      }
    } else {
      udpRemote[0] = rinfo.address;
      udpRemote[1] = rinfo.port;
    }
    console.log(`Received message on udp from from ${rinfo.address}:${rinfo.port}`);
    stream.write(msg, 'buffer', () => {
      stream.write(delimiterBytes);
    });
  });

  var message = [new Buffer(0)];
  stream.on('data', function (data) {
    console.log('received data of length ' + (data != null ? data.length : null));
    message[0] = Buffer.concat([message[0], data]);
    var i = message[0].indexOf(delimiterBytes);
    if (i < 0 || !udpRemote[0]) {
      return;
    }
    var m = message[0].slice(0, i);
    message[0] = new Buffer(0);
    console.log('sending message of length ' + m.length);
    udpSock.send(m, 0, m.length, udpRemote[1], udpRemote[0]);
    if (ipArg == null) {
      udpRemote[0] = null;
      udpRemote[1] = null;
    }
  });
}

function listen_udp_tcp(udpPort, tcpPort, delimiterBytes) {
  var udpSock = dgram.createSocket('udp4');
  udpSock.on('error', (err) => {
    console.log(`udp socket listener on port ${udpPort} got a error:\n${err.stack}`);
    udpSock.close();
  });

  udpSock.bind(udpPort);
  createStream(null, tcpPort, setupDataExchange.bind(null, udpSock, delimiterBytes, null, null));
}

function connect_to_tcp_and_forward_to_udp(tcpRemote, udpRemote, delimiterBytes) {
  var udpSock = dgram.createSocket('udp4');
  udpSock.on('error', (err) => {
    console.log(`udp sender got a error:\n${err.stack}`);
    udpSock.close();
  });

  udpSock.bind();
  createStream(tcpRemote.address, tcpRemote.port, 
    setupDataExchange.bind(null, udpSock, delimiterBytes, udpRemote.address, udpRemote.port));
}

program
  .version('0.0.1')
  .option('-u, --udphostport [remote-or-port]', 'Upd port to listen on or upd host:port remote to forward to')
  .option('-t, --tcphostport [remote-or-port]', 'Tcp port to listen on or tcp host:port remote to get data from')
  .option('-b, --random-boundary [random-boundary]', 'Random boundary to use as message boundary on the stream')
  //.option('-s, --radiussecret [secret]', 'Radius secret to use')
  .parse(process.argv)

if (!program.udphostport || typeof(program.udphostport) === "boolean" ||
    !program.tcphostport || typeof(program.tcphostport) === "boolean" ||
    !program.randomBoundary) {
  program.help();
}

var udp_hp = program.udphostport.split(':');
var updremote = {address: udp_hp.length > 1 ? udp_hp[0] : null, port: parseInt(udp_hp.length > 1 ? udp_hp[1] : udp_hp[0]) };

var tcp_hp = program.tcphostport.split(':');
var tcpremote = {address: tcp_hp.length > 1 ? tcp_hp[0] : null, port: parseInt(tcp_hp.length > 1 ? tcp_hp[1] : tcp_hp[0]) };

program.randomBoundary = new Buffer(program.randomBoundary);

if (udp_hp.length > 1 && tcp_hp.length > 1) {
  connect_to_tcp_and_forward_to_udp(tcpremote, updremote, program.randomBoundary);
} else if (udp_hp.length == 1 && tcp_hp.length == 1) {
  listen_udp_tcp(updremote.port, tcpremote.port, program.randomBoundary);
} else {
  console.log("Either specify remote ip or not for both udp and tcp.");
  process.exit(1);
}

// const server = dgram.createSocket('udp4');
// var radius_secret = 'password';

// server.on('error', (err) => {
//   console.log(`server error:\n${err.stack}`);
//   server.close();
// });

// server.on('message', (msg, rinfo) => {
//   var decoded = radius.decode({ packet: msg, secret:  radius_secret});
//   console.log(`server got: %j from ${rinfo.address}:${rinfo.port}`, decoded);
// });

// server.on('listening', () => {
//   var address = server.address();
//   console.log(`server listening ${address.address}:${address.port}`);
// });

// server.bind(port);
