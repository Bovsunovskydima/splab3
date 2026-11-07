#include <stdio.h>

int main(void)
{
    int n = 100;
    int composite[101];
    for (int i = 0; i <= n; i++)
        composite[i] = 0;

    for (int p = 2; p * p <= n; p++) {
        if (composite[p])
            continue;
        for (int k = p * p; k <= n; k += p)
            composite[k] = 1;
    }

    int count = 0;
    printf("Прості числа до %d:\n", n);
    for (int i = 2; i <= n; i++) {
        if (composite[i])
            continue;
        printf("%4d", i);
        count++;
        if (count % 10 == 0)
            printf("\n");
    }
    printf("\nУсього: %d\n", count);

    int x = 2;
    while (1) {
        if (x * x > 1000)
            break;
        x++;
    }
    printf("Найменше x, для якого x*x > 1000: %d\n", x);
    return 0;
}
