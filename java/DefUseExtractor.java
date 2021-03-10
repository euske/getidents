//  DefUseExtractor.java
//
package getIdents;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


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


//  NamespaceWalker
//
class NamespaceWalker extends ASTVisitor {

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


//  TypeExtractor
//
class TypeExtractor extends NamespaceWalker {

    private FeatureSet _fset;

    public TypeExtractor(FeatureSet fset) {
        super();
        _fset = fset;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(EnumDeclaration node) {
        super.visit(node);
        String type = getCurrent().getKey(1);
        for (EnumConstantDeclaration frag :
                 (List<EnumConstantDeclaration>)node.enumConstants()) {
            String name = frag.getName().getIdentifier();
            String key = "v"+getCurrent()+"."+name;
            fadd(key, new Ident(IdentType.TYPE, type));
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
            fadd(key, new Ident(IdentType.VAR, name));
        }
        String type = Utils.typeName(node.getReturnType2());
        if (type != null) {
            fadd(key, new Ident(IdentType.TYPE, type));
        }
        super.endVisit(node);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        String name = node.getName().getIdentifier();
        String key = "v"+getCurrent()+"."+name;
        String type = Utils.typeName(node.getType());
        if (type != null) {
            fadd(key, new Ident(IdentType.TYPE, type));
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(VariableDeclarationStatement node) {
        String type = Utils.typeName(node.getType());
        if (type != null) {
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)node.fragments()) {
                String name = frag.getName().getIdentifier();
                String key = "v"+getCurrent()+"."+name;
                fadd(key, new Ident(IdentType.TYPE, type));
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
        if (type != null) {
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)node.fragments()) {
                String name = frag.getName().getIdentifier();
                String key = "f"+parent.getKey(1)+"."+name;
                fadd(key, new Ident(IdentType.TYPE, type));
            }
        }
        return true;
    }

    private void fadd(String key, Ident value) {
        Logger.debug("fadd:", key, value);
        _fset.add(key, value);
    }
}


//  DefUse
//
abstract class DefUse {
    public Ident ident;
}
class Def extends DefUse {
    public Def(Ident ident) { this.ident = ident; }
    public String toString() { return "<Def "+this.ident+">"; }
}
class Use extends DefUse {
    public Use(Ident ident) { this.ident = ident; }
    public String toString() { return "<Use "+this.ident+">"; }
}



//  DefUseExtractor
//
public class DefUseExtractor extends NamespaceWalker {

    private FeatureSet _fset;
    private List<DefUse[]> _defuses = new ArrayList<DefUse[]>();

    public DefUseExtractor(FeatureSet fset) {
        super();
        _fset = fset;
    }

    public List<DefUse[]> getResults() {
        return _defuses;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        super.visit(node);
        addDef(new Ident(IdentType.TYPE, node.getName().getIdentifier()));
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        super.visit(node);
        addDef(new Ident(IdentType.TYPE, node.getName().getIdentifier()));
        return true;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        super.visit(node);
        addDef(new Ident(IdentType.FUNC, node.getName().getIdentifier()));
        return true;
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
    @SuppressWarnings("unchecked")
    public boolean visit(VariableDeclarationStatement node) {
        List<DefUse> a = new ArrayList<DefUse>();
        String type = Utils.typeName(node.getType());
        if (type != null) {
            a.add(new Use(new Ident(IdentType.TYPE, type)));
        }
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>)node.fragments()) {
            SimpleName name = frag.getName();
            a.add(new Def(new Ident(IdentType.VAR, name.getIdentifier())));
            Expression expr1 = frag.getInitializer();
            if (expr1 != null) {
                parseExpr(expr1);
            }
        }
        addDefUse(a);
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
    public boolean visit(SingleVariableDeclaration node) {
        String name = node.getName().getIdentifier();
        List<DefUse> a = new ArrayList<DefUse>();
        String type = Utils.typeName(node.getType());
        if (type != null) {
            a.add(new Use(new Ident(IdentType.TYPE, type)));
        }
        a.add(new Def(new Ident(IdentType.VAR, name)));
        addDefUse(a);
        return true;
    }

    private void handleExpr(Expression expr) {
        Logger.debug("handleExpr:", expr.getClass().getName());
        parseExpr(expr);
    }

