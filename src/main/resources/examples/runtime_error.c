#include <stdio.h>

int average(int values[], int n)
{
    int sum = 0;
    for (int i = 0; i <= n; i++)
        sum += values[i];
    return sum / n;
}

int main(void)
{
    int data[4] = {3, 5, 7, 9};
    printf("Рахуємо середнє...\n");
    printf("Середнє: %d\n", average(data, 4));
    return 0;
}
