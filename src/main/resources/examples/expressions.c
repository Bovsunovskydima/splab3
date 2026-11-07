#include <stdio.h>

int main(void)
{
    int a = 5, b = 3, c = 10;

    printf("2 + 3 * 4       = %d\n", 2 + 3 * 4);
    printf("(2 + 3) * 4     = %d\n", (2 + 3) * 4);
    printf("10 - 4 - 3      = %d\n", 10 - 4 - 3);
    printf("-a * b          = %d\n", -a * b);
    printf("a + b * c %% 7   = %d\n", a + b * c % 7);
    printf("7 / 2 = %d, -7 / 2 = %d, -7 %% 2 = %d\n", 7 / 2, -7 / 2, -7 % 2);
    printf("a < b == 0      = %d\n", a < b == 0);
    printf("a > b && b > c  = %d\n", a > b && b > c);
    printf("a > b || b > c  = %d\n", a > b || b > c);
    printf("!a, !0          = %d, %d\n", !a, !0);

    printf("6 & 3 = %d, 6 | 3 = %d, 6 ^ 3 = %d, ~6 = %d\n", 6 & 3, 6 | 3, 6 ^ 3, ~6);
    printf("1 << 10 = %d, -64 >> 3 = %d, 1 + 2 << 3 = %d\n", 1 << 10, -64 >> 3, 1 + 2 << 3);

    int x = 1, y, z;
    x = y = z = 7;
    printf("x = y = z = 7   -> %d %d %d\n", x, y, z);
    x += 3;
    y -= 2;
    z *= 3;
    printf("x+=3 y-=2 z*=3  -> %d %d %d\n", x, y, z);
    x %= 4;
    y <<= 2;
    z ^= 5;
    printf("x%%=4 y<<=2 z^=5 -> %d %d %d\n", x, y, z);

    int i = 5;
    int p = i++;
    int q = ++i;
    printf("i++ = %d, ++i = %d, i = %d\n", p, q, i);
    int r = -i++;
    printf("-i++ = %d, i = %d\n", r, i);

    int max = a > b ? a : b;
    int sign = c > 0 ? 1 : c < 0 ? -1 : 0;
    printf("max(a, b) = %d, sign(c) = %d\n", max, sign);

    double d = 7 / 2;
    double e = 7 / 2.0;
    int t = (int) 3.99;
    char ch = 'A' + 2;
    printf("7/2 -> %.1f, 7/2.0 -> %.1f, (int)3.99 = %d, 'A'+2 = %c (%d)\n", d, e, t, ch, ch);

    int big = 2147483647;
    big = big + 1;
    printf("INT_MAX + 1 = %d\n", big);
    printf("Шістнадцяткове: %x %X %#x, вісімкове: %o\n", 255, 48879, 255, 8);
    return 0;
}
