#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"

#include <string>
#include <vector>
#include <map>
#include <unordered_map>
#include <atomic>
#include <mutex>

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
    std::string currentUser;
    std::atomic<bool> shouldClose;
    std::atomic<int> idC;
    std::atomic<int> idR;
    // (game_name, id)
    std::unordered_map<std::string, int> channels;
    std::mutex mutex;
    // (user, (game_name, game_event))
    std::unordered_map<std::string, std::unordered_map<std::string, std::vector<Event>>> userEvents;


};
