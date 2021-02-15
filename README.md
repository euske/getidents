# GetIdents

Extracts identifiers from Java/Python source code.

## What It Does

### Input (Java)

    public class Hello {
        public static void main(String[] args) {
            String name = args[0];
            System.out.println("Hello, "+name);
        }
    }

### Output (Java)

    + Hello.java
    t Hello
    f main
    v args
    v name

### Input (Python)

    import sys
    class Hello:
        @staticmethod
        def main(args):
            name = args[0]
            print(f"hello, {name}")
    if __name__ == '__main__': Hello.main(sys.argv[1:])

### Output (Python)

    + hello.py
    t Hello
    f main
    v args
    v name

## How to Build

### Prerequisites

  * Python
  * Java
  * Ant
  * Eclipse JDT (automatically downloaded)

### Compiling

    $ ant get-deps clean build


## How to Use

### Java

    $ ./java/extractIdent.sh tests/Hello.java

### Python2

    $ python2 ./python/getidents.py tests/hello2.py

### Python3

    $ python3 ./python/getidents.py tests/hello3.py
