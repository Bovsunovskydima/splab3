#include <stdio.h>

int moves = 0;

void hanoi(int n, char from, char to, char via)
{
    if (n == 0)
        return;
    hanoi(n - 1, from, via, to);
    moves++;
    if (moves <= 7)
        printf("Крок %d: диск %d з %c на %c\n", moves, n, from, to);
    hanoi(n - 1, via, to, from);
}

int main(void)
{
    hanoi(3, 'A', 'C', 'B');
    printf("Для 3 дисків: %d ходів\n", moves);
    moves = 0;
    hanoi(16, 'A', 'C', 'B');
    printf("Для 16 дисків: %d ходів\n", moves);
    return 0;
}
