import java.util.List;

public class OOPS {
    public static void main(String[] args) {
        List<MathOperation> operations = List.of(
                new Addition(),
                new Subtraction(),
                new Multiplication());

        Calculator calculator = new Calculator();

        for (MathOperation operation : operations) {
            int result = calculator.calculate(operation, 10, 5);

            System.out.printf(
                    "%s: %d%n",
                    operation.name(),
                    result);
        }
    }
}

interface MathOperation {
    String name();

    int execute(int a, int b);
}

class Addition implements MathOperation {
    @Override
    public String name() {
        return "Addition";
    }

    @Override
    public int execute(int a, int b) {
        return a + b;
    }
}

class Subtraction implements MathOperation {
    @Override
    public String name() {
        return "Subtraction";
    }

    @Override
    public int execute(int a, int b) {
        return a - b;
    }
}

class Multiplication implements MathOperation {
    @Override
    public String name() {
        return "Multiplication";
    }

    @Override
    public int execute(int a, int b) {
        return a * b;
    }
}

class Calculator {
    public int calculate(MathOperation operation, int a, int b) {
        return operation.execute(a, b);
    }
}