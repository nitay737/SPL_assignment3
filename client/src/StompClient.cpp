#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include <thread>
#include <iostream>
#include <string>
#include <atomic>

int main(int argc, char *argv[]) {
    StompProtocol protocol;
    std::thread readerThread;
    std::cout << "Client started" << std::endl;
    while (!protocol.shouldTerminate()) {
        std::string input;
        if (!std::getline(std::cin, input)) {
            break;
        }
        bool wasLoggedInBefore = protocol.isClientLoggedIn();
        protocol.handleInput(input);
        if (!wasLoggedInBefore && protocol.isClientLoggedIn()) {
            readerThread = std::thread([&protocol]() {
                while (protocol.isClientLoggedIn()) {
                    ConnectionHandler* ch = protocol.getConnectionHandler();
                    if (!ch) 
                        break;
                    std::string answer;
                    if (!ch->getFrameAscii(answer, '\0')) {
                        if (!protocol.shouldTerminate())
                            std::cerr << "Failed to read from server." << std::endl;
                        else
                            std::cout << "Connection closed." << std::endl;
                        break;
                    };
                    protocol.handleFrames(answer);
                }
            });
        }
    }
    if (readerThread.joinable())
        readerThread.join();
    return 0;
}