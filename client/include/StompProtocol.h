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
    StompProtocol();
    ~StompProtocol();
    StompProtocol(const StompProtocol&) = delete;
    StompProtocol& operator=(const StompProtocol&) = delete;
    bool handleInput(const std::string& input);
    bool handleFrames(const std::string& msg);
    ConnectionHandler* getConnectionHandler();

private:
    bool handleLogin(const std::vector<std::string>& params);
    bool handleJoin(const std::vector<std::string>& params);
    bool handleExit(const std::vector<std::string>& params);
    bool handleReport(const std::vector<std::string>& params);
    bool handleSummary(const std::vector<std::string>& params);
    bool handleLogout();
    bool handleConnected();
    bool handleMessage(const std::string& msg);
    bool handleReceipt(const std::string& msg);
    bool handleError(const std::string& msg);
    static bool compareEventsByTime(const Event& a, const Event& b);
    std::vector<std::string> split(const std::string& str, char delimiter);

    ConnectionHandler* connectionHandler;
    std::string currentUser;
    std::atomic<int> idC;
    std::atomic<int> idR;
    // (game_name, id)
    std::unordered_map<std::string, int> channels;
    std::mutex mutex;
    // (user, (game_name, game_event))
    std::unordered_map<std::string, std::unordered_map<std::string, std::vector<Event>>> userEvents;
    struct receiptInf {
        std::string operation;
        std::string game_name;
        receiptInf() : operation(""), game_name("") {}
        receiptInf(std::string op, std::string gn) : operation(op), game_name(gn) {}
    };
    // (receiptId, receiptInf)
    std::unordered_map<int, receiptInf> receipts;

};
