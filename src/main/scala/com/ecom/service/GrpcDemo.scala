package com.ecom.service
import cats.effect._
import com.ecom.protos.orders._
import fs2.grpc.syntax.all._
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}


  //val item = Item("Iphone",1,2000.0)
  class OrderService extends OrderFs2Grpc[IO,Metadata]{
    override def sendOrderStream(request: fs2.Stream[IO, OrderRequest], ctx: Metadata): fs2.Stream[IO, OrderReply] = {
      request.map{ orderReq=>
        OrderReply(
          orderReq.orderid,
          orderReq.items,
          orderReq.items.map(_.amount).sum
        )
      }
    }
  }




  object Server  extends IOApp.Simple {

    //Resource[IO,A]
    /*

  final def bindServiceResource[F[_]: Async](
      serviceImpl: Service[F, Metadata]
  ): Resource[F, ServerServiceDefinition] =
    bindServiceResource[F](serviceImpl, ServerOptions.default)
    Resource[IO, ServerServiceDefinition]
     */
    val grpcServer: Resource[IO, Server] =
    OrderFs2Grpc
       .bindServiceResource(new OrderService)
       .flatMap{
        service=>
             NettyServerBuilder
            .forPort(9999)
            .addService(service)
            .resource[IO]
      }

    override def run: IO[Unit] = Server.grpcServer.use{
      server=>
        IO.println("Order fulfillment Microservice Started!!!") *>
          IO(server.start()) *>
          IO.never

    }
  }



  object Client{
    val resource: Resource[IO, OrderFs2Grpc[IO, Metadata]] = NettyChannelBuilder
      .forAddress("127.0.0.1",9999)
      .usePlaintext()
      .resource[IO]
      .flatMap{
        channel => OrderFs2Grpc.stubResource[IO](channel)
      }
  }
  /*
  trait Service {someMethod()}
  Server => will provide instance of trait ,open a port to interact
  client =>  STUB instance of that trait whose implementation will interact remotely with server
  from my side i can use the stub instance
  so that i can call some method remotely this is called RPC framework in general
   */


  object GrpcDemo extends IOApp.Simple {

  //IO.never is kind of while loop starting Server for always
  override def run: IO[Unit] = Client.resource.use{
    orderService=>
     val rpcCallToRemoteServer =
       orderService
         .sendOrderStream(fs2.Stream.eval(IO(
          OrderRequest(
          0,
          List(Item("iphone",2,2000.0))
                      )
        )),new Metadata())
         .compile
         .toList
         .flatMap(replies=> IO.println(replies))
      rpcCallToRemoteServer
  }

  //Server.grpcServer.use(_=> IO.never)
}
