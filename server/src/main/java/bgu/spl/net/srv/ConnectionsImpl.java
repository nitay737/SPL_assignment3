package bgu.spl.net.srv;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Flow.Subscriber;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.*;
import bgu.spl.net.impl.data.User;

public class ConnectionsImpl implements Connections<StompMessage> {

    // (subscriptionId ,IdConnect)
    private ConcurrentHashMap<Integer ,Integer> subsId;
    // (subscriptionName ,{subscriptionId,...} )
    private ConcurrentHashMap<String ,Set<Integer>> channels;
    // (IdConnect, handler)
    private ConcurrentHashMap<Integer, ConnectionHandler<StompMessage>> handlersId;

    private Integer messageId = 0;
    
    public ConnectionsImpl(ConcurrentHashMap<Integer, ConnectionHandler<StompMessage>> users)
    {
        channels = new ConcurrentHashMap<>();
        subsId = new ConcurrentHashMap<>();
        handlersId = users;
    }

    @Override
    public boolean send(int connectionId, StompMessage msg)
    {
        if(handlersId.contains(connectionId))
        {
            handlersId.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, StompMessage msg)
    {
        if(!channels.contains(channel))
        {
            throw new IllegalArgumentException();
        }
        for (Integer subId : channels.get(channel)) {
            StompMessage response = new StompMessage(StompMessage.stompCommand.MESSAGE, new HashMap<>(), msg.getBody());
            response.addHeader("subscription", subId.toString());
            response.addHeader("message - id", messageId.toString());
            messageId++;
            response.addHeader("destination", channel);
            send(subsId.get(subId), response);
        }
    }

    @Override
    public void disconnect(int connectionId)
    {
        if(handlersId.contains(connectionId))
        {
            try{
                handlersId.get(connectionId).close();
                for (Integer subId : subsId.keySet()) {
                    if(subsId.get(subId) == connectionId)
                        subsId.remove(subId);
                    for (String channel : channels.keySet()) {
                        if(channels.get(channel).contains(subId))
                            channels.get(channel).remove(subId);
                    }
                }
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public void connect(Integer connectionsId, ConnectionHandler<StompMessage> handler)
    {
        handlersId.put(connectionsId, handler);
    }

    public void subscribe(String channel,int connectionId, int subId)
    {
        if(!channels.contains(channel))
        {
            throw new IllegalArgumentException();
        }
        channels.get(channel).add(subId);
        subsId.put(subId, connectionId);

    }

    public void unsubscribe(Integer subId)
    {
        if(!subsId.contains(subId))
            return;
        for (String channel : channels.keySet()) {
            if(channels.get(channel).contains(subId))
                channels.get(channel).remove(subId);
        }
        subsId.remove(subId);
    }

    public ConnectionHandler<StompMessage> getHandler(Integer connectionId)
    {
        return handlersId.get(connectionId);
    }
}
