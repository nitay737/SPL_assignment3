package bgu.spl.net.impl.stomp;

import java.util.List;
import java.lang.String;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.NonBlockingConnectionHandler;

public class StompMessagingProtocolImp implements StompMessagingProtocol<String> {

    private int ownerId;
    private ConnectionsImpl<String> connections;
    private boolean shouldClose;

    @Override
    public void start(int connectionId, Connections<String> connections)
    {
        ownerId = connectionId;
        this.connections = (ConnectionsImpl)connections;
        shouldClose = true;
    }

    public void process(String message)
    {
        StompMessage stomp = new StompMessage(message);
        switch (stomp.getCommand()) {
            case CONNECT:
                connections.connect(new NonBlockingConnectionHandler<>(null, new StompMessagingProtocolImp(), null, null));
                break;
            case SEND:
                connections.send(stomp.getHeader("destination"), stomp.getBody());
                break;
            case SUBSCRIBE:
                connections.subscribe(stomp.getHeader("destination"), Integer.parseInt(stomp.getHeader("id")) , connections.getHandler(Integer.parseInt(stomp.getHeader("id"))));
            case UNSUBSCRIBE:
                connections.unsubscribe(Integer.parseInt(stomp.getHeader("id")));
                break;
            case DISCONNECT:
                connections.disconnect(Integer.parseInt(stomp.getHeader("id")));
                shouldClose = false;
            default:
                break;
        }

    }

    public boolean shouldTerminate(){
        return shouldClose;
    }

    
}
