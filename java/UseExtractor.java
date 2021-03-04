//  UseExtractor.java
//

import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

// FeatSet
class FeatSet {

    private Map<String, List<String> > _feats =
        new HashMap<String, List<String> >();

    public void add(String k, String v) {
        List<String> a = _feats.get(k);
        if (a == null) {
            a = new ArrayList<String>();
            _feats.put(k, a);
        }
        a.add(v);
    }

    public String[] get(String k) {
        List<String> a = _feats.get(k);
        if (a == null) return null;
        String[] r = new String[a.size()];
        a.toArray(r);
        return r;
    }
}

// ContextExtractor
class ContextExtractor extends ASTVisitor {

    private FeatSet _featset;
    private Context _current = new Context(null, "");
    private List<Context> _stack = new ArrayList<Context>();

    private class Context {
        private Context _parent;
        private String _name;
        private List<String> _feats;
        public Context(Context parent, String name) {
            _parent = parent;
            _name = name;
            _feats = new ArrayList<String>();
        }
        @Override
        public String toString() {
            if (_parent == null) {
                return _name;
            } else {
                return _parent.toString()+"."+_name;
            }
        }
        public String getName(int i) {
            Context context = this;
            String name = null;
            while (context != null && 0 < i) {
                if (name == null) {
                    name = context._name;
                } else {
                    name = context._name + "." + name;
                }
                i--;
                context = context._parent;
            }
            return name;
        }
        public void addFeat(String feat) {
            _feats.add(feat);
        }
        public List<String> getFeats() {
            return _feats;
        }
    }

