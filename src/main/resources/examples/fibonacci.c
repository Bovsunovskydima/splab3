#include <stdio.h>

int memo[50];

int fib_recursive(int n)
{
    if (n < 2)
        return n;
    return fib_recursive(n - 1) + fib_recursive(n - 2);
}

int fib_memo(int n)
{
    if (n < 2)
        return n;
    if (memo[n] != 0)
        return memo[n];
    memo[n] = fib_memo(n - 1) + fib_memo(n - 2);
    return memo[n];
}

int main(void)
{
    int a = 0, b = 1;
    printf("Ітеративно: ");
    for (int i = 0; i < 15; i++) {
        printf("%d ", a);
        int next = a + b;
        a = b;
        b = next;
    }
    printf("\nРекурсивно fib(20) = %d\n", fib_recursive(20));
    printf("З мемоізацією fib(45) = %d\n", fib_memo(45));
    return 0;
}
