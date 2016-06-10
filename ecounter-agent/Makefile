IDIR = -I/Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home/include/ -I/Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home/include/darwin/
CC=g++
CFLAGS = -std=c++11 -Wall -shared $(IDIR)
ODIR=obj

_OBJ = exception-counter.o 
OBJ = $(patsubst %,$(ODIR)/%,$(_OBJ))

$(ODIR)/%.o: %.cpp
	$(CC) -c -o $@ $< $(CFLAGS)
libecounter.dylib: $(OBJ)
	$(CC) -o $@ $^ $(CFLAGS)

.PHONY: clean

clean:
	rm -f $(ODIR)/*.o libecounter.dylib
