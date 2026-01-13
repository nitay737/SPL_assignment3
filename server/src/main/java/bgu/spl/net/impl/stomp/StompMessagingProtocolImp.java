package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.List;
import java.lang.String;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolImp implements StompMessagingProtocol<StompMessage> {

    private int ownerId;
    private ConnectionsImpl connections;
    private boolean shouldClose;
    private Database data;

    @Override
    public void start(int connectionId, Connections<StompMessage> connections)
    {
        ownerId = connectionId;
        this.connections = (ConnectionsImpl)connections;
        shouldClose = false;
    }

    public void process(StompMessage stomp)
    {
        switch (stomp.getCommand()) {
            case CONNECT:
                data = Database.getInstance();
                LoginStatus status = data.login(ownerId, stomp.getHeader("login"), stomp.getHeader("passcode"));
                StompMessage response;
                if(status == LoginStatus.WRONG_PASSWORD || status == LoginStatus.ALREADY_LOGGED_IN){
                    sendError(stomp, status.toString());
                }
                else
                {
                    response = new StompMessage("CONNECTED\n" +
                                                "version:1.2\n \n" +
                                                '\u0000');
                    connections.send(ownerId, response);
                }
                break;
            case SEND:
                try{
                    if(!connections.isSubed(stomp.getHeader("destination"), ownerId))
                        sendError(stomp, "you are not subscribed to this channel");
                    }catch(IllegalArgumentException e)
                {
                    sendError(stomp, "this channel does not exists");
                }
                connections.send(stomp.getHeader("destination"), stomp);
                break;
            case SUBSCRIBE:
                    connections.subscribe(stomp.getHeader("destination"), Integer.parseInt(stomp.getHeader("id")),ownerId);
                break;
            case UNSUBSCRIBE:
                connections.unsubscribe(ownerId, Integer.parseInt(stomp.getHeader("id")));
                break;
            case DISCONNECT:
                connections.disconnect(ownerId);
                sendReceipt(stomp.getHeader("receipt"));
                shouldClose = true;
            default:
                break;
        }

    }

    public boolean checkMessage(String message)
    {
        StompMessage stomp;
        try{
            stomp = new StompMessage(message);
        }catch(IllegalArgumentException e)
        {
            StompMessage response = new StompMessage(StompMessage.stompCommand.ERROR, new HashMap<>(), "the message:\n"+ "-----\n"+
                    message+"-----\n"+e.getMessage());
            response.addHeader("receipt-id", "");
            response.addHeader("message", e.getMessage());
            connections.send(ownerId,response);
            connections.disconnect(ownerId);
            shouldClose = true;
            return false;
        }
        return true;
    }

    public void sendError(StompMessage stomp ,String errorMessage)
    {
        StompMessage error = new StompMessage(StompMessage.stompCommand.ERROR, new HashMap<>(), "the message:\n"+ "-----\n"+
        stomp.getMessage()+"-----\n");
        error.addHeader("receipt-id", stomp.getHeader("receipt"));
        error.addHeader("message", "you are not subscribed to this channel");
        connections.send(ownerId, error);
        connections.disconnect(ownerId);
        shouldClose = true;
    }

    public void sendReceipt(String receiptId)
    {
        StompMessage send = new StompMessage("RECEIPT\n" +
                        "receipt-id :"+receiptId+"\n" +
                        '\u0000');
        connections.send(ownerId, send);
    }
    public boolean shouldTerminate(){
        return shouldClose;
    }
}
