#!/usr/bin/env python
import sys
import ast
import logging

PYTHON2 = (sys.version_info[0] == 2)

class FeatExtractor:

    def __init__(self):
        self.feats = []
        return

    def add(self, t, name):
        self.feats.append((t, name))
        return

    def get_feats(self):
        return self.feats

    def walk_assn(self, tree):
        assert isinstance(tree, ast.expr), ast
        if isinstance(tree, ast.Name):
            self.add('v', tree.id)
        elif isinstance(tree, ast.Tuple):
            for t in tree.elts:
                self.walk_assn(t)
        elif isinstance(tree, ast.List):
            for t in tree.elts:
                self.walk_assn(t)
        elif isinstance(tree, ast.Attribute):
            self.add('v', tree.attr)
        return

    def walk_stmt2(self, tree):
        assert isinstance(tree, ast.stmt), ast
        if isinstance(tree, ast.ClassDef):
            self.add('t', tree.name)
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.FunctionDef):
            self.add('f', tree.name)
            for a in tree.args.args:
                self.walk_assn(a)
            if tree.args.vararg is not None:
                self.add('v', tree.args.vararg)
            if tree.args.kwarg is not None:
                self.add('v', tree.args.kwarg)
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.ExceptHandler):
            self.walk_assn(tree.name)
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.For):
            self.walk_assn(tree.target)
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.If):
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.While):
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.With):
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.Assign):
            for t in tree.targets:
                self.walk_assn(t)
        elif isinstance(tree, ast.TryExcept):
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.TryFinally):
            for t in tree.body:
                self.walk_stmt2(t)
        return

    def walk_stmt3(self, tree):
        assert isinstance(tree, ast.stmt), ast
        if isinstance(tree, ast.ClassDef):
            self.add('t', tree.name)
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.FunctionDef):
            self.add('f', tree.name)
            for a in tree.args.posonlyargs:
                self.add('v', a.arg)
            for a in tree.args.args:
                self.add('v', a.arg)
            if tree.args.vararg is not None:
                self.add('v', tree.args.vararg.arg)
            for a in tree.args.kwonlyargs:
                self.add('v', a.arg)
            if tree.args.kwarg is not None:
                self.add('v', tree.args.kwarg.arg)
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.ExceptHandler):
            self.add('v', tree.name)
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.For):
            self.walk_assn(tree.target)
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.If):
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.While):
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.With):
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.Assign):
            for t in tree.targets:
                self.walk_assn(t)
        elif isinstance(tree, ast.Try):
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.AnnAssign):
            self.walk_assn(tree.target)
        return

    if PYTHON2:
        walk_stmt = walk_stmt2
    else:
        walk_stmt = walk_stmt3

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [file ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    output = None
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-o': output = v
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    for path in args:
        with open(path, 'rb') as fp:
            text = fp.read()
        try:
            tree = ast.parse(text, path)
        except SyntaxError:
            print('! '+path)
            print('')
            continue
        assert isinstance(tree, ast.Module)
        extractor = FeatExtractor()
        for t in tree.body:
            extractor.walk_stmt(t)
        print('+ '+path)
        for (t,name) in extractor.get_feats():
            print(t+name)
        print('')
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
