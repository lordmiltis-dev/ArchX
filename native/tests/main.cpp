// Smoke executable that prints a short welcome message and a loop demonstration.

#include <iostream>

int main() {
    const auto lang = "C++";
    std::cout << "Hello and welcome to " << lang << "!\n";

    for (int i = 1; i <= 5; i++) {
        std::cout << "i = " << i << std::endl;
    }

    return 0;
}
