/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.naggati

import scala.collection.mutable
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel._

/**
 * Netty doesn't appear to have a good set of fake objects yet, so this wraps a Codec in a fake
 * environment that collects emitted objects and returns them.
 */
class TestCodec(codec: Codec) {
  def this(firstStage: Stage, encoder: PartialFunction[Any, ChannelBuffer]) =
    this(new Codec(firstStage, encoder))

  val output = new mutable.ListBuffer[AnyRef]

  def log(e: MessageEvent) {
    e.getMessage match {
      case buffer: ChannelBuffer =>
        val bytes = new Array[Byte](buffer.readableBytes)
        buffer.readBytes(bytes)
        output += bytes
      case x =>
        output += x
    }
  }

  val upstreamTerminus = new SimpleChannelUpstreamHandler() {
    override def messageReceived(c: ChannelHandlerContext, e: MessageEvent) {
      log(e)
    }
  }
  val downstreamTerminus = new SimpleChannelDownstreamHandler() {
    override def writeRequested(c: ChannelHandlerContext, e: MessageEvent) {
      log(e)
    }
  }
  val pipeline = Channels.pipeline()
  pipeline.addLast("downstreamTerminus", downstreamTerminus)
  pipeline.addLast("decoder", codec)
  pipeline.addLast("upstreamTerminus", upstreamTerminus)

  val context = pipeline.getContext(codec)
  val sink = new AbstractChannelSink() {
    def eventSunk(pipeline: ChannelPipeline, event: ChannelEvent) { }
  }
  val channel = new AbstractChannel(null, null, pipeline, sink) {
    def getRemoteAddress() = null
    def getLocalAddress() = null
    def isConnected() = true
    def isBound() = true
    def getConfig() = new DefaultChannelConfig()
  }

  def apply(buffer: ChannelBuffer) = {
    output.clear()
    codec.messageReceived(context, new UpstreamMessageEvent(pipeline.getChannel, buffer, null))
    output.toList
  }

  def send(obj: Any) = {
    output.clear()
    codec.handleDownstream(context, new DownstreamMessageEvent(pipeline.getChannel, Channels.future(pipeline.getChannel), obj, null))
    output.toList
  }
}
