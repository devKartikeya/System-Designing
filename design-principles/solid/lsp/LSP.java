
public class LSP {

    public static void main(String[] args) {

        System.out.println("=== BEFORE LSP ===");

        BeforeBird sparrow = new BeforeSparrow();
        BeforeBird penguin = new BeforePenguin();

        makeBirdFly(sparrow);

        try {
            makeBirdFly(penguin);
        } catch (UnsupportedOperationException error) {
            System.out.println("Problem: " + error.getMessage());
        }

        System.out.println("\n=== AFTER LSP ===");

        Bird sparrow2 = new Sparrow();
        Bird penguin2 = new Penguin();

        sparrow2.eat();
        penguin2.eat();

        makeFlyingBirdFly(new Sparrow());
    }

    // BEFORE: Parent promises that every bird can fly.
    static class BeforeBird {
        void fly() {
            System.out.println("Bird is flying");
        }
    }

    static class BeforeSparrow extends BeforeBird {
        @Override
        void fly() {
            System.out.println("Sparrow is flying");
        }
    }

    static class BeforePenguin extends BeforeBird {
        @Override
        void fly() {
            throw new UnsupportedOperationException(
                    "Penguins cannot fly");
        }
    }

    static void makeBirdFly(BeforeBird bird) {
        bird.fly();
    }

    // AFTER: Bird behavior doesn't promise flying.
    interface Bird {
        void eat();
    }

    interface FlyingBird extends Bird {
        void fly();
    }

    static class Sparrow implements FlyingBird {
        public void eat() {
            System.out.println("Sparrow is eating");
        }

        public void fly() {
            System.out.println("Sparrow is flying");
        }
    }

    static class Penguin implements Bird {
        public void eat() {
            System.out.println("Penguin is eating");
        }
    }

    static void makeFlyingBirdFly(FlyingBird bird) {
        bird.fly();
    }
}