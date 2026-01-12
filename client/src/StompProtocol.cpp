#pragma once

#include "..\client\include\ConnectionHandler.h"
#include <atomic>

// TODO: implement the STOMP protocol
class StompProtocol
{
public:
    StompProtocol (ConnectionHandler* connectionHandler):
        connectionHandler(connectionHandler), shouldClose(false), id(0) {}

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
        std::string connectFrame = std::string("CONNECT\n") + "accept-version:1.2\n" +
            "host:stomp.cs.bgu.ac.il\n" +
            "login:" + params[1].substr(1,params[1].length()-2) + "\n" +
            "passcode:" + params[2].substr(1,params[2].length()-2) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleJoin(const std::vector<std::string>& params){
        std::string connectFrame = std::string("SUBSCRIBE\n") +
            "destination:" + params[1].substr(1,params[1].length()-2) + "\n" +
            "id:" + (id++) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleExit(const std::vector<std::string>& params){
        std::string connectFrame = std::string("SUBSCRIBE\n") +
            "destination:" + params[1].substr(1,params[1].length()-2) + "\n" +
            "id:" + std::to_string(id++) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleReport(const std::vector<std::string>& params){
        std::string connectFrame = std::string("UNSUBSCRIBE\n") +
            "id:" + (id++) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleSummary(const std::vector<std::string>& params){
        std::string connectFrame = std::string("SUBSCRIBE\n") +
            "destination:" + params[1].substr(1,params[1].length()-2) + "\n" +
            "id:" + (id++) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }

    bool handleLogout(){
        std::string connectFrame = std::string("SUBSCRIBE\n") +
            "destination:" + params[1].substr(1,params[1].length()-2) + "\n" +
            "id:" + (id++) + "\n" +
            "\n";
        return connectionHandler->sendFrameAscii(connectFrame, '\0');
    }
};
