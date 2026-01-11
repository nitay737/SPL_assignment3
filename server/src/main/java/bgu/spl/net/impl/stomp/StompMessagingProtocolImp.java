package bgu.spl.net.impl.stomp;

import java.util.List;
import java.lang.String;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolImp implements StompMessagingProtocol<String> {

    private int ownerId;
    private ConnectionsImpl<String> connections;

    @Override
    public void start(int connectionId, Connections<String> connections)
    {
        ownerId = connectionId;
        this.connections = (ConnectionsImpl)connections;
    }

    public void process(String message)
    {
        String stompCommand = "";
        int i =0;
        while (message.charAt(i) != '\n') {
            stompCommand+=message.charAt(i);
            i++;
        }

    }
    
}
