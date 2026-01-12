#include <string>
#include "../include/ConnectionHandler.h"

int main(int argc, char *argv[]) {
	std::string host = argv[0];
    short port = std::stoi(argv[1]);
    ConnectionHandler connectionHandler(host, port);
    std::atomic<bool> shouldClose{false};
	StompPtotocol sp()
    if (!connectionHandler.connect()) {
        std::cerr << "Could not connect to server." << std::endl;
        return 1;
    }

    std::thread reader_thread([&connectionHandler, &shouldClose]() {
        while (!shouldClose.load()) {
            std::string line;
            if (!connectionHandler.getLine(line)) {
                // If reading fails (connection closed or error), break the loop
                if (!shouldClose.load())
                    std::cerr << "Failed to read from server." << std::endl;
                else
                    std::cout << "Connection closed." << std::endl;
                shouldClose.store(true);
                break;
            }
            std::cout << "<Server> " << line << std::endl;
        }
    });

    // Main thread: Read input from stdin and send to server
    while (!shouldClose.load()) {
        std::string input;
        if (!std::getline(std::cin, input)) {
            shouldClose.store(true);
            connectionHandler.close();
            break;
        }
        std::vector<std::string> inputVec = split(input, ' ');
		if (inputVec[0] == "login") {
			handleLogin(inputVec);
		}
		else if (inputVec[0] == "join") {
			handleJoin(inputVec);
		}
		else if (inputVec[0] == "report") {
			handleReport(inputVec);
		}
		else if (inputVec[0] == "logout") {
			handleLogout();
		}

        if(!connectionHandler.sendLine(input) and !shouldClose.load()) {
            std::cerr << "Failed to send to server." << std::endl;
            shouldClose.store(true);
            connectionHandler.close();
            break;
        }
    }

    // Cleanup: Close connection and join reader thread
    connectionHandler.close();

    reader_thread.join();

    return 0;
}
