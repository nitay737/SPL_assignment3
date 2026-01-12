#pragma once

#include "..\client\include\ConnectionHandler.h"
#include <atomic>

// TODO: implement the STOMP protocol
class StompProtocol
{
public:
    StompProtocol (ConnectionHandler* connectionHandler, std::atomic<bool> shouldClose);
    void handleInput(const std::string& input);

private:
    void handleLogin(const std::vector<std::string>& params);
    void handleJoin(const std::vector<std::string>& params);
    void handleExit(const std::vector<std::string>& params);
    void handleReport(const std::vector<std::string>& params);
    void handleSummary(const std::vector<std::string>& params);
    void handleLogout();

    ConnectionHandler* connectionHandler;
    std::atomic<bool> shouldClose;
    int id;
};
