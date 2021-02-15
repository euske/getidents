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

def walk_stmt(r, tree):
    assert isinstance(tree, ast.stmt), ast
    if isinstance(tree, ast.ClassDef):
        r.append(('t', tree.name))
        for t in tree.body:
            walk_stmt(r, t)
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
            walk_stmt(r, t)
    elif isinstance(tree, ast.ExceptHandler):
        r.append(('v', tree.name))
        for t in tree.body:
            walk_stmt(r, t)
    elif isinstance(tree, ast.For):
        walk_expr(r, tree.target)
        for t in tree.body:
            walk_stmt(r, t)
    elif isinstance(tree, ast.If):
        for t in tree.body:
            walk_stmt(r, t)
    elif isinstance(tree, ast.Try):
        for t in tree.body:
            walk_stmt(r, t)
    elif isinstance(tree, ast.While):
        for t in tree.body:
            walk_stmt(r, t)
    elif isinstance(tree, ast.With):
        for t in tree.body:
            walk_stmt(r, t)
    elif isinstance(tree, ast.Assign):
        for t in tree.targets:
            walk_expr(r, t)
    elif isinstance(tree, ast.AnnAssign):
        walk_expr(r, tree.target)
    return

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [file ...]')
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
        print(f'+ {path}')
        with open(path, 'rb') as fp:
            text = fp.read()
        tree = ast.parse(text, path)
        assert isinstance(tree, ast.Module)
        r = []
        for t in tree.body:
            walk_stmt(r, t)
        for (t,name) in r:
            print(f'{t} {name}')
        print()
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
