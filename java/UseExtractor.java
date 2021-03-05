//  UseExtractor.java
//

import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

class Utils {

    public static <T> String join(T[] a) {
        return join(", ", a);
    }
    public static <T> String join(String delim, T[] a) {
        StringBuilder b = new StringBuilder();
        b.append("[");
        for (T v : a) {
            if (1 < b.length()) {
                b.append(delim);
            }
            b.append((v == null)? "null" : v.toString());
        }
        b.append("]");
        return b.toString();
    }

    public static <T> String join(List<T> a) {
        return join(", ", a);
    }
    public static <T> String join(String delim, List<T> a) {
        StringBuilder b = new StringBuilder();
        b.append("[");
        for (T v : a) {
            if (1 < b.length()) {
                b.append(delim);
            }
            b.append((v == null)? "null" : v.toString());
        }
        b.append("]");
        return b.toString();
    }

    public static String typeName(Type type) {
        if (type instanceof SimpleType) {
            return ((SimpleType)type).getName().getFullyQualifiedName();
        } else if (type instanceof QualifiedType) {
            return ((QualifiedType)type).getName().getFullyQualifiedName();
        } else if (type instanceof ParameterizedType) {
            return Utils.typeName(((ParameterizedType)type).getType());
        } else {
            return null;
        }
    }
}

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

// Context
class Context {

    private Context _parent;
    private String _name;

    public Context(Context parent, String name) {
        _parent = parent;
        _name = name;
    }

    @Override
    public String toString() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.toString()+"."+_name;
        }
    }

    public Context getParent() {
        return _parent;
    }

    public Context findParent(String t) {
        Context context = this;
        while (context != null && !context._name.startsWith(t)) {
            context = context._parent;
        }
        return context;
    }

    public String getKey(int i) {
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
}

// Extractor
class Extractor extends ASTVisitor {

    private Context _current = new Context(null, "");
    private List<Context> _stack = new ArrayList<Context>();

    public Context getCurrent() {
        return _current;
    }

