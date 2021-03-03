//  UseExtractor.java
//

import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

class ContextExtractor extends ASTVisitor {

    private Context _current = new Context("");
    private List<Context> _stack = new ArrayList<Context>();

    private class Context {
        private String _name;
        private List<String> _feats;
        public Context(String name) {
            _name = name;
            _feats = new ArrayList<String>();
        }
        @Override
        public String toString() {
            return _name;
        }
        public void addFeat(String feat) {
            _feats.add(feat);
        }
        public List<String> getFeats() {
            return _feats;
        }
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        _current = new Context(node.getName().getFullyQualifiedName());
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
        for (String name : _current.getFeats()) {
            put(_current.toString(), name);
        }
        pop();
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        Type type = node.getType();
        if (type instanceof SimpleType) {
            String name = node.getName().getIdentifier();;
            put(_current+"."+name, ((SimpleType)type).getName().getFullyQualifiedName());
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
                put(_current+"."+name, ((SimpleType)type).getName().getFullyQualifiedName());
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
                put(_current+"."+name, ((SimpleType)type).getName().getFullyQualifiedName());
            }
        }
        return true;
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        return true;
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
        _current = new Context(_current+"."+name);
        //System.out.println("currentName: "+_current);
    }
    private void pop() {
        _current = _stack.remove(_stack.size()-1);
    }

    private void put(String id, String type) {
        System.out.println("put: "+id+" "+type);
    }

}

public class UseExtractor extends ASTVisitor {

    private enum IdentType {
        TYPE, FUNC, VAR
    }

    private class Ident {
        IdentType type;
        String name;

        public Ident(IdentType type, String name) {
            this.type = type;
            this.name = name;
        }
    }


    private List<Ident> idents = new ArrayList<>();

    public UseExtractor() {
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        add(IdentType.TYPE, node.getName());
        return true;
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        add(IdentType.TYPE, node.getDeclaration().getName());
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        add(IdentType.TYPE, node.getName());
        return true;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        add(IdentType.FUNC, node.getName());
        return true;
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        add(IdentType.VAR, node.getName());
        return true;
    }

    @Override
    public boolean visit(VariableDeclarationFragment node) {
        add(IdentType.VAR, node.getName());
        return true;
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        add(IdentType.VAR, node.getName());
        return true;
    }

    public Ident[] getIdents() {
        Ident[] a = new Ident[idents.size()];
        idents.toArray(a);
        return a;
    }

    private void add(IdentType type, SimpleName name) {
        idents.add(new Ident(type, name.getIdentifier()));
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

            ContextExtractor extractor = new ContextExtractor();
            cunit.accept(extractor);
        }

        System.err.println("Pass 2.");


        out.close();
    }
}
