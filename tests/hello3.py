import sys
class Hello:
    @staticmethod
    def main(args, *varargs, **kwargs):
        name = args[0]
        print(f"hello, {name}")
if __name__ == '__main__': Hello.main(sys.argv[1:])
