#!/usr/bin/env python
import sys
import os.path
import ast
import logging

PYTHON2 = (sys.version_info[0] == 2)

def basename(k):
    (_,_,n) = k.rpartition('.')
    return n

def fupdate(s1, s2):
    if s1.issuperset(s2): return False
    s1.update(s2)
    return True


##  Type Values
##
class TypeVal:
    def __init__(self, name):
        self.name = name
        return
    def __repr__(self):
        return f'<{self.name}>'

class FuncVal:
    def __init__(self, ns, name, retval, args):
        self.ns = ns
        self.name = name
        self.retval = retval
        self.args = args
        return
    def __repr__(self):
        return f'<FuncVal {self.name} {self.retval} {self.args}>'


##  Namespace
##
class Namespace:

    def __init__(self, name, t=None, parent=None):
        self.name = name
        self.t = t
        self.parent = parent
        return

    def __repr__(self):
        return f'<Namespace {self.name} ({self.t})>'

    def path(self):
        a = []
        while self is not None:
            a.append(self.name)
            self = self.parent
        return '.'.join(reversed(a))


##  TreeWalker
##
class TreeWalker:

    def __init__(self, ns):
        self.ns = ns
        self.retval = None
        self._stack = []
        return

    def def_type(self, name):
        return
    def def_func(self, name, retval, args):
        return
    def def_var(self, name, tps=None, vals=None):
        return name
    def use_type(self, name):
        return
    def use_func(self, name, args, kwargs):
        return None
    def use_var(self, name, tps=None):
        return None

    def push(self, name, t):
        self.ns = Namespace(name, t, self.ns)
        self._stack.append(self.retval)
        return

    def pop(self):
        assert self.ns is not None
        assert self._stack
        self.ns = self.ns.parent
        self.retval = self._stack.pop()
        return

    def parse(self, tree):
        assert isinstance(tree, ast.Module)
        for t in tree.body:
            self.walk_stmt(t)
        return

    def walk_expr(self, tree):
        assert isinstance(tree, ast.expr), ast
        if isinstance(tree, ast.BoolOp):
            for t in tree.values:
                self.walk_expr(t)
            return
        elif isinstance(tree, ast.BinOp):
            self.walk_expr(tree.left)
            return self.walk_expr(tree.right)
        elif isinstance(tree, ast.UnaryOp):
            return self.walk_expr(tree.operand)
        elif isinstance(tree, ast.Lambda):
            name = f':lambda:{tree.lineno}:{tree.col_offset}'
            self.push(name, 'F')
            args = self.walk_args3(tree.args)
            retval = self.walk_expr(tree.body)
            self.pop()
            self.def_func(name, retval, args)
            return None
        elif isinstance(tree, ast.Dict):
            for t in tree.keys:
                self.walk_expr(t)
            for t in tree.values:
                self.walk_expr(t)
            return None
        elif isinstance(tree, ast.Set):
            for t in tree.elts:
                self.walk_expr(t)
            return None
        elif isinstance(tree, ast.ListComp):
            self.walk_expr(tree.elt)
            for comp in tree.generators:
                self.walk_expr(comp.target)
                self.walk_expr(comp.iter)
                for t in comp.ifs:
                    self.walk_expr(t)
            return None
        elif isinstance(tree, ast.SetComp):
            self.walk_expr(tree.elt)
            for comp in tree.generators:
                self.walk_expr(comp.target)
                self.walk_expr(comp.iter)
                for t in comp.ifs:
                    self.walk_expr(t)
            return None
        elif isinstance(tree, ast.DictComp):
            self.walk_expr(tree.key)
            self.walk_expr(tree.value)
            for comp in tree.generators:
                self.walk_expr(comp.target)
                self.walk_expr(comp.iter)
                for t in comp.ifs:
                    self.walk_expr(t)
            return None
        elif isinstance(tree, ast.GeneratorExp):
            self.walk_expr(tree.elt)
            for comp in tree.generators:
                self.walk_expr(comp.target)
                self.walk_expr(comp.iter)
                for t in comp.ifs:
                    self.walk_expr(t)
            return None
        elif isinstance(tree, ast.Yield):
            if tree.value is not None:
                self.walk_expr(tree.value)
            return None
        elif isinstance(tree, ast.Compare):
            self.walk_expr(tree.left)
            for t in tree.comparators:
                self.walk_expr(t)
            return None
        elif isinstance(tree, ast.Call):
            func = self.walk_expr(tree.func)
            args = []
            kwargs = {}
            for t in tree.args:
                args.append(self.walk_expr(t))
            for kw in tree.keywords:
                kwargs[kw.arg] = self.walk_expr(kw.value)
            return self.use_func(func, args, kwargs)
        elif isinstance(tree, ast.Name):
            return self.use_var(tree.id)
        elif isinstance(tree, ast.Tuple):
            for t in tree.elts:
                self.walk_expr(t)
            return None
        elif isinstance(tree, ast.List):
            for t in tree.elts:
                self.walk_expr(t)
            return None
        elif isinstance(tree, ast.Attribute):
            vals = self.walk_expr(tree.value)
            return self.use_var(tree.attr, vals)
        elif isinstance(tree, ast.Subscript):
            self.walk_expr(tree.value)
            self.walk_expr(tree.slice) # python2/3 incompatible.
            return None
        elif isinstance(tree, ast.FormattedValue):
            self.walk_expr(tree.value)
            return None
        elif isinstance(tree, ast.JoinedStr):
            for t in tree.values:
                self.walk_expr(t)
            return None
        return None

    def walk_assn(self, tree, vals=None):
        assert isinstance(tree, ast.expr), ast
        if isinstance(tree, ast.Name):
            return self.def_var(tree.id, vals=vals)
        elif isinstance(tree, ast.Tuple):
            for t in tree.elts:
                self.walk_assn(t)
        elif isinstance(tree, ast.List):
            for t in tree.elts:
                self.walk_assn(t)
        elif isinstance(tree, ast.Attribute):
            vals = self.walk_expr(tree.value)
            self.def_var(tree.attr, tps=vals)
        elif isinstance(tree, ast.Subscript):
            self.walk_expr(tree.value)
            #self.walk_expr(tree.slice) # python2/3 incompatible.
        return

    def walk_type(self, tree):
        assert isinstance(tree, ast.expr), ast
        if isinstance(tree, ast.Name):
            self.use_type(tree.id)
        elif isinstance(tree, ast.Tuple):
            for t in tree.elts:
                self.walk_type(t)
        elif isinstance(tree, ast.List):
            for t in tree.elts:
                self.walk_type(t)
        elif isinstance(tree, ast.Attribute):
            vals = self.walk_expr(tree.value)
            self.use_type(tree.attr)
        return

    def walk_stmt2(self, tree):
        assert isinstance(tree, ast.stmt), ast
        if isinstance(tree, ast.FunctionDef):
            self.push(tree.name, 'F')
            args = []
            for a in tree.args.args:
                x = self.walk_assn(a)
                if x is not None:
                    args.append(x)
            if tree.args.vararg is not None:
                self.def_var(tree.args.vararg)
            if tree.args.kwarg is not None:
                self.def_var(tree.args.kwarg)
            for t in tree.args.defaults:
                self.walk_expr(t)
            for t in tree.body:
                self.walk_stmt2(t)
            retval = self.retval
            self.pop()
            self.def_func(tree.name, retval, args)
        elif isinstance(tree, ast.ClassDef):
            self.def_type(tree.name)
            self.push(tree.name, 'T')
            for t in tree.bases:
                self.walk_type(t)
            for t in tree.body:
                self.walk_stmt2(t)
            self.pop()
        elif isinstance(tree, ast.Return):
            if tree.value is not None:
                vals = self.walk_expr(tree.value)
                if vals is not None:
                    if self.retval is None:
                        self.retval = set()
                    self.retval.update(vals)
        elif isinstance(tree, ast.Delete):
            for t in tree.targets:
                self.walk_assn(t)
        elif isinstance(tree, ast.Assign):
            vals = self.walk_expr(tree.value)
            for t in tree.targets:
                self.walk_assn(t, vals)
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
            vals = self.walk_expr(tree.context_expr)
            if tree.optional_vars is not None:
                self.walk_assn(tree.optional_vars, vals)
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
        elif isinstance(tree, ast.Expr):
            self.walk_expr(tree.value)
        return

    def walk_stmt3(self, tree):
        assert isinstance(tree, ast.stmt), ast
        if isinstance(tree, ast.ClassDef):
            self.def_type(tree.name)
            self.push(tree.name, 'T')
            for t in tree.bases:
                self.walk_type(t)
            for t in tree.body:
                self.walk_stmt3(t)
            self.pop()
        elif isinstance(tree, ast.FunctionDef):
            self.push(tree.name, 'F')
            args = self.walk_args3(tree.args)
            for t in tree.body:
                self.walk_stmt3(t)
            retval = self.retval
            self.pop()
            self.def_func(tree.name, retval, args)
        elif isinstance(tree, ast.Return):
            if tree.value is not None:
                vals = self.walk_expr(tree.value)
                if vals is not None:
                    if self.retval is None:
                        self.retval = set()
                    self.changed = fupdate(self.retval, vals)
        elif isinstance(tree, ast.Delete):
            for t in tree.targets:
                self.walk_expr(t)
        elif isinstance(tree, ast.Assign):
            vals = self.walk_expr(tree.value)
            for t in tree.targets:
                self.walk_assn(t, vals)
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
                vals = self.walk_expr(w.context_expr)
                if w.optional_vars is not None:
                    self.walk_assn(w.optional_vars, vals)
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
                    self.def_var(h.name)
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
        elif isinstance(tree, ast.Expr):
            self.walk_expr(tree.value)
        return

    def walk_args3(self, tree):
        args = []
        for a in tree.posonlyargs:
            args.append(self.def_var(a.arg))
        for a in tree.args:
            args.append(self.def_var(a.arg))
        if tree.vararg is not None:
            self.def_var(tree.vararg.arg)
        for a in tree.kwonlyargs:
            args.append(self.def_var(a.arg))
        if tree.kwarg is not None:
            self.def_var(tree.kwarg.arg)
        for t in tree.defaults:
            self.walk_expr(t)
        for t in tree.kw_defaults:
            self.walk_expr(t)
        return args

    if PYTHON2:
        walk_stmt = walk_stmt2
    else:
        walk_stmt = walk_stmt3


