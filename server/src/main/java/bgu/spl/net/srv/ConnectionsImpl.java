package bgu.spl.net.srv;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Flow.Subscriber;

import bgu.spl.net.api.StompMessagingProtocol;

public class ConnectionsImpl<T> implements Connections<T> {
    Integer id;
    private ConcurrentHashMap<Integer,ConnectionHandler<T>> handlersId;
    private ConcurrentHashMap<Integer, Set<String>> handlersChannels;

    public ConnectionsImpl(List<ConnectionHandler<T>> handlers)
    {
        id = 0;
        handlersChannels = new ConcurrentHashMap<>();
        handlersId = new ConcurrentHashMap<>();
        for (ConnectionHandler<T> connectionHandler : handlers) {
            handlersId.put(id, connectionHandler);
            handlersChannels.put(id, new HashSet<String>());
            id++;
        }
    }

    @Override
    public boolean send(int connectionId, T msg)
    {
        if(!handlersId.contains(connectionId))
            return false;
        handlersId.get(connectionId).send(msg);;
        return true;
    }

    @Override
    public void send(String channel, T msg)
    {
        for (Integer handlerId : handlersChannels.keySet()) {
            if(handlersChannels.get(handlerId).contains(channel))
                handlersId.get(handlerId).send(msg);
        }
    }

    @Override
    public void disconnect(int connectionId)
    {
        if(handlersId.contains(connectionId))
        {
            try{
                handlersId.get(connectionId).close();
                handlersId.remove(connectionId);
                handlersChannels.remove(connectionId);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public void connect(ConnectionHandler<T> handler)
    {
        if(!handlersId.contains(handler))
            return;
        handlersId.put(id, handler);
        handlersChannels.put(id, new HashSet<String>());
        id++;
    }

    public void subscribe(String channel,int connectionId)
    {
        if(handlersChannels.contains(connectionId))
            handlersChannels.get(connectionId).add(channel);
    }
}
