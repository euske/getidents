#!/usr/bin/env python
##
##  Generate Glossary.
##
import sys
from math import log
from phrases import get_phrases

# loadidf(path): obtains IDF from a frequency list.
def loadidf(path):
    df = {}
    with open(path) as fp:
        for line in fp:
            (line,_,_) = line.strip().partition('#')
            if not line: continue
            (n,_,k) = line.strip().partition(' ')
            df[k] = int(n)
    idf0 = log(df['*'])
    idf = { k:idf0-log(n) for (k,n) in df.items() }
    return (idf0, idf)

# loadkeys(path): obtains a reference list.
def loadkeys(path):
    keys = {}
    name = syns = None
    with open(path) as fp:
        for line in fp:
            (line,_,_) = line.strip().partition('#')
            if line.startswith('+'):
                (_,_,name) = line.partition(' ')
                syns = []
                keys[name] = syns
            elif line:
                assert syns is not None
                syns.append(line.split())
            else:
                name = syns = None
    return keys

# perf_eval(): performs evaluation.
def perf_eval(idmap, idf0, idf, keyfile, threshold, verbose=0):
    keys = loadkeys(keyfile)
    n_total = 0
    s_total = 0
    for (name,syns) in keys.items():
        path = f'uses/{name}.lst'
        (cats, phrases) = get_phrases(path, idmap)
        phrases.sort(key=lambda p:p.score()*idf.get(p.word, idf0), reverse=True)
        ranked = { ''.join(p.seq): i for (i,p) in enumerate(phrases) }
        found = []
        for syn in syns:
            i = None
            for w in syn:
                if w in ranked:
                    if i is None or ranked[w] < i:
                        i = ranked[w]
            if i is not None:
                found.append((i, syn[0]))
        n = len([ i for (i,w) in found if i < threshold ])
        print(name, f'{n}/{len(syns)}')
        if verbose:
            print('#', sorted(found))
        n_total += n
        s_total += len(syns)
    print(f'Total: {n_total}/{s_total} ({n_total*100/s_total:.01f}%)')
    return

#
def main(argv):
    import getopt
    def usage():
        print('usage: %s [-a] [-p phrases.lst] [-k keyfile] [-n threshold] [-B] [-t|-T] [-f|-F] [-v|-V] [file ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'ap:k:n:BAtTfFvV')
    except getopt.GetoptError:
        return usage()
    verbose = 0
    phrasefile = 'data/phrases-python.lst'
    keyfile = None
    idmap = {}
    threshold = 50
    for (k, v) in opts:
        if k == '-a': verbose += 1
        elif k == '-p': phrasefile = v
        elif k == '-k': keyfile = v
        elif k == '-n': threshold = int(v)
        elif k == '-B': idmap.update({ 'T':'T', 'F':'T', 'V':'T' })
        elif k == '-T': idmap.update({ 'T':'T', 'r':'T', 'u':'T', 'e':'T' })
        elif k == '-t': idmap.update({ 'T':'T' })
        elif k == '-F': idmap.update({ 'F':'F', 'f':'F' })
        elif k == '-f': idmap.update({ 'F':'F' })
        elif k == '-V': idmap.update({ 'V':'V', 'a':'V', 'v':'V' })
        elif k == '-v': idmap.update({ 'V':'V' })
    if not idmap:
        idmap.update({ 'T':'T', 'r':'T', 'u':'T', 'e':'T', 'F':'F', 'f':'F', 'V':'V', 'a':'V', 'v':'V' })

    (idf0, idf) = loadidf(phrasefile)

    if keyfile is not None:
        # Evaluation mode.
        perf_eval(idmap, idf0, idf, keyfile, threshold, verbose=verbose)
    else:
        for path in args:
            (cats, phrases) = get_phrases(path, idmap)
            phrases.sort(key=lambda p:p.score()*idf.get(p.word, idf0), reverse=True)
            phrases = phrases[:threshold]
            print(f'+ {path}')
            if verbose:
                print(f'# {cats}')
                for p in phrases:
                    print(p, p.idents[:3])
            else:
                for p in phrases:
                    print(p.word)

    return

if __name__ == '__main__': main(sys.argv)
