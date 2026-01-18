package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.StompMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

// public class TPC {

//     private final int port;
//     private final Supplier<StompMessagingProtocol<StompMessage>> protocolFactory;
//     private final Supplier<MessageEncoderDecoder<StompMessage>> encdecFactory;
//     private ServerSocket sock;

//     public
//     @Override
//     public void serve() {

//         try (ServerSocket serverSock = new ServerSocket(port)) {
// 			System.out.println("Server started");

//             this.sock = serverSock; //just to be able to close

//             while (!Thread.currentThread().isInterrupted()) {

//                 Socket clientSock = serverSock.accept();

//                 BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(
//                         clientSock,
//                         encdecFactory.get(),
//                         protocolFactory.get());

//                 execute(handler);
//             }
//         } catch (IOException ex) {
//         }

//         System.out.println("server closed!!!");
//     }

//     @Override
//     public void close() throws IOException {
// 		if (sock != null)
// 			sock.close();
//     }

//     @Override
//     protected void execute(BlockingConnectionHandler<StompMessage>  handler)
//     {

//     }

// }
