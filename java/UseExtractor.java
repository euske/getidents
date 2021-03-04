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
            put(_current+"."+name, "v"+name);
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
                put(_current+"."+name, "v"+name);
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
                put(_current+"."+name, "v"+name);
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
        handleExpr(node.getExpression());
        return true;
    }
    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(ForStatement node) {
        Expression expr1 = node.getExpression();
        if (expr1 != null) {
            handleExpr(expr1);
        }
        for (Expression expr : (List<Expression>)node.initializers()) {
            handleExpr(expr);
        }
        for (Expression expr : (List<Expression>)node.updaters()) {
            handleExpr(expr);
        }
        return true;
    }
    @Override
    public boolean visit(EnhancedForStatement node) {
        handleExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(IfStatement node) {
        handleExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(ExpressionStatement node) {
        handleExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(SwitchStatement node) {
        handleExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(WhileStatement node) {
        handleExpr(node.getExpression());
        return true;
    }
    @Override
    public boolean visit(ReturnStatement node) {
        Expression expr = node.getExpression();
        if (expr != null) {
            handleExpr(expr);
        }
        return true;
    }
    @Override
    public boolean visit(VariableDeclarationFragment node) {
        Expression expr = node.getInitializer();
        if (expr != null) {
            handleExpr(expr);
        }
        return true;
    }

    private void handleExpr(Expression expr) {
        //System.out.println(expr.getClass().getName()+" "+expr);
        parseExpr(expr);
    }

    @SuppressWarnings("unchecked")
    private String parseExpr(Expression expr) {
        if (expr instanceof Annotation) {
            // "@Annotation"
        } else if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                SimpleName sname = (SimpleName)name;
                resolve(sname.getIdentifier());
            } else {
                QualifiedName qname = (QualifiedName)name;
                parseExpr(qname.getQualifier());
            }
        } else if (expr instanceof ThisExpression) {
            // "this"
        } else if (expr instanceof BooleanLiteral) {
            // "true", "false"
        } else if (expr instanceof CharacterLiteral) {
            // "'c'"
        } else if (expr instanceof NullLiteral) {
            // "null"
        } else if (expr instanceof NumberLiteral) {
            // "42"
        } else if (expr instanceof StringLiteral) {
            // ""abc""
        } else if (expr instanceof TypeLiteral) {
            // "A.class"
            Type value = ((TypeLiteral)expr).getType();

        } else if (expr instanceof PrefixExpression) {
            // "++x"
            // "!a", "+a", "-a", "~a"
            PrefixExpression prefix = (PrefixExpression)expr;
            parseExpr(prefix.getOperand());
        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            parseExpr(postfix.getOperand());
        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            parseExpr(infix.getLeftOperand());
            parseExpr(infix.getRightOperand());
        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            parseExpr(paren.getExpression());
        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            parseExpr(assn.getLeftHandSide());
            parseExpr(assn.getRightHandSide());
        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            // XXX
        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                parseExpr(expr1);
            }
            for (Expression arg : (List<Expression>)invoke.arguments()) {
                parseExpr(arg);
            }
        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>)sinvoke.arguments()) {
                parseExpr(arg);
            }
        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>)init.expressions()) {
                parseExpr(expr1);
            }
        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            parseExpr(aa.getArray());
            parseExpr(aa.getIndex());
        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            if (expr1 != null) {
                parseExpr(expr1);
            }
            SimpleName fieldName = fa.getName();
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            Type type = cast.getType();
        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Type type = cstr.getType();
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                parseExpr(expr1);
            }
            for (Expression arg : (List<Expression>)cstr.arguments()) {
                parseExpr(arg);
            }
        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            parseExpr(cond.getExpression());
            parseExpr(cond.getThenExpression());
            parseExpr(cond.getElseExpression());
        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            InstanceofExpression instof = (InstanceofExpression)expr;
            Type type = instof.getRightOperand();
            parseExpr(instof.getLeftOperand());
        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
        } else if (expr instanceof ExpressionMethodReference) {
        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  SuperMethodReference
            //  TypeMethodReference
        }
        return null;
    }

    private void resolve(String name) {
        String k = _current+"."+name;
        String[] a = _featset.get(k);
        if (a != null) {
            for (String v : a) {
                System.out.println(k+": "+v);
            }
        }
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
