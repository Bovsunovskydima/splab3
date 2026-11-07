#include <stdio.h>

int gcd(int a, int b)
{
    while (b != 0) {
        int r = a % b;
        a = b;
        b = r;
    }
    return a;
}

int gcd_recursive(int a, int b)
{
    return b == 0 ? a : gcd_recursive(b, a % b);
}

int lcm(int a, int b)
{
    return a / gcd(a, b) * b;
}

int digit_sum(int n)
{
    int sum = 0;
    do {
        sum += n % 10;
        n /= 10;
    } while (n > 0);
    return sum;
}

int main(void)
{
    int pairs[4][2] = {{48, 18}, {270, 192}, {17, 5}, {1071, 462}};
    for (int i = 0; i < 4; i++) {
        int a = pairs[i][0], b = pairs[i][1];
        printf("НСД(%d, %d) = %d = %d, НСК = %d\n", a, b, gcd(a, b), gcd_recursive(a, b), lcm(a, b));
    }
    printf("Сума цифр 987654: %d\n", digit_sum(987654));
    return 0;
}
