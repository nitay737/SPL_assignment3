package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.StompMessage;
import bgu.spl.net.impl.stomp.StompMessagingProtocolImp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public abstract class BaseServer implements Server<StompMessage> {

    private final int port;
    private final Supplier<StompMessagingProtocolImp> protocolFactory;
    private final Supplier<MessageEncoderDecoder<StompMessage>> encdecFactory;
    private ServerSocket sock;
    private ConnectionsImpl connections;
    private AtomicInteger connectionId;

    public BaseServer(
            int port,
            Supplier<StompMessagingProtocolImp> protocolFactory,
            Supplier<MessageEncoderDecoder<StompMessage>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
        connections = new ConnectionsImpl(new ConcurrentHashMap<>());
        connectionId = new AtomicInteger(0);
    }

    @Override
    public void serve() {

        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock; //just to be able to close

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();
                StompMessagingProtocolImp protocol = protocolFactory.get();
                BlockingConnectionHandler<StompMessage> handler = new BlockingConnectionHandler<>(
                        clientSock,
                        encdecFactory.get(),
                        protocol);
                int ownId = connectionId.getAndIncrement();
                connections.connect(ownId,handler);
                protocol.start(ownId,connections);
                execute(handler);
            }
        } catch (IOException ex) {
        }

        System.out.println("server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<StompMessage>  handler);

}
