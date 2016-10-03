function match(node, istack, indexToBeginMatchFrom) {
	var lcIndex = -1, longestMatchLen = 0;
	for (var i = 0; i < node.c.length; ++i) {
		var child = node.c[i];
		if (child === undefined) {
			continue;
		}
		var newIndex = matchStack(istack, child.stack, indexToBeginMatchFrom);
		if (indexToBeginMatchFrom - newIndex > longestMatchLen) {
			longestMatchLen = indexToBeginMatchFrom - newIndex;
			lcIndex = i;
		}
	}
	if (lcIndex < 0) {
		node.c.push({stack : istack.slice(0, indexToBeginMatchFrom + 1), c: [], matchCount : 1});
		return node; 
	}
	
	var longestMatchingChild = node.c[lcIndex];

	if (longestMatchingChild.stack.length > longestMatchLen) {
		var newChildStack = longestMatchingChild.stack.slice(0, longestMatchingChild.stack.length - longestMatchLen);
		var newParentStack = longestMatchingChild.stack.slice(longestMatchingChild.stack.length - longestMatchLen);
		var newParent = {stack: newParentStack, c: [longestMatchingChild], matchCount: longestMatchingChild.matchCount};
		longestMatchingChild.stack = newChildStack;

		delete node.c[lcIndex];
		node.c.push(newParent);
		longestMatchingChild = newParent;
	}

	longestMatchingChild.matchCount = ++(longestMatchingChild.matchCount);

	if (indexToBeginMatchFrom >= longestMatchLen) {
		indexToBeginMatchFrom -= longestMatchLen;
		match(longestMatchingChild, istack, indexToBeginMatchFrom);
	}
}

function matchStack(left, right, fromIndex) {
	var i = fromIndex, j = right.length - 1;
	for (; i >= 0 && j >= 0; --i, --j) {
		if (left[i] !== right[j]) {
			break;
		}
	}

	return i;
}

var errorCounter = 0;

function logError(errorMsg) {
	++errorCounter;
	console.error(errorMsg);
}

function assert(condition) {
	var result = condition();
	if (!result) {
		logError("Failed: " + condition)
	}
}

function test1() {
	var r = {stack: [], c: []};
	var inputStack = ["f3", "f2", "f1", "main"];
	match(r, inputStack, inputStack.length - 1);
	assert(function () {return r.c.length == 1;});
	assert(function () {return r.c[0].stack.length == inputStack.length && r.c[0].stack[0] === "f3";});
}

function nonNullCount(arr) {
	var c = 0;
	for (a in arr) {++c};
	return c;
}

function firstNonNull(arr) {
	for(a in arr) {return arr[a];}
}

function test2() {
	var r = {stack: [], c: []};
	var inputStack = ["f3", "f2", "f1", "main"];
	match(r, inputStack, inputStack.length - 1);
	var inputStack2 = ["f4", "f2", "f1", "main"];

	match(r, inputStack2, inputStack2.length - 1);
	assert(function () {return nonNullCount(r.c) == 1 && nonNullCount(firstNonNull(r.c).c) == 2;});
	return r;
}

function test3() {
	var r = test2();
	var inputStack = ["f5", "f22", "f1", "main"];
	match(r, inputStack, inputStack.length - 1);

	assert(function () {
		return nonNullCount(r.c) == 1 && firstNonNull(r.c).stack.length == 2 &&
		firstNonNull(r.c).stack[0] == "f1" && firstNonNull(r.c).stack[1] == "main" &&
		nonNullCount(firstNonNull(r.c).c) == 2;
	});
	console.log(firstNonNull(r.c));
}

function runTests() {
	console.log("Running... tests")

	test1();
	test2();
	test3();

	if (errorCounter == 0) {
		console.log("All Passed!!");
	}
}

// runTests();
function onLine(line) {
	line = line.trim();
	if (line.length <= 0 && stackInput.length > 0) {
		match(r, stackInput, stackInput.length - 1);
		stackInput = [];
		return;
	}
	stackInput.push(line);
}

var stackInput = [];
var r = {stack: [], c: []};
var inputLinePart = [];
process.stdin.setEncoding('utf8');
process.stdin.on('data', function (chunk) {
	inputLinePart.push(chunk);
	chunk = inputLinePart.join('');
	var i = chunk.indexOf('\n');
	if (i < 0) {
		inputLinePart = [chunk];
		return;
	}

	inputLinePart = [];
	var endWithSlashN = chunk.charAt(chunk.length - 1) == '\n';
	var lines = chunk.split('\n');
	for (var j = 0; j < lines.length - 1; ++j) {
		onLine(lines[j]);	
	}
	if (endWithSlashN) {
		onLine(lines[lines.length - 1]);
	} else {
		inputLinePart = [lines[lines.length - 1]];
	}
});

process.stdin.on('close', function () {
	console.log(JSON.stringify(r));
});
