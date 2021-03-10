//  Utils.java
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
