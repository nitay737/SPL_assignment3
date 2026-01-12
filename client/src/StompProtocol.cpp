#pragma once

#include "..\client\include\ConnectionHandler.h"
#include <atomic>
#include <unordered_map>
#include <mutex>

// TODO: implement the STOMP protocol
class StompProtocol
{
public:
    StompProtocol (ConnectionHandler* connectionHandler):
        connectionHandler(connectionHandler), shouldClose(false), idC(0), idR(0) {}

    void handleInput(const std::string& input){
    std::vector<std::string> params = split(input, ' ');
    switch params[0]:
    case "login":
        handleLogin(params);
        break;
    case "join":
        handleJoin(params);
        break;
    case "exit":
        handleExit(params);
        break;
    case "report":
        handleReport(params);
        break;
    case "summary":
        handleSummary(params);
        break;
    case "logout":
        handleLogout();
        break;
}

private:
    bool handleLogin(const std::vector<std::string>& params){
        std::string connectFrame = "CONNECT\n" +
            "accept-version:1.2\n" +
            "host:stomp.cs.bgu.ac.il\n" +
            "login:" + params[1].substr(1,params[1].length()-2) + "\n" +
            "passcode:" + params[2].substr(1,params[2].length()-2) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleJoin(const std::vector<std::string>& params){
        std::string channel (params[1].substr(1,params[1].length()-2));
        std::lock_guard<std::mutex> lock(mutex);
        if (channels.find(channel) != channels.end()) {
            std::cerr << "channel already joined" << std::endl;
            return false;
        }
        int curIdC = idC.fetch_add(1);
        int curIdR = idR.fetch_add(1);
        std::string connectFrame = "SUBSCRIBE\n" +
            "destination:" + channel + "\n" +
            "id:" + std::to_string(curIdC) + "\n" +
            "receipt:" + std::to_string(curIdR) + "\n" +
            "\n";
        channels.insert({channel, curIdC});
        
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleExit(const std::vector<std::string>& params){
        std::string channel (params[1].substr(1,params[1].length()-2));
        std::lock_guard<std::mutex> lock(mutex);
        auto it = channels.find(channel);
        if (it == channels.end()) {
            std::cerr<<"wrong channel"<<std::endl;
            return false;
        }
        int channelId = it->second;
        int curIdR = idR.fetch_add(1);
        std::string connectFrame = std::string("UNSUBSCRIBE\n") +
            "id:" + std::to_string(channelId) + "\n" +
            "receipt:" + std::to_string(curIdR) + "\n" +
            "\n";
        channels.erase(channel);
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleReport(const std::vector<std::string>& params){
        
    }

    bool handleSummary(const std::vector<std::string>& params){
    
    }

    bool handleLogout(){
        int curIdR = idR.fetch_add(1);
        std::string connectFrame = std::string("DISCONNECT\n") +
            "receipt:" + std::to_string(curIdR) + "\n" +
            "\n";
        channels.clear();
        idC.store(0);
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }
};
