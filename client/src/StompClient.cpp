#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include <thread>
#include <iostream>
#include <string>

int main(int argc, char *argv[]) {
    StompProtocol protocol;
    std::thread readerThread; 

    std::cout << "Client started" << std::endl;
    while (true) {
        std::string input;
        if (!std::getline(std::cin, input)) {
            break;
        }
        bool wasConnectedBefore = (protocol.getConnectionHandler() != nullptr);
        if (!protocol.handleInput(input)) {
            continue; 
        }
        bool isConnectedNow = (protocol.getConnectionHandler() != nullptr);
        if (!wasConnectedBefore && isConnectedNow) {
            if (readerThread.joinable()) {
                readerThread.join();
            }
            readerThread = std::thread([&protocol]() {
                while (protocol.getConnectionHandler() != nullptr) {
                    ConnectionHandler* ch = protocol.getConnectionHandler();
                    if (ch == nullptr) break; 
                    std::string answer;
                    if (!ch->getFrameAscii(answer, '\0')) {
                        std::cout << "Socket closed" << std::endl;
                        break; 
                    }
                    protocol.handleFrames(answer);
                }
            });
        }
    }
    if (readerThread.joinable()) {
        readerThread.join();
    }
    return 0;
}