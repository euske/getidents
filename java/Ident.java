//  Ident.java
//
package getIdents;

//  Ident
//
public class Ident {

    IdentType type;
    String name;

    public Ident(IdentType type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public String toString() {
        return this.type.code+this.name;
    }
}
