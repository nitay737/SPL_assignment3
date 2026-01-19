#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include "StompProtocol.h"
#include <atomic>
#include <unordered_map>
#include <mutex>
#include <iostream>
#include <fstream> 
#include <algorithm>

StompProtocol::StompProtocol(): connectionHandler(nullptr), isLoggedIn(false), shouldClose(false), currentUser(""),idC(0),
    idR(0),channels(),mutex(),userEvents(),receipts() {}

StompProtocol::~StompProtocol() {
    if (connectionHandler) {
        connectionHandler->close();
        delete connectionHandler;
        connectionHandler = nullptr;
    }
}

bool StompProtocol::handleInput(const std::string& input){
    if (input.empty()){
        std::cerr<< "empty input" <<std::endl;
        return false;
    } 
    std::vector<std::string> params = split(input,' ');
    std::string command = params[0];
    if (command == "login") {
        return handleLogin(params);
    } else if (command == "join") {
        return handleJoin(params);
    } else if (command == "exit") {
        return handleExit(params);
    } else if (command == "report") {
        return handleReport(params);
    } else if (command == "summary") {
        return handleSummary(params);
    } else if (command == "logout") {
        return handleLogout();
    } else {
        std::cerr << "invalid input" << std::endl;
        return false;
    }
}

bool StompProtocol::handleFrames(const std::string &msg){
    std::string command;
    size_t i = 0;
    while (i < msg.length() && msg[i] != '\n') {
        command += msg[i];
        i++;
    }
    if (command == "CONNECTED") {
        return handleConnected();
    } else if (command == "MESSAGE") {
        return handleMessage(msg);
    } else if (command == "RECEIPT") {
        return handleReceipt(msg);
    } else if (command == "ERROR") {
        return handleError(msg);
    } else {
        std::cerr << "frame is not legal" << std::endl;
        return false;
    }
}

bool StompProtocol::shouldTerminate(){
    return shouldClose.load();
}

bool StompProtocol::isClientLoggedIn(){
    return isLoggedIn.load();
}

ConnectionHandler* StompProtocol::getConnectionHandler() {
    return connectionHandler;
}

bool StompProtocol::handleLogin(const std::vector<std::string>& params){
    if (isLoggedIn) {
        std::cout << "The client is already logged in, log out before trying again" << std::endl;
        return false;
    }
    currentUser = params[2];
    std::string connectFrame = std::string("CONNECT\n") +
        "accept-version:1.2\n" +
        "host:stomp.cs.bgu.ac.il\n" +
        "login:" + currentUser + "\n" +
        "passcode:" + params[3] + "\n" +
        "\n";
    size_t colonPos = params[1].find(':');
    if (colonPos == std::string::npos) {
        std::cerr << "invalid input" << std::endl;
        return false;
    }
    std::string host = params[1].substr(0, colonPos);
    int port = std::stoi(params[1].substr(colonPos + 1));
    if (connectionHandler != nullptr) {
        delete connectionHandler;
    }
    connectionHandler = new ConnectionHandler(host, port);
    if (!connectionHandler->connect()) {
        std::cerr << "Could not connect to server" << std::endl;
        delete connectionHandler;
        connectionHandler = nullptr;
        return false;
    }
    if (!connectionHandler->sendFrameAscii(connectFrame, '\0')) {
        std::cerr << "Error sending CONNECT frame" << std::endl;
        connectionHandler->close();
        delete connectionHandler;
        connectionHandler = nullptr;
        return false;
    }
    // readerThread does not exist until the user is connecting, we handle the answer here
    std::string answer;
    if (!connectionHandler->getFrameAscii(answer, '\0')) {
        std::cerr << "Error reading CONNECTED frame" << std::endl;
        connectionHandler->close();
        delete connectionHandler;
        connectionHandler = nullptr;
        return false;
    }
    if (answer.find("CONNECTED") != std::string::npos) {
        return handleConnected();
    } 
    else if (answer.find("ERROR") != std::string::npos) {
        handleError(answer);
        return false;
    } 
    else {
        std::cerr << "wrong frame" << std::endl;
        connectionHandler->close();
        delete connectionHandler;
        connectionHandler = nullptr;
        return false;
    }
}

