#!/bin/bash

awk -v threadName=\"$1 \
'BEGIN {
  tbeginStr = sprintf("%s", threadName);
  printStack = 0;
}
{
op = printStack;
ts = 0;
printStack = printStack && (index($0, "\t") == 1 || ts = index($0, "java.lang.Thread.State:"));
if (!printStack) {
  if (op) {
    printf "\n";
  }
  printStack = index($0, tbeginStr) == 1;
}
if (printStack && op && !ts) {
  toPrint = substr($0, 2);
  if (index(toPrint, "at ") == 1) {
    printf "%s\n", toPrint;
  }
}
}
END {
}'
