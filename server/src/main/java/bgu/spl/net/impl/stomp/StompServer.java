package bgu.spl.net.impl.stomp;

import com.google.common.base.Supplier;

import bgu.spl.net.impl.echo.EchoProtocol;
import bgu.spl.net.impl.echo.LineMessageEncoderDecoder;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Reactor;
import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        Server<StompMessage> server = null;
        if(args[1].equals("reactor")){
            server = new Reactor(3, Integer.parseInt(args[0]), () -> new StompMessagingProtocolImp(), () -> new MessageEncoderDecoderImpl());

        }else if(args[1].equals("tpc"))
        {
            server = new BaseServer(Integer.parseInt(args[0]),() -> new StompMessagingProtocolImp(), () -> new MessageEncoderDecoderImpl()) {
            @Override
            protected void execute(BlockingConnectionHandler<StompMessage>  handler) {
                new Thread(handler).start();
            }
        };
        }

        server.serve();
    }
}