bool StompProtocol::handleJoin(const std::vector<std::string>& params){
    std::string channel (params[1]);
    std::lock_guard<std::mutex> lock (mutex);
    if (channels.find(channel) != channels.end()) {
        std::cerr << "channel already joined" << std::endl;
        return false;
    }
    int curIdC = idC.fetch_add(1);
    int curIdR = idR.fetch_add(1);
    std::string connectFrame = std::string("SUBSCRIBE\n") + 
        "destination:" + channel + "\n" +
        "id:" + std::to_string(curIdC) + "\n" +
        "receipt:" + std::to_string(curIdR) + "\n" +
        "\n";
    receipts[curIdR] = {"join",channel};
    channels.insert({channel, curIdC});
    if (!connectionHandler->sendFrameAscii(connectFrame, '\0')) {
        std::cerr << "Error sending SUBSCRIBE frame" << std::endl;
        return false;
    }
    return true;
}

bool StompProtocol::handleExit(const std::vector<std::string>& params){
    std::string channel (params[1]);
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
    receipts[curIdR] = {"exit",channel};
    channels.erase(channel);
    if (!connectionHandler->sendFrameAscii(connectFrame, '\0')) {
        std::cerr << "Error sending UNSUBSCRIBE frame" << std::endl;
        return false;
    }
    return true;
}

bool StompProtocol::handleReport(const std::vector<std::string>& params){
    names_and_events nne = parseEventsFile(params[1]);
    std::string game_name = nne.team_a_name + "_" + nne.team_b_name;
    for (Event evn : nne.events) {
        std::lock_guard<std::mutex> lock (mutex);
        userEvents[currentUser][game_name].push_back(evn);
        std::string game_updates;
        std::string team_a_updates;
        std::string team_b_updates;
        for (const auto& pair : evn.get_game_updates())
            game_updates += pair.first + ": " + pair.second + "\n";
        for (const auto& pair : evn.get_team_a_updates())
            game_updates += pair.first + ": " + pair.second + "\n";
        for (const auto& pair : evn.get_team_b_updates())
            game_updates += pair.first + ": " + pair.second + "\n";
        std::string connectFrame = std::string("SEND\n") +
        "destination:" + game_name + "\n\n" +
        "user: " + currentUser + "\n" +
        "team a: " + nne.team_a_name + "\n" +
        "team b: " + nne.team_b_name + "\n" +
        "event name: " + evn.get_name() + "\n" +
        "time: " + std::to_string(evn.get_time()) + "\n" +
        "general game updates:\n" + game_updates +
        "team a updates:\n" + team_a_updates +
        "team b updates:\n" + team_b_updates +
        "description:\n" +
        evn.get_discription() + "\n" +
        "\n";
        if (!connectionHandler->sendFrameAscii(connectFrame, '\0'))
            std::cerr << "Error sending SEND frame" << std::endl;
    }
    return true;
}

bool  StompProtocol::handleSummary(const std::vector<std::string>& params){
    std::string game_name = params[1];
    std::string user = params[2];
    std::string file_path = params[3];

    size_t pos = game_name.find('_');
    std::string team_a_name = game_name.substr(0, pos);
    std::string team_b_name = game_name.substr(pos + 1);

    std::ofstream file(file_path);
    if (!file.is_open()) {
        std::cerr << "Failed to open file at " << file_path << std::endl;
        return false;
    }

    std::lock_guard<std::mutex> lock (mutex);
    std::vector<Event> events = userEvents[user][game_name];
    std::map<std::string,std::string> game_updates;
    std::map<std::string,std::string> team_a_updates;
    std::map<std::string,std::string> team_b_updates;
    for (const Event& evn : events) {
        game_updates.insert(evn.get_game_updates().begin(), evn.get_game_updates().end());
        team_a_updates.insert(evn.get_team_a_updates().begin(), evn.get_team_a_updates().end());
        team_b_updates.insert(evn.get_team_b_updates().begin(), evn.get_team_b_updates().end());
    }
    std::sort(events.begin(), events.end(), compareEventsByTime);

    file << team_a_name << " vs " << team_b_name << std::endl;
    file << "Game stats:\nGeneral stats:" << std::endl;
    for (const auto& pair : game_updates) {
            file << pair.first << ": " << pair.second << std::endl;
        }
    file << team_a_name << " stats:" << std::endl;
    for (const auto& pair : team_a_updates) {
            file << pair.first << ": " << pair.second << std::endl;
        }
    file << team_b_name << " stats:" << std::endl;
    for (const auto& pair : team_b_updates) {
            file << pair.first << ": " << pair.second << std::endl;
        }
    file << "Game event reports:" << std::endl;
    for (const Event& evn : events) {
        file << evn.get_time() << " - " << evn.get_name() << std::endl;
        file << "\n" << evn.get_discription() << "\n" << std::endl;
        }
    file.close();
    return true;
}

