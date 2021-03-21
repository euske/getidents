#!/usr/bin/env python
import sys
import ast
import logging

PYTHON2 = (sys.version_info[0] == 2)

class Namespace:

    def __init__(self, parent, name):
        self.parent = parent
        self.name = name
        return


class FeatExtractor:

    def __init__(self, ns):
        self.feats = []
        self.ns = ns
        return

    def add(self, t, name):
        assert isinstance(name, str)
        self.feats.append((t, name))
        return

    def push(self, name):
        self.ns = Namespace(self.ns, name)
        return

    def pop(self):
        assert self.ns is not None
        self.ns = self.ns.parent
        return

    def get_feats(self):
        return self.feats

    def walk_expr(self, tree):
        return

    def walk_assn(self, tree):
        assert isinstance(tree, ast.expr), ast
        if isinstance(tree, ast.Name):
            self.add('V', tree.id)
        elif isinstance(tree, ast.Tuple):
            for t in tree.elts:
                self.walk_assn(t)
        elif isinstance(tree, ast.List):
            for t in tree.elts:
                self.walk_assn(t)
        elif isinstance(tree, ast.Attribute):
            self.add('V', tree.attr)
            self.walk_expr(tree.value)
        elif isinstance(tree, ast.Subscript):
            self.walk_expr(tree.value)
            #self.walk_expr(tree.slice) # python2/3 incompatible.
        return

    def walk_type(self, tree):
        assert isinstance(tree, ast.expr), ast
        if isinstance(tree, ast.Name):
            self.add('t', tree.id)
        elif isinstance(tree, ast.Tuple):
            for t in tree.elts:
                self.walk_type(t)
        elif isinstance(tree, ast.List):
            for t in tree.elts:
                self.walk_type(t)
        elif isinstance(tree, ast.Attribute):
            self.add('t', tree.attr)
            self.walk_expr(tree.value)
        return

    def walk_stmt2(self, tree):
        assert isinstance(tree, ast.stmt), ast
        if isinstance(tree, ast.FunctionDef):
            self.add('F', tree.name)
            self.push(tree.name)
            for a in tree.args.args:
                self.walk_assn(a)
            if tree.args.vararg is not None:
                self.add('V', tree.args.vararg)
            if tree.args.kwarg is not None:
                self.add('V', tree.args.kwarg)
            for t in tree.args.defaults:
                self.walk_expr(t)
            for t in tree.body:
                self.walk_stmt2(t)
            self.pop()
        elif isinstance(tree, ast.ClassDef):
            self.add('T', tree.name)
            self.push(tree.name)
            for t in tree.bases:
                self.walk_type(t)
            for t in tree.body:
                self.walk_stmt2(t)
            self.pop()
        elif isinstance(tree, ast.Return):
            if tree.value is not None:
                self.walk_expr(tree.value)
        elif isinstance(tree, ast.Delete):
            for t in tree.targets:
                self.walk_assn(t)
        elif isinstance(tree, ast.Assign):
            self.walk_expr(tree.value)
            for t in tree.targets:
                self.walk_assn(t)
        elif isinstance(tree, ast.AugAssign):
            self.walk_expr(tree.value)
            self.walk_assn(tree.target)
        elif isinstance(tree, ast.Print):
            if tree.dest is not None:
                self.walk_expr(tree.dest)
            for t in tree.values:
                self.walk_expr(t)
        elif isinstance(tree, ast.For):
            self.walk_assn(tree.target)
            self.walk_expr(tree.iter)
            for t in tree.body:
                self.walk_stmt2(t)
            for t in tree.orelse:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.While):
            self.walk_expr(tree.test)
            for t in tree.body:
                self.walk_stmt2(t)
            for t in tree.orelse:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.If):
            self.walk_expr(tree.test)
            for t in tree.body:
                self.walk_stmt2(t)
            for t in tree.orelse:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.With):
            self.walk_expr(tree.context_expr)
            if tree.optional_vars is not None:
                self.walk_assn(tree.optional_vars)
            for t in tree.body:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.Raise):
            if tree.type is not None:
                self.walk_type(tree.type)
            if tree.inst is not None:
                self.walk_expr(tree.inst)
            if tree.tback is not None:
                self.walk_expr(tree.tback)
        elif isinstance(tree, ast.TryExcept):
            for h in tree.handlers:
                assert isinstance(h, ast.ExceptHandler)
                if h.type is not None:
                    self.walk_type(h.type)
                if h.name is not None:
                    self.walk_assn(h.name)
                for t in h.body:
                    self.walk_stmt2(t)
            for t in tree.body:
                self.walk_stmt2(t)
            for t in tree.orelse:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.TryFinally):
            for t in tree.body:
                self.walk_stmt2(t)
            for t in tree.finalbody:
                self.walk_stmt2(t)
        elif isinstance(tree, ast.Assert):
            self.walk_expr(tree.test)
        elif isinstance(tree, ast.expr):
            self.walk_expr(tree)
        return

    def walk_stmt3(self, tree):
        assert isinstance(tree, ast.stmt), ast
        if isinstance(tree, ast.ClassDef):
            self.add('T', tree.name)
            self.push(tree.name)
            for t in tree.bases:
                self.walk_type(t)
            for t in tree.body:
                self.walk_stmt3(t)
            self.pop()
        elif isinstance(tree, ast.FunctionDef):
            self.add('F', tree.name)
            self.push(tree.name)
            for a in tree.args.posonlyargs:
                self.add('V', a.arg)
            for a in tree.args.args:
                self.add('V', a.arg)
            if tree.args.vararg is not None:
                self.add('V', tree.args.vararg.arg)
            for a in tree.args.kwonlyargs:
                self.add('V', a.arg)
            if tree.args.kwarg is not None:
                self.add('V', tree.args.kwarg.arg)
            for t in tree.args.defaults:
                self.walk_expr(t)
            for t in tree.args.kw_defaults:
                self.walk_expr(t)
            for t in tree.body:
                self.walk_stmt3(t)
            self.pop()
        elif isinstance(tree, ast.Return):
            if tree.value is not None:
                self.walk_expr(tree.value)
        elif isinstance(tree, ast.Delete):
            for t in tree.targets:
                self.walk_expr(t)
        elif isinstance(tree, ast.Assign):
            for t in tree.targets:
                self.walk_assn(t)
            self.walk_expr(tree.value)
        elif isinstance(tree, ast.AugAssign):
            self.walk_assn(tree.target)
            self.walk_expr(tree.value)
        elif isinstance(tree, ast.AnnAssign):
            self.walk_assn(tree.target)
            if tree.value is not None:
                self.walk_expr(tree.value)
        elif isinstance(tree, ast.For):
            self.walk_assn(tree.target)
            self.walk_expr(tree.iter)
            for t in tree.body:
                self.walk_stmt3(t)
            for t in tree.orelse:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.While):
            self.walk_expr(tree.test)
            for t in tree.body:
                self.walk_stmt3(t)
            for t in tree.orelse:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.If):
            self.walk_expr(tree.test)
            for t in tree.body:
                self.walk_stmt3(t)
            for t in tree.orelse:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.With):
            for w in tree.items:
                assert isinstance(w, ast.withitem)
                self.walk_expr(w.context_expr)
                if w.optional_vars is not None:
                    self.walk_assn(w.optional_vars)
            for t in tree.body:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.Raise):
            if tree.exc is not None:
                self.walk_expr(tree.exc)
            if tree.cause is not None:
                self.walk_expr(tree.cause)
        elif isinstance(tree, ast.Try):
            for h in tree.handlers:
                assert isinstance(h, ast.ExceptHandler)
                if h.name is not None:
                    self.add('V', h.name)
                if h.type is not None:
                    self.walk_type(h.type)
            for t in tree.body:
                self.walk_stmt3(t)
            for t in tree.orelse:
                self.walk_stmt3(t)
            for t in tree.finalbody:
                self.walk_stmt3(t)
        elif isinstance(tree, ast.Assert):
            self.walk_expr(tree.test)
            if tree.msg is not None:
                self.walk_expr(tree.msg)
        elif isinstance(tree, ast.expr):
            self.walk_expr(tree)
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
        root = Namespace(None, 'root')
        extractor = FeatExtractor(root)
        for t in tree.body:
            extractor.walk_stmt(t)
        print('+ '+path)
        for (t,name) in extractor.get_feats():
            print(t+name)
        print('')
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
