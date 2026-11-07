#include <stdio.h>

void check(int a, int b)
{
    printf("a = %d, b = %d: ", a, b);
    if (a > 0)
        if (b > 0)
            printf("обидва додатні\n");
        else
            printf("a додатне, b ні\n");
    printf("\n");
}

int main(void)
{
    check(1, 1);
    check(1, -1);
    check(-1, 1);
    return 0;
}
