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

    @Override
    public void start(int connectionId, Connections<StompMessage> connections)
    {
        ownerId = connectionId;
        this.connections = (ConnectionsImpl)connections;
        shouldClose = false;
    }

    public void process(StompMessage stomp)
    {
        //Send receipt if the header exists
        String receiptId = stomp.getHeader("receipt");
        if(receiptId != "")
            sendReceipt(receiptId);
        switch (stomp.getCommand()) {
            case CONNECT:
                //Trying logging in
                StompMessage response;
                
                if(!connections.login(stomp.getHeader("login"), stomp.getHeader("passcode"))){
                    //Login failed
                    sendError(stomp, "Wrong password");
                }
                else
                {
                    //Login succeeded
                    response = new StompMessage(StompMessage.stompCommand.CONNECTED,new HashMap<>(),"");
                    response.addHeader("version", "1.2");
                    connections.send(ownerId, response);
                }
                break;
            case SEND:
                //Checks if the channel exists
                if(!connections.channelExist(stomp.getHeader("destination")))
                    sendError(stomp, "this channel does not exists");
                //Checks if the user is subed
                else if(!connections.isSubed(stomp.getHeader("destination"), ownerId))
                    sendError(stomp, "you are not subscribed to this channel");
                connections.send(stomp.getHeader("destination"), stomp);
                break;
            case SUBSCRIBE:
                    connections.subscribe(stomp.getHeader("destination"), Integer.parseInt(stomp.getHeader("id")), ownerId);
                break;
            case UNSUBSCRIBE:
                connections.unsubscribe(ownerId, Integer.parseInt(stomp.getHeader("id")));
                break;
            case DISCONNECT:
                connections.disconnect(ownerId);
                shouldClose = true;
                break;
            case ERROR:
                connections.send(ownerId, stomp);
                break;
            case CONNECTED:
                connections.send(ownerId, stomp);
                break;
            case MESSAGE:
                connections.send(ownerId, stomp);
                break;
            case RECEIPT:
                connections.send(ownerId, stomp);
                break;
            default:
                break;
        }


    }

    //Convert the string into a StompMessage and checks if the message is valid otherwise sends an error
    public static StompMessage convertMessage(String message)
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
            return response;
        }
        return stomp;
    }

    private void sendError(StompMessage stomp ,String errorMessage)
    {
        System.out.println(errorMessage);
        StompMessage error = new StompMessage(StompMessage.stompCommand.ERROR, new HashMap<>(), "the message:\n"+ "-----\n"+
        stomp.getMessage()+"-----\n");
        error.addHeader("receipt-id", stomp.getHeader("receipt"));
        error.addHeader("message", errorMessage);
        System.out.println(connections.send(ownerId, error));
        connections.disconnect(ownerId);
        shouldClose = true;
    }

    private void sendReceipt(String receiptId)
    {
        StompMessage send = new StompMessage(StompMessage.stompCommand.RECEIPT,new HashMap<>(),"");
        send.addHeader("receipt-id", receiptId);
        connections.send(ownerId, send);
    }

    public boolean shouldTerminate(){
        return shouldClose;
    }
}
