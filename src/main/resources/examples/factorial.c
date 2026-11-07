#include <stdio.h>

int factorial(int n)
{
    if (n <= 1)
        return 1;
    return n * factorial(n - 1);
}

int factorial_loop(int n)
{
    int result = 1;
    for (int i = 2; i <= n; i++)
        result *= i;
    return result;
}

int main(void)
{
    for (int n = 0; n <= 12; n++) {
        printf("%2d! = %10d  (цикл: %d)\n", n, factorial(n), factorial_loop(n));
    }
    return 0;
}
