/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.netty

import org.jboss.netty.util._
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.ssl._
import org.jboss.netty.bootstrap._
import cats._
import cats.data._
import cats.implicits._
import java.net.InetSocketAddress
import zenith.{Async, Logger}
import java.util.concurrent.{ExecutorService, Executors}
import scala.util.{Success, Failure}
import NettyUtils._
import zenith.netty._
import zenith.Logger.Level._
import org.jboss.netty.handler.ssl.util.InsecureTrustManagerFactory

private [this] final case class NettySsl (host: String, port: Int, sslCtx: SslContext)

private [this] final class NettyClientHandler[Z[_]](promise: Async.Promise[Z, zenith.HttpResponse])
  extends SimpleChannelUpstreamHandler {

  override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent): Unit = try {
    val response = e.getMessage.asInstanceOf[HttpResponse]
    if (response.isChunked) ???
    val zResponse = toZenith (response)
    promise.success (zResponse)
  } catch { case t: Throwable => promise.failure (t) }

  override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    e.getChannel.close ()
    promise.failure (e.getCause)
  }
}

private [this] final class NettyClientPipelineFactory[Z[_]: Async](promise: Async.Promise[Z, zenith.HttpResponse], ssl: NettySsl)
  extends ChannelPipelineFactory
{
  override def getPipeline: ChannelPipeline = {
    // Create a default pipeline implementation.
    val pipeline = Channels.pipeline

    pipeline.addLast ("ssl", ssl.sslCtx.newHandler (ssl.host, ssl.port))
    pipeline.addLast ("codec", new HttpClientCodec ())
    pipeline.addLast ("inflater", new HttpContentDecompressor ())
    pipeline.addLast ("aggregator", new HttpChunkAggregator (1048576))
    pipeline.addLast ("handler", new NettyClientHandler (promise))
    pipeline
  }
}

final class NettyHttpClientProvider[Z[_]: Monad: Async: Logger] extends zenith.HttpClientProvider[Z]
{
  import java.util.concurrent.{ExecutorService, Executors}
  import NettyUtils._

  private var client: Option[zenith.client.HttpClient[Z]] = None
  private var bootstrap: ClientBootstrap = null
  private var boss: ExecutorService = null
  private var workers: ExecutorService = null

  def create (config: zenith.client.HttpClientConfig): zenith.client.HttpClient[Z] = {
    client match {
      case Some (c) => c
      case None =>
        boss = Executors.newCachedThreadPool ()
        workers = Executors.newCachedThreadPool ()
        bootstrap = new ClientBootstrap (new NioClientSocketChannelFactory (boss, workers))
        val cli = zenith.client.HttpClient[Z](send)(config)
        client = Some (cli); cli
    }
  }

  def getClient (): Option[zenith.client.HttpClient[Z]] = client

  def destroy (): Unit = {
    if (client.isDefined) {
      client = None
      bootstrap.shutdown ()
      workers.shutdown ()
      boss.shutdown ()
    }
  }

  private def send (request: zenith.HttpRequest): Z[zenith.HttpResponse] = try {
    // I solemnly swear...
    val p = Async[Z].promise[zenith.HttpResponse]()
    // Just allow all ssl.
    val ssl = NettySsl (request.host, request.hostPort, SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE))
    // Set up the event pipeline factory.
    bootstrap.setPipelineFactory (new NettyClientPipelineFactory (p, ssl))
    // Start the connection attempt.
    val channelFuture = bootstrap.connect (new InetSocketAddress (request.host, request.hostPort))
    // Wait until the connection attempt succeeds or fails.
    val channel = channelFuture.sync ().getChannel
    // Prepare the HTTP request.
    val nettyRequest = toNetty (request)
    // Send the HTTP request.
    channel.write (nettyRequest)
    // Wait for the server to close the connection.
    channel.getCloseFuture.sync ()
    // An then...
    p.future
  } catch {
    case ex: Throwable => for {
      _ <- zenith.Logger[Z].log (LOG_CH, ERROR, s"Crap, something is wrong: ${ex.getMessage}")
      _ <- zenith.Logger[Z].log (LOG_CH, ERROR, ex.getStackTrace.toString)
    } yield zenith.HttpResponse.createPlain (500, "FUCK")
  }
}
