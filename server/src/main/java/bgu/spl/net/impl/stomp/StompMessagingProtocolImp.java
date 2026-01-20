package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.List;
import java.lang.String;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.DatabaseService;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolImp implements StompMessagingProtocol<StompMessage> {

    private int ownerId;
    private ConnectionsImpl connections;
    private boolean shouldClose;
    private DatabaseService db;

    @Override
    public void start(int connectionId, Connections<StompMessage> connections)
    {
        ownerId = connectionId;
        this.connections = (ConnectionsImpl)connections;
        shouldClose = false;
        db = new DatabaseService();
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
                String loginUsername = stomp.getHeader("login");
                String loginPasscode = stomp.getHeader("passcode");
                String savedPassword = db.getPassword(loginUsername);
                if (savedPassword != null) {
                    if (!savedPassword.equals(loginPasscode)) {
                        sendError(stomp, "Wrong password");
                    }
                    else {
                        db.addLogin(stomp.getHeader("login"));
                        connections.login(ownerId, stomp.getHeader("login"));
                        response = new StompMessage(StompMessage.stompCommand.CONNECTED,new HashMap<>(),"");
                        response.addHeader("version", "1.2");
                        connections.send(ownerId, response);
                    }
                } else {
                    db.registerUser(loginUsername, loginPasscode);
                    db.addLogin(stomp.getHeader("login"));
                    connections.login(ownerId, stomp.getHeader("login"));
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
                else {
                    connections.send(stomp.getHeader("destination"), stomp);
                    String gameName = stomp.getHeader("destination");
                    db.addFileReport(connections.getUserName(ownerId), gameName);
                }
                break;
            case SUBSCRIBE:
                    connections.subscribe(stomp.getHeader("destination"), Integer.parseInt(stomp.getHeader("id")), ownerId);
                break;
            case UNSUBSCRIBE:
                connections.unsubscribe(ownerId, Integer.parseInt(stomp.getHeader("id")));
                break;
            case DISCONNECT:
                db.addLogout(connections.getUserName(ownerId));
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
        StompMessage error = new StompMessage(StompMessage.stompCommand.ERROR, new HashMap<>(), "the message:\n"+ "-----\n"+
        stomp.getMessage()+"-----\n");
        error.addHeader("receipt-id", stomp.getHeader("receipt"));
        error.addHeader("message", errorMessage);
        connections.send(ownerId, error);
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
