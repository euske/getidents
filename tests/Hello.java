package foo.bar;

class Person {

    String name;

    Person(String x) {
        String y = x;
        this.name = y;
    }

    String greet(String msg) {
        return msg+", "+name;
    }
}

public class Hello {
    public static void main(String[] args) {
        String arg0 = args[0];
        Person p = new Person(arg0);
        System.out.println(p.greet("Hello"));
        System.out.println(p.name);
    }
}