    public ContextExtractor(FeatSet featset) {
        super();
        _featset = featset;
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        _current = new Context(null, node.getName().getFullyQualifiedName());
        return true;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        push(node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(TypeDeclaration node) {
        pop();
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        push(node.getDeclaration().getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(TypeDeclarationStatement node) {
        pop();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(EnumDeclaration node) {
        push(node.getName().getIdentifier());
        for (EnumConstantDeclaration frag :
                 (List<EnumConstantDeclaration>)node.enumConstants()) {
            String name = frag.getName().getIdentifier();
            put(_current+"."+name, "t"+_current.getName(1));
        }
        return true;
    }
    @Override
    public void endVisit(EnumDeclaration node) {
        pop();
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        push(node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(MethodDeclaration node) {
        for (String name : _current.getFeats()) {
            put("t"+_current.getName(2), "v"+name);
        }
        pop();
    }

    @Override
    public boolean visit(Block node) {
        push("B"+node.getStartPosition());
        return true;
    }
    @Override
    public void endVisit(Block node) {
        List<String> feats = _current.getFeats();
        pop();
        for (String feat : feats) {
            _current.addFeat(feat);
        }
    }

    private void push(String name) {
        _stack.add(_current);
        _current = new Context(_current, name);
        //System.out.println("current: "+_current);
    }
    private void pop() {
        _current = _stack.remove(_stack.size()-1);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        Type type = node.getType();
        if (type instanceof SimpleType) {
            String name = node.getName().getIdentifier();
            put(_current+"."+name,
                "t"+((SimpleType)type).getName().getFullyQualifiedName());
            _current.addFeat(name);
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(VariableDeclarationStatement node) {
        Type type = node.getType();
        if (type instanceof SimpleType) {
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)node.fragments()) {
                String name = frag.getName().getIdentifier();;
                put(_current+"."+name,
                    "t"+((SimpleType)type).getName().getFullyQualifiedName());
                _current.addFeat(name);
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(FieldDeclaration node) {
        Type type = node.getType();
        if (type instanceof SimpleType) {
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)node.fragments()) {
                String name = frag.getName().getIdentifier();;
                put(_current+"."+name,
                    "t"+((SimpleType)type).getName().getFullyQualifiedName());
            }
        }
        return true;
    }

    private void put(String key, String value) {
        System.out.println("put: "+key+" "+value);
        _featset.add(key, value);
    }
}

// UseExtractor
public class UseExtractor extends ASTVisitor {

    private FeatSet _featset;
    private String _current = "";
    private List<String> _stack = new ArrayList<String>();

    public UseExtractor(FeatSet featset) {
        super();
        _featset = featset;
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        _current = node.getName().getFullyQualifiedName();
        return true;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        push(node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(TypeDeclaration node) {
        pop();
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        push(node.getDeclaration().getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(TypeDeclarationStatement node) {
        pop();
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        push(node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(EnumDeclaration node) {
        pop();
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        push(node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(MethodDeclaration node) {
        pop();
    }

    @Override
    public boolean visit(Block node) {
        push("B"+node.getStartPosition());
        return true;
    }
    @Override
    public void endVisit(Block node) {
        pop();
    }

    private void push(String name) {
        _stack.add(_current);
        _current = _current+"."+name;
        //System.out.println("current: "+_current);
    }
    private void pop() {
        _current = _stack.remove(_stack.size()-1);
    }

    @Override
    public boolean visit(DoStatement node) {
        parseExpr(node.getExpression());
        return true;
    }
    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(ForStatement node) {
        Expression expr1 = node.getExpression();
        if (expr1 != null) {
            parseExpr(expr1);
        }
        for (Expression expr : (List<Expression>)node.initializers()) {
            parseExpr(expr);
        }
        for (Expression expr : (List<Expression>)node.updaters()) {
            parseExpr(expr);
        }
        return true;
    }
    @Override
    public boolean visit(EnhancedForStatement node) {
        parseExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(IfStatement node) {
        parseExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(ExpressionStatement node) {
        parseExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(SwitchStatement node) {
        parseExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(WhileStatement node) {
        parseExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(ReturnStatement node) {
        Expression expr = node.getExpression();
        if (expr != null) {
            parseExpr(expr);
        }
        return true;
    }
    @Override
    public boolean visit(VariableDeclarationFragment node) {
        Expression expr = node.getInitializer();
        if (expr != null) {
            parseExpr(expr);
        }
        return true;
    }

    private void parseExpr(Expression expr) {
        while (expr instanceof Assignment) {
            expr = ((Assignment)expr).getRightHandSide();
        }
        System.out.println(expr.getClass().getName()+" "+expr);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)
        throws IOException {

        List<String> files = new ArrayList<String>();
        PrintStream out = System.out;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                while (i < args.length) {
                    files.add(args[++i]);
                }
            } else if (arg.equals("-i")) {
                String path = args[++i];
                InputStream input = System.in;
                try {
                    if (!path.equals("-")) {
                        input = new FileInputStream(path);
                    }
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input));
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) break;
                        files.add(line);
                    }
                } catch (IOException e) {
                    System.err.println("Cannot open input file: "+path);
                }
            } else if (arg.equals("-o")) {
                String path = args[++i];
                try {
                    out = new PrintStream(new FileOutputStream(path));
                } catch (IOException e) {
                    System.err.println("Cannot open output file: "+path);
                }
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.exit(1);
            } else {
                files.add(arg);
            }
        }

        FeatSet featset = new FeatSet();

        System.err.println("Pass 1.");
        String[] srcpath = { "." };
        List<CompilationUnit> cunits = new ArrayList<CompilationUnit>();
        for (String path : files) {
            String src;
            {
                // Read an entire file as a String.
                StringBuilder b = new StringBuilder();
                try (FileReader fp = new FileReader(path)) {
                    char[] buf = new char[8192];
                    while (true) {
                        int n = fp.read(buf, 0, buf.length);
                        if (n < 0) break;
                        b.append(buf, 0, n);
                    }
                }
                src = b.toString();
            }

            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setUnitName(path);
            parser.setSource(src.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            parser.setEnvironment(null, srcpath, null, true);
            {
                Map<String, String> options = JavaCore.getOptions();
                JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
                parser.setCompilerOptions(options);
            }
            CompilationUnit cunit = (CompilationUnit)parser.createAST(null);
            cunits.add(cunit);

            ContextExtractor extractor = new ContextExtractor(featset);
            cunit.accept(extractor);
        }

        System.err.println("Pass 2.");
        for (CompilationUnit cunit : cunits) {
            UseExtractor extractor = new UseExtractor(featset);
            cunit.accept(extractor);
        }

        out.close();
    }
}
