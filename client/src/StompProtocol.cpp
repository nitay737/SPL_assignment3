#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <atomic>
#include <unordered_map>
#include <mutex>
#include <fstream>
#include "StompProtocol.h"


StompProtocol::StompProtocol (ConnectionHandler* connectionHandler):
    connectionHandler(connectionHandler), shouldClose(false), idC(0), idR(0) {}

bool StompProtocol::handleInput(const std::string& input){
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
        std::cerr << "frame is not legal" << std::endl;
        return false;
    }
}

bool StompProtocol::handleFrames(const std::string &msg){
    std::string command;
    int i = 0;
    while (i < msg.length() && msg[i] != '\n') {
        command += msg[i];
        i++;
    }
    if (command == "CONNECTED") {
        return handleConnected(msg);
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

bool StompProtocol::handleLogin(const std::vector<std::string>& params){
    currentUser = params[1];
    std::string connectFrame = "CONNECT\n" +
        "accept-version:1.2\n" +
        "host:stomp.cs.bgu.ac.il\n" +
        "login:" + currentUser + "\n" +
        "passcode:" + params[2].substr(1,params[2].length()-2) + "\n" +
        "\n";
    return connectionHandler->sendFrameAscii(connectFrame, '\0');
}

bool StompProtocol::handleJoin(const std::vector<std::string>& params){
    std::string channel (params[1]);
    std::lock_guard<std::mutex> lock(mutex);
    if (channels.find(channel) != channels.end()) {
        std::cerr << "channel already joined" << std::endl;
        return false;
    }
    int curIdC = idC.fetch_add(1);
    int curIdR = idR.fetch_add(1);
    std::string connectFrame = "SUBSCRIBE\n" + 
        "destination: " + channel + "\n" +
        "id: " + std::to_string(curIdC) + "\n" +
        "receipt: " + std::to_string(curIdR) + "\n" +
        "\n";
    channels.insert({channel, curIdC});
    return connectionHandler->sendFrameAscii(connectFrame, '\0');
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
    channels.erase(channel);
    return connectionHandler->sendFrameAscii(connectFrame, '\0');
}

bool StompProtocol::handleReport(const std::vector<std::string>& params){
    names_and_events nne = parseEventsFile(params[1]);
    std::string game_name = nne.team_a_name + "_" + nne.team_b_name;
    for (Event evn : nne.events) {
        userEvents[currentUser][game_name].push_back(evn);
        std::string game_updates;
        std::string team_a_updates;
        std::string team_b_updates;
        for (const auto& [key, value] : evn.get_game_updates())
            game_updates += key + ": " + value + "\n";
        for (const auto& [key, value] : evn.get_team_a_updates())
            team_a_updates += key + ": " + value + "\n";
        for (const auto& [key, value] : evn.get_team_b_updates())
            team_b_updates += key + ": " + value + "\n";
        std::string connectFrame = std::string("SEND\n") +
        "destination:" + game_name + "\n\n" +
        "user:" + currentUser + "\n" +
        "team a:" + nne.team_a_name + "\n" +
        "team b:" + nne.team_b_name + "\n" +
        "event name:" + evn.get_name() + "\n" +
        "time:" + std::to_string(evn.get_time()) + "\n" +
        "general game updates:\n" + game_updates +
        "team a updates:\n" + team_a_updates +
        "team b updates:\n" + team_b_updates +
        "description:\n" +
        evn.get_discription() + "\n" +
        "\n";
        connectionHandler->sendFrameAscii(connectFrame, '\0');
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

    file << game_name << "\n Game stats:\n General stats:" << std::endl;
    for (const auto& [key, value] : game_updates) {
            file << key << ": " << value << std::endl;
        }
    file << team_a_name << " stats:" << std::endl;
    for (const auto& [key, value] : team_a_updates) {
            file << key << ": " << value << std::endl;
        }
    file << team_b_name << " stats:" << std::endl;
    for (const auto& [key, value] : team_b_updates) {
            file << key << ": " << value << std::endl;
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
    channels.clear();
    idC.store(0);
    return connectionHandler->sendFrameAscii(connectFrame, '\0');
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