    public Context findParent(String t) {
        return _current.findParent(t);
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        _current = new Context(null, node.getName().getFullyQualifiedName());
        return true;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        push("T"+node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(TypeDeclaration node) {
        pop();
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        push("T"+node.getDeclaration().getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(TypeDeclarationStatement node) {
        pop();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(EnumDeclaration node) {
        push("T"+node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(EnumDeclaration node) {
        pop();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(MethodDeclaration node) {
        push("M"+node.getName().getIdentifier());
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
        _current = new Context(_current, name);
        //System.out.println("current: "+_current);
    }
    private void pop() {
        _current = _stack.remove(_stack.size()-1);
    }
}

// ContextExtractor
class ContextExtractor extends Extractor {

    private FeatSet _featset;

    public ContextExtractor(FeatSet featset) {
        super();
        _featset = featset;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(EnumDeclaration node) {
        super.visit(node);
        for (EnumConstantDeclaration frag :
                 (List<EnumConstantDeclaration>)node.enumConstants()) {
            String name = frag.getName().getIdentifier();
            String key = "v"+getCurrent()+"."+name;
            put(key, "t"+getCurrent().getKey(1));
        }
        return true;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        String key = "m"+findParent("M").getKey(2);
        String type = Utils.typeName(node.getReturnType2());
        if (type != null) {
            put(key, "t"+type);
        }
        super.endVisit(node);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        String name = node.getName().getIdentifier();
        String key = "v"+getCurrent()+"."+name;
        put(key, "v"+name);
        String type = Utils.typeName(node.getType());
        if (type != null) {
            put(key, "t"+type);
        }
        put("m"+findParent("M").getKey(2), "v"+name);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(VariableDeclarationStatement node) {
        String type = Utils.typeName(node.getType());
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>)node.fragments()) {
            String name = frag.getName().getIdentifier();
            String key = "v"+getCurrent()+"."+name;
            put(key, "v"+name);
            if (type != null) {
                put(key, "t"+type);
            }
            put("m"+findParent("M").getKey(2), "v"+name);
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(FieldDeclaration node) {
        String type = Utils.typeName(node.getType());
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>)node.fragments()) {
            String name = frag.getName().getIdentifier();
            String key = "f"+findParent("T").getKey(1)+"."+name;
            put(key, "v"+name);
            if (type != null) {
                put(key, "t"+type);
            }
        }
        return true;
    }

    private void put(String key, String value) {
        //System.out.println("put: "+key+" "+value);
        _featset.add(key, value);
    }
}

// UseExtractor
public class UseExtractor extends Extractor {

    private FeatSet _featset;

    public UseExtractor(FeatSet featset) {
        super();
        _featset = featset;
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
        //System.out.println("+ "+expr.getClass().getName()+" "+expr);
        parseExpr(expr);
    }

    @SuppressWarnings("unchecked")
    private String parseExpr(Expression expr) {
        if (expr instanceof Annotation) {
            // "@Annotation"
            return null;
        } else if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            String[] a = null;
            if (name.isSimpleName()) {
                SimpleName sname = (SimpleName)name;
                Context context = getCurrent();
                while (context != null) {
                    a = resolve("v"+context+"."+sname.getIdentifier());
                    if (a != null) break;
                    context = context.getParent();
                }
                if (a == null) {
                    context = findParent("T");
                    while (context != null) {
                        a = resolve("f"+context.getKey(1)+"."+sname.getIdentifier());
                        if (a != null) break;
                        context = context.getParent().findParent("T");
                    }
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                String type = parseExpr(qname.getQualifier());
                if (type != null) {
                    a = resolve("fT"+type+"."+qname.getName().getIdentifier());
                }
            }
            return findType(a);
        } else if (expr instanceof ThisExpression) {
            // "this"
            return returnType(findParent("T").getKey(1).substring(1));
        } else if (expr instanceof BooleanLiteral) {
            // "true", "false"
            return null;
        } else if (expr instanceof CharacterLiteral) {
            // "'c'"
            return null;
        } else if (expr instanceof NullLiteral) {
            // "null"
            return null;
        } else if (expr instanceof NumberLiteral) {
            // "42"
            return null;
        } else if (expr instanceof StringLiteral) {
            // ""abc""
            return null;
        } else if (expr instanceof TypeLiteral) {
            // "A.class"
            return null;
        } else if (expr instanceof PrefixExpression) {
            // "++x"
            // "!a", "+a", "-a", "~a"
            PrefixExpression prefix = (PrefixExpression)expr;
            return parseExpr(prefix.getOperand());
        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            return parseExpr(postfix.getOperand());
        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            parseExpr(infix.getLeftOperand());
            return parseExpr(infix.getRightOperand());
        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return parseExpr(paren.getExpression());
        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            //parseExpr(assn.getLeftHandSide());
            return parseExpr(assn.getRightHandSide());
        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            String type = null;
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)decl.fragments()) {
                Expression expr1 = frag.getInitializer();
                if (expr1 != null) {
                    type = parseExpr(expr1);
                }
            }
            return returnType(type);
        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            String[] a = null;
            SimpleName name = invoke.getName();
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                String type1 = parseExpr(expr1);
                if (type1 != null) {
                    a = resolve("mT"+type1+".M"+name.getIdentifier());
                }
                if (a == null) {
                    a = resolve("mT"+expr1+".M"+name.getIdentifier());
                }
            } else {
                a = resolve("m"+findParent("T").getKey(1)+".M"+name.getIdentifier());
            }
            for (Expression arg : (List<Expression>)invoke.arguments()) {
                parseExpr(arg);
            }
            return findType(a);
        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>)sinvoke.arguments()) {
                parseExpr(arg);
            }
            return null;
        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression expr1 : (List<Expression>)ac.dimensions()) {
                parseExpr(expr1);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                parseExpr(init);
            }
            return null;
        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>)init.expressions()) {
                parseExpr(expr1);
            }
            return null;
        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            parseExpr(aa.getIndex());
            return parseExpr(aa.getArray());
        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            String type1 = parseExpr(expr1);
            String[] a = resolve("fT"+type1+"."+fa.getName().getIdentifier());
            return findType(a);
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            String[] a = resolve("f"+findParent("T").getKey(1)+"."+sfa.getName().getIdentifier());
            return findType(a);
        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            parseExpr(cast.getExpression());
            String type = Utils.typeName(cast.getType());
            return returnType(type);
        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            String type = Utils.typeName(cstr.getType());
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                parseExpr(expr1);
            }
            resolve("mT"+type+".M"+type);
            for (Expression arg : (List<Expression>)cstr.arguments()) {
                parseExpr(arg);
            }
            return returnType(type);
        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            parseExpr(cond.getExpression());
            parseExpr(cond.getThenExpression());
            return parseExpr(cond.getElseExpression());
        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            InstanceofExpression instof = (InstanceofExpression)expr;
            Type type = instof.getRightOperand();
            parseExpr(instof.getLeftOperand());
            return null;
        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
            return null;
        } else if (expr instanceof ExpressionMethodReference) {
            return null;
        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  SuperMethodReference
            //  TypeMethodReference
            return null;
        } else {
            return null;
        }
    }

    private String[] resolve(String key) {
        //System.out.println("resolve: "+key);
        String[] a = _featset.get(key);
        if (a != null) {
            StringBuilder b = new StringBuilder();
            for (String v : a) {
                if (1 < b.length()) {
                    b.append(" ");
                }
                b.append(v);
            }
            System.out.println(b.toString());
        }
        return a;
    }

    private String findType(String[] a) {
        String type = null;
        if (a != null) {
            for (String v : a) {
                if (v.startsWith("t")) {
                    type = v.substring(1);
                    //System.out.println("= "+type);
                    break;
                }
            }
        }
        return type;
    }

    private String returnType(String type) {
        //System.out.println("= "+type);
        return type;
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
        Map<String, CompilationUnit> cunits = new HashMap<String, CompilationUnit>();
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
            cunits.put(path, cunit);

            ContextExtractor extractor = new ContextExtractor(featset);
            cunit.accept(extractor);
        }

        System.err.println("Pass 2.");
        for (String path : cunits.keySet()) {
            CompilationUnit cunit = cunits.get(path);
            UseExtractor extractor = new UseExtractor(featset);
            System.out.println("+ "+path);
            cunit.accept(extractor);
        }

        out.close();
    }
}
