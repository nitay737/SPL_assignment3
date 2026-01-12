package bgu.spl.net.srv;

import java.io.IOException;
import java.nio.channels.SocketChannel;
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
import bgu.spl.net.impl.data.User;

public class ConnectionsImpl<T> implements Connections<T> {

    // (subscriptionId,IdConnect)
    private ConcurrentHashMap<Integer ,Integer> handlersId;
    // (subscriptionName,{subscriptionId,...} )
    private ConcurrentHashMap<String ,List<Integer>> channels;
    // (IdConnect, true/false)
    private ConcurrentHashMap<Integer ,Boolean> handlersConnected;
    // (IdConnect, handler)
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> idConnected;

    private boolean blocking;
    public ConnectionsImpl(List<Integer> users, boolean blocking)
    {
        this.blocking = blocking;
        channels = new ConcurrentHashMap<>();
        handlersId = new ConcurrentHashMap<>();
        handlersConnected = new ConcurrentHashMap<>();
        for (Integer connectionId : users) {
            handlersConnected.put(connectionId, true);
            idConnected.put(connectionId, createConnectionHandler());
        }
    }

    @Override
    public boolean send(int connectionId, T msg)
    {
        if(handlersId.contains(connectionId) && handlersConnected.get(handlersId.get(connectionId)))
        {
            idConnected.get(handlersId.get(connectionId)).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg)
    {
        if(!channels.contains(channel))
            return;
        for (Integer handlerId : channels.get(channel)) {
            send(handlerId, msg);
        }
    }

    @Override
    public void disconnect(int connectionId)
    {
        if(handlersId.contains(connectionId))
        {
            try{
                idConnected.get(handlersId.get(connectionId)).close();
                handlersConnected.put(handlersId.get(connectionId),false);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public void connect(Integer connectionsId)
    {

        ConnectionHandler<T> handler = createConnectionHandler();
        if(!handlersId.contains(handler))
            return;
        handlersConnected.put(handler,true);
    }

    public void subscribe(String channel,int connectionId, int subId)
    {
        if(channels.contains(channel))
        {
            channels.get(channel).add(subId);
            handlersId.put(subId, connectionId);
        }

    }

    public void unsubscribe(Integer id)
    {
        for (String channel : channels.keySet()) {
            if(channels.get(channel).contains(id))
                channels.get(channel).remove(id);
        }
    }

    public ConnectionHandler<T> getHandler(Integer id)
    {
        return idConnected.get(id);
    }

    private ConnectionHandler<T> createConnectionHandler()
    {
        if(blocking)
        {
            return new BlockingConnectionHandler<>(null, null, null)
        }
    }
}
