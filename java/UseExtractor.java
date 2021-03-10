//  UseExtractor.java
//

package getIdents;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  Utils
//
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
            Name name = ((SimpleType)type).getName();
            if (name instanceof SimpleName) {
                return ((SimpleName)name).getIdentifier();
            } else {
                return ((QualifiedName)name).getName().getIdentifier();
            }
        } else if (type instanceof QualifiedType) {
            return ((QualifiedType)type).getName().getIdentifier();
        } else if (type instanceof NameQualifiedType) {
            return ((NameQualifiedType)type).getName().getIdentifier();
        } else if (type instanceof ArrayType) {
            return Utils.typeName(((ArrayType)type).getElementType());
        } else if (type instanceof ParameterizedType) {
            return Utils.typeName(((ParameterizedType)type).getType());
        } else {
            return null;
        }
    }
}


//  Logger
//
class Logger {

    public static PrintStream out = System.err;
    public static int LogLevel = 0;

    public static void info(Object ... a) {
        if (1 <= LogLevel) {
            println(a);
        }
    }

    public static void debug(Object ... a) {
        if (2 <= LogLevel) {
            println(a);
        }
    }

    public static void println(Object[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i != 0) {
                b.append(" ");
            }
            b.append(a[i].toString());
        }
        out.println(b.toString());
    }
}


//  FeatureSet
//
class FeatureSet {

    private Map<String, List<Ident> > _feats =
        new HashMap<String, List<Ident> >();

    public void add(String k, Ident v) {
        List<Ident> a = _feats.get(k);
        if (a == null) {
            a = new ArrayList<Ident>();
            _feats.put(k, a);
        }
        a.add(v);
    }

    public Ident[] get(String k) {
        List<Ident> a = _feats.get(k);
        if (a == null) return null;
        Ident[] r = new Ident[a.size()];
        a.toArray(r);
        return r;
    }
}


//  Context
//
class Context {

    private Context _parent;
    private String _name;

    public Context(Context parent, String name) {
        _parent = parent;
        _name = name;
    }