bool StompProtocol::handleLogout(){
    int curIdR = idR.fetch_add(1);
    std::string connectFrame = std::string("DISCONNECT\n") +
        "receipt:" + std::to_string(curIdR) + "\n" +
        "\n";
    std::lock_guard<std::mutex> lock (mutex);
    receipts[curIdR] = {"logout",""};
    channels.clear();
    idC.store(0);
    if (!connectionHandler->sendFrameAscii(connectFrame, '\0')) {
        std::cerr << "Error sending DISCONNECT frame" << std::endl;
        return false;
    }
    return true;
}

bool StompProtocol::handleConnected(){
    isLoggedIn.store(true);
    std::cout << "connected successfully" << std::endl;
    return true;
}

bool StompProtocol::handleMessage(const std::string& msg) {
    std::vector<std::string> lines = split(msg, '\n'); 

    std::string game_name;
    std::string user;
    std::string team_a_name, team_b_name, event_name, description;
    int time;
    std::map<std::string, std::string> general_updates, team_a_updates, team_b_updates;
    bool isBody = false;
    std::string current_section = "header";

    for (std::string line : lines) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (!isBody) {
            if (line.empty()) {
                isBody = true;
                continue;
            }
            if (line.find("destination:") == 0) {
                game_name = line.substr(12);
            }
        } 
        else {
            if (line.empty()) continue;
            if (line == "general game updates:") {
                current_section = "general";
                continue;
            } else if (line == "team a updates:") {
                current_section = "team_a";
                continue;
            } else if (line == "team b updates:") {
                current_section = "team_b";
                continue;
            } else if (line == "description:") {
                current_section = "description";
                continue;
            }
            if (current_section == "description") {
                description += line + "\n";
            } else {
                size_t colonPos = line.find(':');
                if (colonPos != std::string::npos) {
                    std::string key = line.substr(0, colonPos);
                    std::string value = line.substr(colonPos + 1);
                    if (!value.empty() && value[0] == ' ') value = value.substr(1);
                    if (current_section == "header") {
                        if (key == "user") user = value;
                        else if (key == "team a") team_a_name = value;
                        else if (key == "team b") team_b_name = value;
                        else if (key == "event name") event_name = value;
                        else if (key == "time") time = std::stoi(value);
                    } 
                    else if (current_section == "general") general_updates[key] = value;
                    else if (current_section == "team_a") team_a_updates[key] = value;
                    else if (current_section == "team_b") team_b_updates[key] = value;
                }
            }
        }
    }
    if (user.empty() || game_name.empty()) return false;
    Event event(event_name, team_a_name, team_b_name, time, 
                general_updates, team_a_updates, team_b_updates, description);
    std::lock_guard<std::mutex> lock (mutex);
    userEvents[user][game_name].push_back(event);
    return true;
}

bool StompProtocol::handleReceipt(const std::string &msg){
    std::vector<std::string> lines = split(msg, '\n'); 
    if (lines.size() < 2) return false;
    size_t colonPos = lines[1].find(':');
    if (colonPos != std::string::npos){
        int receiptId = std::stoi(lines[1].substr(colonPos + 1));
        std::lock_guard<std::mutex> lock (mutex);
        auto it = receipts.find(receiptId);
        if (it != receipts.end()) {
            std::string operation = it->second.operation;
            std::string gameName = it->second.game_name;
            if (operation == "join") {
                std::cout << "Joined channel " << gameName << std::endl;
                receipts.erase(it);
                return true;
            } 
            else if (operation == "exit") {
                std::cout << "Exited channel " << gameName << std::endl;
                receipts.erase(it);
                return true;
            } 
            else if (operation == "logout") {
                connectionHandler->close();
                isLoggedIn.store(false);
                shouldClose.store(true);
                receipts.erase(it);
                return true;
            }
        }
    }
    std::cerr << "invalid frame" << std::endl;
    return false;
}

bool StompProtocol::handleError(const std::string &msg) {
    std::vector<std::string> lines = split(msg, '\n');
    size_t colonPos = lines[2].find(':');
    if (colonPos != std::string::npos){
        std::cerr << "ERROR: " << lines[2].substr(colonPos + 1) << std::endl;
    }
    connectionHandler->close();
    //shouldClose.store(true);
    return true;
}

bool StompProtocol::compareEventsByTime(const Event& a, const Event& b) {
    return a.get_time() < b.get_time();
}

std::vector<std::string> StompProtocol::split(const std::string& str, char delimiter) {
    std::vector<std::string> params;
    std::string word;
    for (char ch : str){
        if (ch == delimiter){
            if (!word.empty()){
                params.push_back(word);
                word.clear();
            }
        }
        else
            word += ch;
    }
    if (!word.empty())
        params.push_back(word);
    return params;
}