    @SuppressWarnings("unchecked")
    private String parseExpr(Expression expr) {
        Logger.debug("parseExpr:", expr);
        String type = null;
        if (expr instanceof Annotation) {
            // "@Annotation"
        } else if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            if (name instanceof SimpleName) {
                SimpleName sname = (SimpleName)name;
                String id = sname.getIdentifier();
                Context context = getCurrent();
                while (context != null) {
                    type = resolveType("v"+context+"."+id);
                    if (type != null) break;
                    context = context.getParent();
                }
                if (type == null) {
                    context = findParent("T");
                    while (context != null) {
                        type = resolveType("f"+context.getKey(1)+"."+id);
                        if (type != null) break;
                        context = context.getParent();
                        if (context == null) break;
                        context = context.findParent("T");
                    }
                }
                if (type != null) {
                    addUse(new Ident(IdentType.VAR, id));
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                String id = qname.getName().getIdentifier();
                String klass = parseExpr(qname.getQualifier());
                List<DefUse> a = new ArrayList<DefUse>();
                if (klass != null) {
                    a.add(new Use(new Ident(IdentType.TYPE, klass)));
                    type = resolveType("fT"+klass+"."+id);
                }
                a.add(new Use(new Ident(IdentType.VAR, id)));
                addDefUse(a);
            }
        } else if (expr instanceof ThisExpression) {
            // "this"
            type = findParent("T").getKey(1).substring(1);
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
            type = "String";
        } else if (expr instanceof TypeLiteral) {
            // "A.class"
        } else if (expr instanceof PrefixExpression) {
            // "++x"
            // "!a", "+a", "-a", "~a"
            PrefixExpression prefix = (PrefixExpression)expr;
            type = parseExpr(prefix.getOperand());
        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            type = parseExpr(postfix.getOperand());
        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            type = parseExpr(infix.getLeftOperand());
            parseExpr(infix.getRightOperand());
            for (Expression expr1 :
                     (List<Expression>)infix.extendedOperands()) {
                parseExpr(expr1);
            }
        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            type = parseExpr(paren.getExpression());
        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            parseAssign(assn.getLeftHandSide());
            type = parseExpr(assn.getRightHandSide());
        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
        } else if (expr instanceof MethodInvocation) {
            // "f(x)"
            MethodInvocation invoke = (MethodInvocation)expr;
            SimpleName name = invoke.getName();
            String id = name.getIdentifier();
            List<DefUse> a = new ArrayList<DefUse>();
            a.add(new Use(new Ident(IdentType.FUNC, id)));
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                String klass = parseExpr(expr1);
                if (klass != null) {
                    a.add(new Use(new Ident(IdentType.TYPE, klass)));
                    type = resolveFunc("mT"+klass+".M"+id, a);
                }
                if (type == null) {
                    type = resolveFunc("mT"+expr1+".M"+id, a);
                }
            } else {
                String klass = findParent("T").getKey(1).substring(1);
                a.add(new Use(new Ident(IdentType.TYPE, klass)));
                type = resolveFunc("mT"+klass+".M"+id, a);
            }
            addDefUse(a);
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
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression expr1 : (List<Expression>)ac.dimensions()) {
                parseExpr(expr1);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                parseExpr(init);
            }
        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>)init.expressions()) {
                parseExpr(expr1);
            }
        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            parseExpr(aa.getIndex());
            type = parseExpr(aa.getArray());
        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            List<DefUse> a = new ArrayList<DefUse>();
            String id = fa.getName().getIdentifier();
            String klass = parseExpr(expr1);
            if (klass != null) {
                a.add(new Use(new Ident(IdentType.TYPE, klass)));
            }
            type = resolveType("fT"+klass+"."+id);
            a.add(new Use(new Ident(IdentType.VAR, id)));
            addDefUse(a);
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            String id = sfa.getName().getIdentifier();
            String klass = findParent("T").getKey(1).substring(1);
            if (klass != null) {
                a.add(new Use(new Ident(IdentType.TYPE, klass)));
            }
            type = resolveType("fT"+klass+"."+id);
            a.add(new Use(new Ident(IdentType.VAR, id)));
        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            parseExpr(cast.getExpression());
            type = Utils.typeName(cast.getType());
        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            type = Utils.typeName(cstr.getType());
            a.add(new Use(new Ident(IdentType.TYPE, type)));
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                parseExpr(expr1);
            }
            resolveFunc("mT"+type+".M"+type, a);
            for (Expression arg : (List<Expression>)cstr.arguments()) {
                parseExpr(arg);
            }
        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            parseExpr(cond.getExpression());
            parseExpr(cond.getThenExpression());
            type = parseExpr(cond.getElseExpression());
        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            InstanceofExpression instof = (InstanceofExpression)expr;
            //Type type = instof.getRightOperand();
            parseExpr(instof.getLeftOperand());
        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
        } else if (expr instanceof ExpressionMethodReference) {

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  SuperMethodReference
            //  TypeMethodReference
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    private void parseAssign(Expression expr) {
        Logger.debug("parseAssign:", expr);
        if (expr instanceof Annotation) {
            // "@Annotation"
        } else if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            if (name instanceof SimpleName) {
                SimpleName sname = (SimpleName)name;
                String id = sname.getIdentifier();
                addDef(new Ident(IdentType.VAR, id));
            } else {
                QualifiedName qname = (QualifiedName)name;
                String id = qname.getName().getIdentifier();
                String klass = parseExpr(qname.getQualifier());
                List<DefUse> a = new ArrayList<DefUse>();
                if (klass != null) {
                    a.add(new Use(new Ident(IdentType.TYPE, klass)));
                }
                a.add(new Def(new Ident(IdentType.VAR, id)));
                addDefUse(a);
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
        } else if (expr instanceof PrefixExpression) {
            // "++x"
            // "!a", "+a", "-a", "~a"
        } else if (expr instanceof PostfixExpression) {
            // "y--"
        } else if (expr instanceof InfixExpression) {
            // "a+b"
        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            parseAssign(paren.getExpression());
        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            parseAssign(assn.getLeftHandSide());
        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            String type = Utils.typeName(decl.getType());
            if (type != null) {
                a.add(new Use(new Ident(IdentType.TYPE, type)));
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)decl.fragments()) {
                SimpleName name = frag.getName();
                a.add(new Use(new Ident(IdentType.VAR, name.getIdentifier())));
                Expression expr1 = frag.getInitializer();
                if (expr1 != null) {
                    parseExpr(expr1);
                }
            }
            addDefUse(a);
        } else if (expr instanceof MethodInvocation) {
            // "f(x)"
        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
        } else if (expr instanceof ArrayInitializer) {
        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            parseAssign(aa.getArray());
        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            List<DefUse> a = new ArrayList<DefUse>();
            String id = fa.getName().getIdentifier();
            String klass = parseExpr(expr1);
            if (klass != null) {
                a.add(new Use(new Ident(IdentType.TYPE, klass)));
            }
            a.add(new Def(new Ident(IdentType.VAR, id)));
            addDefUse(a);
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            String id = sfa.getName().getIdentifier();
            String klass = findParent("T").getKey(1).substring(1);
            if (klass != null) {
                a.add(new Use(new Ident(IdentType.TYPE, klass)));
            }
            a.add(new Def(new Ident(IdentType.VAR, id)));
        } else if (expr instanceof CastExpression) {
            // "(String)"
        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
        } else if (expr instanceof ExpressionMethodReference) {

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  SuperMethodReference
            //  TypeMethodReference
        }
    }

    private void addDef(Ident ident) {
        Logger.debug("addDef:", ident);
        _defuses.add(new DefUse[] { new Def(ident) });
    }

    private void addUse(Ident ident) {
        Logger.debug("addUse:", ident);
        _defuses.add(new DefUse[] { new Use(ident) });
    }

    private void addDefUse(List<DefUse> uses) {
        DefUse[] a = new DefUse[uses.size()];
        uses.toArray(a);
        Logger.debug("addDefUse:", Utils.join(a));
        _defuses.add(a);
    }

    private String resolveType(String key) {
        String type = null;
        Ident[] idents = _fset.get(key);
        if (idents != null) {
            for (Ident ident : idents) {
                if (ident.type == IdentType.TYPE) {
                    type = ident.name;
                    break;
                }
            }
        }
        Logger.debug("resolveType:", key, "->", type);
        return type;
    }

    private String resolveFunc(String key, List<DefUse> a) {
        String type = null;
        Ident[] idents = _fset.get(key);
        if (idents != null) {
            for (Ident ident : idents) {
                if (ident.type == IdentType.TYPE) {
                    type = ident.name;
                } else if (ident.type == IdentType.VAR) {
                    a.add(new Def(ident));
                }
            }
        }
        Logger.debug("resolveFunc:", key, "->", type);
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

            TypeExtractor extractor = new TypeExtractor(fset);
            cunit.accept(extractor);
        }

        Logger.info("Pass 2.");
        for (String path : cunits.keySet()) {
            Logger.info("  parsing:", path);
            CompilationUnit cunit = cunits.get(path);
            DefUseExtractor extractor = new DefUseExtractor(fset);
            cunit.accept(extractor);

            out.println("+ "+path);
            for (DefUse[] defuses : extractor.getResults()) {
                StringBuffer b = new StringBuffer();
                for (DefUse du : defuses) {
                    if (0 < b.length()) {
                        b.append(" ");
                    }
                    Ident ident = du.ident;
                    String code = ident.type.code;
                    b.append(((du instanceof Def)? code.toUpperCase() : code) + ident.name);
                }
                out.println(b.toString());
            }
            out.println();
        }

        out.close();
    }
}