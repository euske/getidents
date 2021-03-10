package getIdents;

//  IdentType
//
public enum IdentType {
    TYPE("t"),
    FUNC("f"),
    VAR("v");

    public String code;

    IdentType(String code) {
        this.code = code;
    }
}
