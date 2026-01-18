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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.*;
import bgu.spl.net.impl.data.User;

class Subscription {
    int connectionId;
    int subscriptionId;

    public Subscription(int connectionId, int subscriptionId) {
        this.connectionId = connectionId;
        this.subscriptionId = subscriptionId;
    }
    
    // IMPORTANT: Implement equals and hashCode for Set operations to work!
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subscription that = (Subscription) o;
        return connectionId == that.connectionId && subscriptionId == that.subscriptionId;
    }
}

public class ConnectionsImpl implements Connections<StompMessage> {

    // (subscriptionName ,{Subscription,...} )
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<Subscription>> channels;
    // (connectionId, handler)
    private ConcurrentHashMap<Integer, ConnectionHandler<StompMessage>> handlersId;

    private AtomicInteger messageId;
    
    public ConnectionsImpl(ConcurrentHashMap<Integer, ConnectionHandler<StompMessage>> users)
    {
        messageId = new AtomicInteger(0);
        channels = new ConcurrentHashMap<>();
        handlersId = users;
    }

    @Override
    public boolean send(int connectionId, StompMessage msg)
    {
        if(handlersId.containsKey(connectionId))
        {
            handlersId.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, StompMessage msg)
    {

        if(!channels.containsKey(channel))
        {
            throw new IllegalArgumentException();
        }
        for (Subscription sub : channels.get(channel)) {
            StompMessage response = new StompMessage(StompMessage.stompCommand.MESSAGE, new HashMap<>(), msg.getBody());
            response.addHeader("subscription", String.valueOf(sub.subscriptionId));
            response.addHeader("message-id", String.valueOf(messageId.getAndIncrement()));
            response.addHeader("destination", channel);
            send(sub.connectionId, response);
        }
    }

    @Override
    public void disconnect(int connectionId)
    {
        if(handlersId.containsKey(connectionId))
        {
            try{
                handlersId.get(connectionId).close();
                for (String channel : channels.keySet()) {
                    for(Subscription sub : channels.get(channel))
                    {
                        if(sub.connectionId == connectionId)
                            channels.get(channel).remove(sub);
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

    public void subscribe(String channel, int connectionId, int subId)
    {
        if(!channels.containsKey(channel))
        {
            channels.put(channel, new ConcurrentLinkedQueue<>());
        }
        channels.get(channel).add(new Subscription(connectionId, subId));

    }

    public void unsubscribe(int connectionId, int subId)
    {
        Subscription remove = new Subscription(connectionId, subId);
        for (String channel : channels.keySet()) {
            if(channels.get(channel).contains(remove))
            {
                channels.get(channel).remove(remove);
            }
        }
    }

    public ConnectionHandler<StompMessage> getHandler(Integer connectionId)
    {
        return handlersId.get(connectionId);
    }

    public boolean isSubed(String channel, int connectionId)
    {
        if(!channels.containsKey(channel))
        {
            throw new IllegalArgumentException();
        }
        boolean isSub = false;
        for (Subscription sub : channels.get(channel)) {
            if(sub.connectionId == connectionId)
                isSub = true;
        }
        return isSub;
    }

    public boolean channelExist(String channel)
    {
        return channels.containsKey(channel);
    }
}
