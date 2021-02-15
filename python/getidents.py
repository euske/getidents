#!/usr/bin/env python
import sys
import ast
import logging

def walk_expr(r, tree):
    assert isinstance(tree, ast.expr), ast
    if isinstance(tree, ast.Name):
        r.append(('v', tree.id))
    elif isinstance(tree, ast.Tuple):
        for t in tree.elts:
            walk_expr(r, t)
    elif isinstance(tree, ast.List):
        for t in tree.elts:
            walk_expr(r, t)
    elif isinstance(tree, ast.Attribute):
        r.append(('v', tree.attr))
    return

def walk_stmt2(r, tree):
    assert isinstance(tree, ast.stmt), ast
    if isinstance(tree, ast.ClassDef):
        r.append(('t', tree.name))
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.FunctionDef):
        r.append(('f', tree.name))
        for a in tree.args.args:
            walk_expr(r, a)
        if tree.args.vararg is not None:
            r.append(('v', tree.args.vararg))
        if tree.args.kwarg is not None:
            r.append(('v', tree.args.kwarg))
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.ExceptHandler):
        walk_expr(r, tree.name)
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.For):
        walk_expr(r, tree.target)
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.If):
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.While):
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.With):
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.Assign):
        for t in tree.targets:
            walk_expr(r, t)
    elif isinstance(tree, ast.TryExcept):
        for t in tree.body:
            walk_stmt2(r, t)
    elif isinstance(tree, ast.TryFinally):
        for t in tree.body:
            walk_stmt2(r, t)
    return

def walk_stmt3(r, tree):
    assert isinstance(tree, ast.stmt), ast
    if isinstance(tree, ast.ClassDef):
        r.append(('t', tree.name))
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.FunctionDef):
        r.append(('f', tree.name))
        for a in tree.args.posonlyargs:
            r.append(('v', a.arg))
        for a in tree.args.args:
            r.append(('v', a.arg))
        if tree.args.vararg is not None:
            r.append(('v', tree.args.vararg))
        for a in tree.args.kwonlyargs:
            r.append(('v', a.arg))
        if tree.args.kwarg is not None:
            r.append(('v', tree.args.kwarg))
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.ExceptHandler):
        r.append(('v', tree.name))
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.For):
        walk_expr(r, tree.target)
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.If):
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.While):
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.With):
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.Assign):
        for t in tree.targets:
            walk_expr(r, t)
    elif isinstance(tree, ast.Try):
        for t in tree.body:
            walk_stmt3(r, t)
    elif isinstance(tree, ast.AnnAssign):
        walk_expr(r, tree.target)
    return

# check: 2 or 3
if sys.version_info[0] == 2:
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
        r = []
        for t in tree.body:
            walk_stmt(r, t)
        print('+ '+path)
        for (t,name) in r:
            print(t+' '+name)
        print('')
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
