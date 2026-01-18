package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;

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
    private final LinkedList<String> ConnectHeaders = new LinkedList<>(Arrays.asList("accept-version","host","login","passcode"));
    private final LinkedList<String> SendHeaders = new LinkedList<>(Arrays.asList("destination"));
    private final LinkedList<String> SubscribeHeaders = new LinkedList<>(Arrays.asList("destination","id"));
    private final LinkedList<String> UnsubscribeHeaders = new LinkedList<>(Arrays.asList("id"));
    private final LinkedList<String> DisconnectHeaders = new LinkedList<>(Arrays.asList("receipt"));

    public StompMessage(String message)
    {
        header = new HashMap<>();
        parse(message);
        System.out.println("after parse:\n"+getMessage());
    }

    public StompMessage(stompCommand command,HashMap<String,String> header, String body)
    {
        this.command = command;
        this.body = body;
        this.header = header;
    }

    private void parse(String message)
    {
        System.out.println("before parse:\n"+message);
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
        System.out.println("start parsing");
        while (i < subMessage.length && !subMessage[i].isEmpty()) {
            System.out.println(subMessage[i]);
            String[] subHeader = subMessage[i].split(":", 2);
            if (subHeader.length < 2) {
                throw new IllegalArgumentException("Header missing value");
            }

            String key = subHeader[0].trim();
            String value = subHeader[1].trim();
            if (headers.contains(key) || key.equals("receipt")) {
                header.put(key, value);
                if(headers.contains(key))
                    headers.remove(key);
            } else {
                throw new IllegalArgumentException("not a legal header: " + key);
            }
            i++;
        }
        if(!headers.isEmpty())
            throw new IllegalArgumentException("not a legal header");

        while (i<subMessage.length && subMessage[i] != "\u0000") {
            body += subMessage[i] + "\n";
            i++;
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
        if(header.containsKey(h) || value == null)
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
