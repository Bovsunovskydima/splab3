#include <stdio.h>

void multiply(int a[][3], int b[][3], int c[][3], int n)
{
    for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++) {
            c[i][j] = 0;
            for (int k = 0; k < n; k++)
                c[i][j] += a[i][k] * b[k][j];
        }
}

void print_matrix(int m[][3], int n)
{
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++)
            printf("%5d", m[i][j]);
        printf("\n");
    }
}

int trace(int m[][3], int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++)
        sum += m[i][i];
    return sum;
}

int main(void)
{
    int a[3][3] = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    int b[3][3] = {{9, 8, 7}, {6, 5, 4}, {3, 2, 1}};
    int c[3][3];
    multiply(a, b, c, 3);
    printf("A * B =\n");
    print_matrix(c, 3);
    printf("Слід матриці: %d\n", trace(c, 3));
    return 0;
}
