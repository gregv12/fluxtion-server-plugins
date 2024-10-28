public class HelloWorld {
    public void sayHello() {
        System.out.println("Hello World - Hello World");
        new HelloWorld.Test().sayHello();
        System.out.println();
    }

    public static class Test {
        public void sayHello() {
            System.out.println("Test - Hello World");
        }
    }
}