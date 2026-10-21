import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

public class FunctionalProg
{
    public static void main(String[] args)
    {

        // 1. Pure functions
        System.out.println("Addition: " + add(10, 5));
        System.out.println("Subtraction: " + subtract(10, 5));

        // 2. Higher-order function
        BinaryOperator<Integer> multiply = (a, b)->a * b;
        System.out.println("Multiplication: " + calculate(10, 5, multiply));

        // 3. Immutable data and functional transformations
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6);

        List<Integer> squares = numbers.stream()
                                    .map(n->n * n)
                                    .toList();

        System.out.println("Original: " + numbers);
        System.out.println("Squares: " + squares);

        // 4. Filtering with a predicate
        Predicate<Integer> isEven = n->n % 2 == 0;

        List<Integer> evenNumbers = numbers.stream()
                                        .filter(isEven)
                                        .toList();

        System.out.println("Even numbers: " + evenNumbers);

        // 5. Function composition
        Function<Integer, Integer> doubleIt = n->n * 2;
        Function<Integer, Integer> increment = n->n + 1;

        Function<Integer, Integer> doubleThenIncrement =
            increment.compose(doubleIt);

        System.out.println(
            "Double then increment: " + doubleThenIncrement.apply(5));
    }

    static int add(int a, int b)
    {
        return a + b;
    }

    static int subtract(int a, int b)
    {
        return a - b;
    }

    static int calculate(
        int a,
        int b,
        BinaryOperator<Integer> operation)
    {
        return operation.apply(a, b);
    }}