##  DefExtractor
##
class DefExtractor(TreeWalker):

    def __init__(self, ns, defs):
        TreeWalker.__init__(self, ns)
        self.defs = defs
        self.feats = []
        return

    def addvar(self, key, value=None):
        if key in self.defs:
            a = self.defs[key]
        else:
            a = self.defs[key] = set()
        if value is not None:
            a.add(value)
        return

    def addfeat(self, feat):
        self.feats.append(feat)
        return

    def def_type(self, name):
        t = TypeVal(name)
        self.addvar(f'{self.ns.path()}.{name}', t)
        self.addfeat(f'T{name}')
        return

    def def_func(self, name, retval, args):
        f = FuncVal(self.ns, name, retval, args)
        self.addvar(f'+{self.ns.name}.{name}', f)
        self.addvar(f'{self.ns.path()}.{name}', f)
        self.addfeat(f'F{name}')
        return

    def def_var(self, name, tps=None, vals=None):
        assert tps is None and vals is None
        if name == 'self' and self.ns.parent.t == 'T':
            t = TypeVal(self.ns.parent.name)
        else:
            t = None
        k = f'{self.ns.path()}.{name}'
        self.addvar(k, t)
        self.addfeat(f'V{name}')
        return k


##  FeatExtractor
##
class FeatExtractor(TreeWalker):

    def __init__(self, ns, defs):
        TreeWalker.__init__(self, ns)
        self.defs = defs
        self.feats = []
        self.changed = False
        return

    def addfeat(self, feat):
        self.feats.append(feat)
        return

    def use_type(self, name):
        self.addfeat(f'u{name}')
        return

    def def_func(self, name, retval, args):
        for k in (f'+{self.ns.name}.{name}', f'{self.ns.path()}.{name}'):
            if k in self.defs:
                for f in self.defs[k]:
                    if not isinstance(f, FuncVal): continue
                    f.retval = retval
        return

    def use_func(self, func, args, kwargs):
        if func is None: return
        funcs = set()
        retval = None
        for tp in func:
            if isinstance(tp, TypeVal):
                if retval is None:
                    retval = set()
                retval.add(tp)
                self.addfeat(f'u{tp.name}')
                k = '+{tp.name}.__init__'
                if k in self.defs:
                    funcs.update(self.defs[k])
            elif isinstance(tp, FuncVal):
                if tp.retval is not None:
                    if retval is None:
                        retval = set()
                    retval.update(tp.retval)
                funcs.add(tp)
        for f in funcs:
            if not isinstance(f, FuncVal): continue
            self.addfeat(f'f{f.name}')
            #print('call', f, args, kwargs)
            if f.ns.t == 'T':
                args0 = f.args[1:]
            else:
                args0 = f.args
            for (k,vals) in zip(args0, args):
                if vals is None: continue
                if k in self.defs:
                    #print('passarg', k, vals)
                    self.changed = fupdate(self.defs[k], vals)
                    self.addfeat(f'a{basename(k)}')
            for (name,vals) in kwargs.items():
                if vals is None: continue
                for k in args0:
                    assert k in self.defs
                    if basename(k) == name:
                        #print('passkwarg', k, vals)
                        self.changed = fupdate(self.defs[k], vals)
                        self.addfeat(f'a{basename(k)}')
                        break
        return retval

    def def_var(self, name, tps=None, vals=None):
        if vals is None: return
        k = None
        if tps:
            for tp in tps:
                if isinstance(tp, TypeVal):
                    k = f'+{tp.name}.{name}'
                    if k in self.defs:
                        #print('assign', k, vals)
                        self.changed = fupdate(self.defs[k], vals)
                        self.addfeat(f'r{tp.name}')
                        self.addfeat(f'a{name}')
        else:
            ns = self.ns
            while ns is not None:
                k = f'{ns.path()}.{name}'
                if k in self.defs:
                    #print('assign', k, vals)
                    self.changed = fupdate(self.defs[k], vals)
                    self.addfeat(f'a{name}')
                    break
                ns = ns.parent
        return k

    def use_var(self, name, tps=None):
        vals = set()
        if tps:
            for tp in tps:
                if isinstance(tp, TypeVal):
                    k = f'+{tp.name}.{name}'
                    if k in self.defs:
                        #print('resolve', k, self.defs[k])
                        vals.update(self.defs[k])
                        self.addfeat(f'r{tp.name}')
                        self.addfeat(f'v{name}')
        else:
            ns = self.ns
            while ns is not None:
                k = f'{ns.path()}.{name}'
                if k in self.defs:
                    #print('resolve', k, self.defs[k])
                    vals.update(self.defs[k])
                    self.addfeat(f'v{name}')
                    break
                ns = ns.parent
        if not vals: return None
        return vals


def main(argv):
    import getopt
    def usage():
        print('usage: %s [-d] [-m maxiters] [file ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dm:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    maxiters = 5
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-m': maxiters = int(v)
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    trees = []
    for path in args:
        logging.info(f'Parsing: {path!r}')
        with open(path, 'rb') as fp:
            text = fp.read()
        try:
            tree = ast.parse(text, path)
        except SyntaxError:
            print('! '+path)
            print('')
            continue
        print('+ '+path)
        (name,_) = os.path.splitext(path)
        name = os.path.normpath(name).replace(os.path.sep, '.')
        root = Namespace(name)
        trees.append((root, tree))

    defs = {}

    for (root, tree) in trees:
        logging.debug(f'Extracting defs: {root}')
        extractor = DefExtractor(root, defs)
        extractor.parse(tree)
        for feat in extractor.feats:
            print(feat)

    for i in range(maxiters):
        logging.info(f'Phase {i}...')
        feats = []
        changed = False
        for (root, tree) in trees:
            logging.debug(f'Extracting uses: {root}')
            extractor = FeatExtractor(root, defs)
            extractor.parse(tree)
            feats.extend(extractor.feats)
            changed = changed or extractor.changed
        if not changed: break
    for feat in feats:
        print(feat)
    print()
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
