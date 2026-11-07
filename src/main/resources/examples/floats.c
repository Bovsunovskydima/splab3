#include <stdio.h>
#include <math.h>

double newton_sqrt(double x)
{
    double guess = x / 2;
    for (int i = 0; i < 20; i++)
        guess = (guess + x / guess) / 2;
    return guess;
}

double power(double base, int exp)
{
    double result = 1.0;
    for (int i = 0; i < exp; i++)
        result *= base;
    return result;
}

int main(void)
{
    for (int i = 2; i <= 10; i += 2) {
        double mine = newton_sqrt(i);
        double lib = sqrt(i);
        printf("sqrt(%2d): Ньютон = %.10f, sqrt = %.10f, різниця = %e\n", i, mine, lib, fabs(mine - lib));
    }

    printf("2^10 = %.0f, pow(2, 0.5) = %.6f\n", power(2, 10), pow(2, 0.5));

    float f = 1.0 / 3;
    double g = 1.0 / 3;
    printf("float  1/3 = %.10f\n", f);
    printf("double 1/3 = %.10f\n", g);

    double pi = 0;
    for (int k = 0; k < 100000; k++)
        pi += (k % 2 == 0 ? 4.0 : -4.0) / (2 * k + 1);
    printf("pi (ряд Лейбніца) = %.6f\n", pi);
    printf("Формати: %g %g %g %e\n", 100000.0, 1000000.0, 0.0001234, 12345.678);
    printf("Округлення: floor(-2.5) = %.1f, ceil(-2.5) = %.1f\n", floor(-2.5), ceil(-2.5));
    return 0;
}
