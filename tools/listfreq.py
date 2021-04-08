#!/usr/bin/env python
##
##  List phrase frequency per project.
##
import sys
from phrases import get_phrases

def main(argv):
    args = argv[1:]

    idmap = {}
    idmap.update({ 'T':'T', 'r':'T', 'u':'T', 'e':'T' })
    idmap.update({ 'F':'F', 'f':'F' })
    idmap.update({ 'V':'V', 'v':'V','a':'V' })

    df = {'*':0}
    for path in args:
        sys.stderr.write(path+'\n'); sys.stderr.flush()
        df['*'] += 1
        (cats, phrases) = get_phrases(path, idmap)
        for p in phrases:
            k = p.word
            if k not in df: df[k] = 0
            df[k] += 1

    print('# phrases list')
    for k in sorted(df.keys(), key=lambda k:df[k], reverse=True):
        if df[k] < 2: continue
        print(df[k], k)
    return

#
if __name__ == '__main__': main(sys.argv)
