#include <stdio.h>

int add(int a, int b)
{
    return a + b;
}

int minus(int a, int b)
{
    return a - b;
}

int multiply(int a, int b)
{
    return a * b;
}

int divide(int a, int b)
{
    if (b == 0)
    {
        return 0;
    }
    return a / b;
}

int main()
{
    int a = 10;
    int b = 5;
    printf("%d \n", add(a, b));
    printf("%d \n", minus(a, b));
    printf("%d \n", multiply(a, b));
    printf("%d \n", divide(a, b));
}