import sys

def foo(p):
    return p

class Person(object):

    def __init__(self, x):
        y = x
        self.name = y
        return

    def greet(self, msg):
        return msg+", "+self.name

    def foo(self, p):
        return p

class Hello:

    @staticmethod
    def main(args, *varargs, **kwargs):
        name = args[0]
        p = Person(name)
        print(p.greet("hello"))
        print(p.name)
        q = p.foo(p)
        r = foo(p=q)
        return

if __name__ == '__main__': Hello.main(sys.argv[1:])
