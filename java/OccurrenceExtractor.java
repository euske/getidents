//  OccurrenceExtractor.java
//

import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

public class OccurrenceExtractor extends ASTVisitor {

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

    public OccurrenceExtractor() {
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
                for (; i < args.length; i++) {
                    files.add(args[i]);
                }
            } else if (arg.equals("-o")) {
                out = new PrintStream(new FileOutputStream(args[i+1]));
                i++;
            } else if (arg.startsWith("-")) {
            } else {
                files.add(arg);
            }
        }

        String[] srcpath = { "." };
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

            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setUnitName(path);
            parser.setSource(src.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            parser.setEnvironment(null, srcpath, null, true);
            parser.setCompilerOptions(options);
            CompilationUnit cu = (CompilationUnit)parser.createAST(null);

            OccurrenceExtractor extractor = new OccurrenceExtractor();
            cu.accept(extractor);

            out.println("+ "+path);
            for (Ident ident : extractor.getIdents()) {
                String t;
                switch (ident.type) {
                case TYPE:
                    t = "t";
                    break;
                case FUNC:
                    t = "f";
                    break;
                case VAR:
                    t = "v";
                    break;
                default:
                    t = "?";
                    break;
                }
                out.println(t+ident.name);
            }
            out.println();
        }

        out.close();
    }
}
