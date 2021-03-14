//  DefUseExtractor.java
//
//  T: define type...
//     class Foo { }
//  r: refer type...
//     x = foo.a; foo.baa();
//  e: extend type...
//     class Bar extends Foo { }
//  u: use type...
//     Foo foo = new Foo();
//  F: define function...
//     void baa() { }
//  f: use function...
//     baa();
//  V: define variable...
//     int x = 1;
//  v: use variable;
//     print(x);
//  a: reassign variable;
//     x = 1;
//
package getIdents;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  Feat
//
abstract class Feat {
    public String name;
    public Feat(String name) { this.name = name; }
}
class TypeFeat extends Feat {
    TypeFeat(String name) { super(name); }
    @Override public String toString() { return "T"+this.name; }
}
class VarFeat extends Feat {
    VarFeat(String name) { super(name); }
    @Override public String toString() { return "V"+this.name; }
}


//  FeatureSet
//
class FeatureSet {

    private Map<String, List<Feat> > _feats =
        new HashMap<String, List<Feat> >();

    public void add(String k, Feat v) {
        List<Feat> a = _feats.get(k);
        if (a == null) {
            a = new ArrayList<Feat>();
            _feats.put(k, a);
        }
        a.add(v);
    }

    public Feat[] get(String k) {
        List<Feat> a = _feats.get(k);
        if (a == null) return null;
        Feat[] r = new Feat[a.size()];
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
        String typename = getCurrent().getKey(1);
        for (EnumConstantDeclaration frag :
                 (List<EnumConstantDeclaration>)node.enumConstants()) {
            String name = frag.getName().getIdentifier();
            String key = "v"+getCurrent()+"."+name;
            fadd(key, new TypeFeat(typename));
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
            fadd(key, new VarFeat(name));
        }
        String typename = Utils.typeName(node.getReturnType2());
        if (typename != null) {
            fadd(key, new TypeFeat(typename));
        }
        super.endVisit(node);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        String name = node.getName().getIdentifier();
        String key = "v"+getCurrent()+"."+name;
        String typename = Utils.typeName(node.getType());
        if (typename != null) {
            fadd(key, new TypeFeat(typename));
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(VariableDeclarationStatement node) {
        String typename = Utils.typeName(node.getType());
        if (typename != null) {
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)node.fragments()) {
                String name = frag.getName().getIdentifier();
                String key = "v"+getCurrent()+"."+name;
                fadd(key, new TypeFeat(typename));
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean visit(FieldDeclaration node) {
        Context parent = findParent("T");
        if (parent == null) return false;
        String typename = Utils.typeName(node.getType());
        if (typename != null) {
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)node.fragments()) {
                String name = frag.getName().getIdentifier();
                String key = "f"+parent.getKey(1)+"."+name;
                fadd(key, new TypeFeat(typename));
            }
        }
        return true;
    }

    private void fadd(String key, Feat value) {
        Logger.debug("fadd:", key, value);
        _fset.add(key, value);
    }
}


//  DefUse
//
abstract class DefUse {
    public String name;
    public DefUse(String name) { this.name = name; }
}
class DefType extends DefUse {
    DefType(String name) { super(name); }
    @Override public String toString() { return "T"+this.name; }
}
class RefType extends DefUse {
    RefType(String name) { super(name); }
    @Override public String toString() { return "r"+this.name; }
}
class ExtType extends DefUse {
    ExtType(String name) { super(name); }
    @Override public String toString() { return "e"+this.name; }
}
class UseType extends DefUse {
    UseType(String name) { super(name); }
    @Override public String toString() { return "u"+this.name; }
}
class DefFunc extends DefUse {
    DefFunc(String name) { super(name); }
    @Override public String toString() { return "F"+this.name; }
}
class UseFunc extends DefUse {
    UseFunc(String name) { super(name); }
    @Override public String toString() { return "f"+this.name; }
}
class DefVar extends DefUse {
    DefVar(String name) { super(name); }
    @Override public String toString() { return "V"+this.name; }
}
class UseVar extends DefUse {
    UseVar(String name) { super(name); }
    @Override public String toString() { return "v"+this.name; }
}
class AssVar extends DefUse {
    AssVar(String name) { super(name); }
    @Override public String toString() { return "a"+this.name; }
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
    @SuppressWarnings("unchecked")
    public boolean visit(TypeDeclaration node) {
        super.visit(node);
        addu(new DefType(node.getName().getIdentifier()));
        String stypename = Utils.typeName(node.getSuperclassType());
        if (stypename != null) {
            addu(new ExtType(stypename));
        }
        for (Type type1 : (List<Type>)node.superInterfaceTypes()) {
            String typename = Utils.typeName(type1);
            if (typename != null) {
                addu(new ExtType(typename));
            }
        }
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        super.visit(node);
        addu(new DefType(node.getName().getIdentifier()));
        return true;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        super.visit(node);
        addu(new DefFunc(node.getName().getIdentifier()));
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
        for (String typename : Utils.getTypeNames(node.getType())) {
            a.add(new UseType(typename));
        }
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>)node.fragments()) {
            SimpleName name = frag.getName();
            a.add(new DefVar(name.getIdentifier()));
            Expression expr1 = frag.getInitializer();
            if (expr1 != null) {
                parseExpr(expr1);
            }
        }
        addu(a);
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
        for (String typename : Utils.getTypeNames(node.getType())) {
            a.add(new UseType(typename));
        }
        a.add(new DefVar(name));
        addu(a);
        return true;
    }

    private void handleExpr(Expression expr) {
        Logger.debug("handleExpr:", expr.getClass().getName());
        parseExpr(expr);
    }

    @SuppressWarnings("unchecked")
    private String parseExpr(Expression expr) {
        Logger.debug("parseExpr:", expr);
        String typename = null;
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
                    typename = resolveType("v"+context+"."+id);
                    if (typename != null) break;
                    context = context.getParent();
                }
                if (typename == null) {
                    context = findParent("T");
                    while (context != null) {
                        typename = resolveType("f"+context.getKey(1)+"."+id);
                        if (typename != null) break;
                        context = context.getParent();
                        if (context == null) break;
                        context = context.findParent("T");
                    }
                }
                if (typename != null) {
                    addu(new UseVar(id));
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                String id = qname.getName().getIdentifier();
                String klass = parseExpr(qname.getQualifier());
                List<DefUse> a = new ArrayList<DefUse>();
                if (klass != null) {
                    a.add(new RefType(klass));
                    typename = resolveType("fT"+klass+"."+id);
                }
                a.add(new UseVar(id));
                addu(a);
            }
        } else if (expr instanceof ThisExpression) {
            // "this"
            typename = findParent("T").getKey(1).substring(1);
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
            typename = "String";
        } else if (expr instanceof TypeLiteral) {
            // "A.class"
        } else if (expr instanceof PrefixExpression) {
            // "++x"
            // "!a", "+a", "-a", "~a"
            PrefixExpression prefix = (PrefixExpression)expr;
            typename = parseExpr(prefix.getOperand());
        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            typename = parseExpr(postfix.getOperand());
        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            typename = parseExpr(infix.getLeftOperand());
            parseExpr(infix.getRightOperand());
            for (Expression expr1 :
                     (List<Expression>)infix.extendedOperands()) {
                parseExpr(expr1);
            }
        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            typename = parseExpr(paren.getExpression());
        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            parseAssign(assn.getLeftHandSide());
            typename = parseExpr(assn.getRightHandSide());
        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
        } else if (expr instanceof MethodInvocation) {
            // "f(x)"
            MethodInvocation invoke = (MethodInvocation)expr;
            SimpleName name = invoke.getName();
            String id = name.getIdentifier();
            List<DefUse> a = new ArrayList<DefUse>();
            a.add(new UseFunc(id));
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                String klass = parseExpr(expr1);
                if (klass != null) {
                    a.add(new RefType(klass));
                    typename = resolveFunc("mT"+klass+".M"+id, a);
                }
                if (typename == null) {
                    typename = resolveFunc("mT"+expr1+".M"+id, a);
                }
            } else {
                String klass = findParent("T").getKey(1).substring(1);
                a.add(new RefType(klass));
                typename = resolveFunc("mT"+klass+".M"+id, a);
            }
            addu(a);
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
            typename = parseExpr(aa.getArray());
        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            List<DefUse> a = new ArrayList<DefUse>();
            String id = fa.getName().getIdentifier();
            String klass = parseExpr(expr1);
            if (klass != null) {
                a.add(new RefType(klass));
            }
            typename = resolveType("fT"+klass+"."+id);
            a.add(new UseVar(id));
            addu(a);
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            String id = sfa.getName().getIdentifier();
            String klass = findParent("T").getKey(1).substring(1);
            if (klass != null) {
                a.add(new RefType(klass));
            }
            typename = resolveType("fT"+klass+"."+id);
            a.add(new UseVar(id));
        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            parseExpr(cast.getExpression());
            typename = Utils.typeName(cast.getType());
        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            for (String typename1 : Utils.getTypeNames(cstr.getType())) {
                a.add(new UseType(typename1));
                if (typename != null) {
                    typename = typename1;
                }
            }
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                parseExpr(expr1);
            }
            resolveFunc("mT"+typename+".M"+typename, a);
            for (Expression arg : (List<Expression>)cstr.arguments()) {
                parseExpr(arg);
            }
        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            parseExpr(cond.getExpression());
            parseExpr(cond.getThenExpression());
            typename = parseExpr(cond.getElseExpression());
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
        return typename;
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
                addu(new AssVar(id));
            } else {
                QualifiedName qname = (QualifiedName)name;
                String id = qname.getName().getIdentifier();
                String klass = parseExpr(qname.getQualifier());
                List<DefUse> a = new ArrayList<DefUse>();
                if (klass != null) {
                    a.add(new RefType(klass));
                }
                a.add(new AssVar(id));
                addu(a);
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
            String typename = Utils.typeName(decl.getType());
            if (typename != null) {
                a.add(new RefType(typename));
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>)decl.fragments()) {
                SimpleName name = frag.getName();
                a.add(new UseVar(name.getIdentifier()));
                Expression expr1 = frag.getInitializer();
                if (expr1 != null) {
                    parseExpr(expr1);
                }
            }
            addu(a);
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
                a.add(new RefType(klass));
            }
            a.add(new DefVar(id));
            addu(a);
        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            List<DefUse> a = new ArrayList<DefUse>();
            String id = sfa.getName().getIdentifier();
            String klass = findParent("T").getKey(1).substring(1);
            if (klass != null) {
                a.add(new RefType(klass));
            }
            a.add(new DefVar(id));
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

    private void addu(DefUse du) {
        Logger.debug("addu:", du);
        _defuses.add(new DefUse[] { du });
    }

    private void addu(List<DefUse> dus) {
        Logger.debug("addu:", Utils.join(dus));
        DefUse[] a = new DefUse[dus.size()];
        dus.toArray(a);
        _defuses.add(a);
    }

    private String resolveType(String key) {
        String typename = null;
        Feat[] feats = _fset.get(key);
        if (feats != null) {
            for (Feat feat : feats) {
                if (feat instanceof TypeFeat) {
                    typename = feat.name;
                    break;
                }
            }
        }
        Logger.debug("resolveType:", key, "->", typename);
        return typename;
    }

    private String resolveFunc(String key, List<DefUse> a) {
        String typename = null;
        Feat[] feats = _fset.get(key);
        if (feats != null) {
            for (Feat feat : feats) {
                if (feat instanceof TypeFeat) {
                    typename = feat.name;
                } else if (feat instanceof VarFeat) {
                    a.add(new AssVar(feat.name));
                }
            }
        }
        Logger.debug("resolveFunc:", key, "->", typename);
        return typename;
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
                    b.append(du.toString());
                }
                out.println(b.toString());
            }
            out.println();
        }

        out.close();
    }
}
