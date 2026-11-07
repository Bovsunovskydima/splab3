#include <stdio.h>

void print_array(int a[], int n)
{
    for (int i = 0; i < n; i++)
        printf("%d%s", a[i], i + 1 < n ? ", " : "\n");
}

int bubble_sort(int a[], int n)
{
    int swaps = 0;
    for (int i = 0; i < n - 1; i++) {
        int swapped = 0;
        for (int j = 0; j < n - 1 - i; j++) {
            if (a[j] > a[j + 1]) {
                int t = a[j];
                a[j] = a[j + 1];
                a[j + 1] = t;
                swaps++;
                swapped = 1;
            }
        }
        if (!swapped)
            break;
    }
    return swaps;
}

int main(void)
{
    int data[] = {64, 34, 25, 12, 22, 11, 90, 5, 77, 1};
    int n = 10;
    printf("До сортування:    ");
    print_array(data, n);
    int swaps = bubble_sort(data, n);
    printf("Після сортування: ");
    print_array(data, n);
    printf("Кількість обмінів: %d\n", swaps);
    return 0;
}
