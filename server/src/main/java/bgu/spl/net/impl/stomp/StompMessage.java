package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class StompMessage {
    public enum stompCommand{
        CONNECT,
        SEND,
        SUBSCRIBE,
        UNSUBSCRIBE,
        DISCONNECT,
        ERROR,
        CONNECTED,
        MESSAGE,
        RECEIPT
    }

    private stompCommand command;
    private HashMap<String,String> header;
    private String body = "";
    private final LinkedList<String> ConnectHeaders = new LinkedList<>(List.of("accept - version","host","login","passcode"));
    private final LinkedList<String> SendHeaders = new LinkedList<>(List.of("destination"));
    private final LinkedList<String> SubscribeHeaders = new LinkedList<>(List.of("destination","id"));
    private final LinkedList<String> UnsubscribeHeaders = new LinkedList<>(List.of("id"));
    private final LinkedList<String> DisconnectHeaders = new LinkedList<>(List.of("receipt"));

    public StompMessage(String message)
    {
        parse(message);
    }

    public StompMessage(stompCommand command,HashMap<String,String> header, String body)
    {
        this.command = command;
        this.body = body;
        this.header = header;
    }

    private void parse(String message)
    {
        if(message.length() == 0)
            throw new IllegalArgumentException();
        String[] subMessage = message.split("\n");
        List<String> headers;
        switch (subMessage[0]) {
            case "CONNECT":
                command = stompCommand.CONNECT;
                headers = ConnectHeaders;
                break;
            case "SEND":
                command = stompCommand.SEND;
                headers = SendHeaders;
                break;
            case "SUBSCRIBE":
                command = stompCommand.SUBSCRIBE;
                headers = SubscribeHeaders;
                break;
            case "UNSUBSCRIBE":
                command = stompCommand.UNSUBSCRIBE;
                headers = UnsubscribeHeaders;
                break;
            case "DISCONNECT":
                command = stompCommand.DISCONNECT;
                headers = DisconnectHeaders;
                break;
            default:
                throw new IllegalArgumentException("not a legal command");
        }
        int i = 1;
        while( i<subMessage.length && subMessage[i] != "")
        {
            String[] subHeader = subMessage[i].split(":");
            if(headers.contains(subHeader[0]))
            {
                header.put(subHeader[0], subHeader[1]);
                headers.remove(subHeader[0]);
            }
            else
                throw new IllegalArgumentException("not a legal header");
            i++;
        }

        while (i<subMessage.length && subMessage[i] != "^ @") {
            body += subMessage[i] + "\n";
        }
    }

    public stompCommand getCommand()
    {
        return command;
    }

    public String getHeader(String h)
    {
        if(!header.containsKey(h))
            return "";
        return header.get(h);
    }

    public String getBody()
    {
        return body;
    }

    public void addHeader(String h, String value)
    {
        if(!header.containsKey(h) || value == null)
            return;
        header.put(h, value);
    }

    public String getMessage()
    {
        String message = "";
        message += command.toString()+"\n";
        for (String h : header.keySet()) {
            message += h+":"+header.get(h)+"\n";
        }
        message+= "\n" ;
        message+= body + "\n";
        return message;
    }

}
