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
    StompProtocol (ConnectionHandler* connectionHandler);
    bool handleInput(const std::string& input);
    bool handleFrames(const std::string& msg);

private:
    bool handleLogin(const std::vector<std::string>& params);
    bool handleJoin(const std::vector<std::string>& params);
    bool handleExit(const std::vector<std::string>& params);
    bool handleReport(const std::vector<std::string>& params);
    bool handleSummary(const std::vector<std::string>& params);
    bool handleLogout();
    bool compareEventsByTime(const Event& a, const Event& b);
    std::vector<std::string> split(const std::string& str, char delimiter);

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