    public Context(Context parent, Name name) {
        if (name instanceof SimpleName) {
            _parent = parent;
            _name = ((SimpleName)name).getIdentifier();
        } else {
            _parent = new Context(parent, ((QualifiedName)name).getQualifier());
            _name = ((QualifiedName)name).getName().getIdentifier();
        }
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


//  Extractor
//
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
        _current = new Context(null, node.getName());
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
    public boolean visit(AnnotationTypeDeclaration node) {
        push("A"+node.getName().getIdentifier());
        return true;
    }
    @Override
    public void endVisit(AnnotationTypeDeclaration node) {
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
    @SuppressWarnings("unchecked")
    public boolean visit(Initializer node) {
        push("M:static");
        return true;
    }
    @Override
    public void endVisit(Initializer node) {
        pop();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(LambdaExpression node) {
        push("M:lambda"+node.getStartPosition());
        return true;
    }
    @Override
    public void endVisit(LambdaExpression node) {
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
        Logger.debug("current:", _current);
    }
    private void pop() {
        _current = _stack.remove(_stack.size()-1);
    }
}


//  FeatureExtractor
//
class FeatureExtractor extends Extractor {

    private FeatureSet _fset;

    public FeatureExtractor(FeatureSet fset) {
        super();
        _fset = fset;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(EnumDeclaration node) {
        super.visit(node);
        for (EnumConstantDeclaration frag :
                 (List<EnumConstantDeclaration>)node.enumConstants()) {
            String name = frag.getName().getIdentifier();
            String key = "v"+getCurrent()+"."+name;
            put(key, new Ident(IdentType.TYPE, getCurrent().getKey(1)));
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void endVisit(MethodDeclaration node) {
        Context parent = findParent("M");
        if (parent == null) return;
        String key = "m"+parent.getKey(2);
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>)node.parameters()) {
            String name = decl.getName().getIdentifier();
            put(key, new Ident(IdentType.VAR, name));
            String type = Utils.typeName(decl.getType());
            if (type != null) {
                put(key, new Ident(IdentType.TYPE, type));
            }
        }
        {
            String name = node.getName().getIdentifier();
            put(key, new Ident(IdentType.FUNC, name));
            String type = Utils.typeName(node.getReturnType2());
            if (type != null) {
                put(key, new Ident(IdentType.TYPE, type));
            }
        }
        super.endVisit(node);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        String name = node.getName().getIdentifier();
        String key = "v"+getCurrent()+"."+name;
        put(key, new Ident(IdentType.VAR, name));
        String type = Utils.typeName(node.getType());
        if (type != null) {
            put(key, new Ident(IdentType.TYPE, type));
        }
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
            put(key, new Ident(IdentType.VAR, name));
            if (type != null) {
                put(key, new Ident(IdentType.TYPE, type));
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(FieldDeclaration node) {
        Context parent = findParent("T");
        if (parent == null) return false;
        String type = Utils.typeName(node.getType());
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>)node.fragments()) {
            String name = frag.getName().getIdentifier();
            String key = "f"+parent.getKey(1)+"."+name;
            put(key, new Ident(IdentType.VAR, name));
            if (type != null) {
                put(key, new Ident(IdentType.TYPE, type));
            }
        }
        return true;
    }

    private void put(String key, Ident value) {
        Logger.debug("put:", key, value);
        _fset.add(key, value);
    }
}


//  UseExtractor
//
public class UseExtractor extends Extractor {

    private FeatureSet _fset;
    private List<Ident[]> _idents = new ArrayList<Ident[]>();

    public UseExtractor(FeatureSet fset) {
        super();
        _fset = fset;
    }

    public List<Ident[]> getIdents() {
        return _idents;
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
        Logger.debug("handleExpr:", expr, "("+expr.getClass().getName()+")");
        parseExpr(expr);
    }

    @SuppressWarnings("unchecked")
    private String parseExpr(Expression expr) {
        Logger.debug("parseExpr:", expr);
        if (expr instanceof Annotation) {
            // "@Annotation"
            return null;
        } else if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            Ident[] a = null;
            if (name instanceof SimpleName) {
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
                        context = context.getParent();
                        if (context == null) break;
                        context = context.findParent("T");
                    }
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                String type = parseExpr(qname.getQualifier());
                if (type != null) {
                    a = resolve("fT"+type+"."+qname.getName().getIdentifier());
                }
                if (a == null) {
                    add1(new Ident(IdentType.VAR, qname.getName().getIdentifier()));
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
            return "String";
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
            Ident[] a = null;
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
            if (a == null) {
                add1(new Ident(IdentType.FUNC, name.getIdentifier()));
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
            Ident[] a = resolve("fT"+type1+"."+fa.getName().getIdentifier());
            return findType(a);
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            Ident[] a = resolve("f"+findParent("T").getKey(1)+"."+sfa.getName().getIdentifier());
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

    private void add1(Ident ident) {
        _idents.add(new Ident[] { ident });
    }

    private Ident[] resolve(String key) {
        Logger.debug("resolve:", key);
        Ident[] a = _fset.get(key);
        if (a != null) {
            _idents.add(a);
        }
        return a;
    }

    private String findType(Ident[] a) {
        String type = null;
        if (a != null) {
            for (Ident v : a) {
                if (v.type == IdentType.TYPE) {
                    type = v.name;
                    Logger.debug("return:", type);
                    break;
                }
            }
        }
        return type;
    }

    private String returnType(String type) {
        Logger.debug("return:", type);
        return type;
    }

    // main
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
            } else if (arg.equals("-v")) {
                Logger.LogLevel++;
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

        FeatureSet fset = new FeatureSet();

        Logger.info("Pass 1.");
        String[] srcpath = { "." };
        Map<String, CompilationUnit> cunits = new HashMap<String, CompilationUnit>();
        for (String path : files) {
            Logger.info("  parsing:", path);
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

            FeatureExtractor extractor = new FeatureExtractor(fset);
            cunit.accept(extractor);
        }

        Logger.info("Pass 2.");
        for (String path : cunits.keySet()) {
            Logger.info("  parsing:", path);
            CompilationUnit cunit = cunits.get(path);
            UseExtractor extractor = new UseExtractor(fset);
            cunit.accept(extractor);

            out.println("+ "+path);
            for (Ident[] idents : extractor.getIdents()) {
                StringBuffer b = new StringBuffer();
                for (Ident ident : idents) {
                    if (0 < b.length()) {
                        b.append(" ");
                    }
                    b.append(ident.type.code+ident.name);
                }
                out.println(b.toString());
            }
            out.println();
        }

        out.close();
    }
}
