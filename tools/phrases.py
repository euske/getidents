#!/usr/bin/env python
import re


##  splitwords
##
WORD = re.compile(r'[0-9]*[a-z]+[A-Z]?|[0-9]*[A-Z]+')
def splitwords(s):
    n = len(s)
    r = ''.join(reversed(s))
    w = [ s[n-m.end(0):n-m.start(0)].lower() for m in WORD.finditer(r) ]
    w.reverse()
    return w


##  Identifier
##
class Identifier:

    def __init__(self, name):
        self.name = name
        self.T = 0
        self.F = 0
        self.V = 0
        return

    def __repr__(self):
        return f'<{self.name} ({self.T},{self.F},{self.V})>'

    def add(self, c):
        if c == 'T':
            self.T += 1
        elif c == 'F':
            self.F += 1
        elif c == 'V':
            self.V += 1
        else:
            raise ValueError(c)
        return

    def score(self):
        return pow(self.T*self.F*self.V, 1/3)


##  Phrase
##
class Phrase:

    def __init__(self, seq):
        self.seq = seq
        self.word = '/'.join(seq)
        self.idents = []
        self.T = 0
        self.V = 0
        self.F = 0
        return

    def __repr__(self):
        return f'<{self.word} ({self.T:.03f},{self.V:.03f},{self.F:.03f}>'

    def add(self, e):
        self.idents.append(e)
        self.T += e.T
        self.V += e.V
        self.F += e.F
        return

    def fixate(self, cats):
        n = 0
        for v in (self.T, self.V, self.F):
            if 0 < v:
                n += 1
        if n != len(cats): return False
        self.T /= cats.get('T',1)
        self.V /= cats.get('V',1)
        self.F /= cats.get('F',1)
        self.idents.sort(key=lambda e:e.score(), reverse=True)
        return True

    def score(self):
        return pow(self.T*self.V*self.F, 1/3)


##  get_phrases(fp, idmap)
##  Reads a list of phrases.
##
def get_phrases(path, idmap):
    # Count Identifiers.
    idents = {}
    cats = {}
    with open(path) as fp:
        for line in fp:
            line = line.strip()
            if not line or line.startswith('+'): continue
            for w in line.split():
                (c,name) = (w[0],w[1:])
                if c not in idmap: continue
                c = idmap[c]
                if name in idents:
                    e = idents[name]
                else:
                    e = idents[name] = Identifier(name)
                e.add(c)
                if c not in cats:
                    cats[c] = 0
                cats[c] += 1
    # Count phrases.
    pcands = {}
    for (name,e) in idents.items():
        words = tuple(splitwords(name))
        for i in range(len(words)):
            for j in range(i, len(words)):
                seq = words[i:j+1]
                if seq in pcands:
                    p = pcands[seq]
                else:
                    p = pcands[seq] = Phrase(seq)
                p.add(e)
    # Filter phrases.
    phrases = [ p for p in pcands.values() if p.fixate(cats) ]
    return (cats, phrases)
