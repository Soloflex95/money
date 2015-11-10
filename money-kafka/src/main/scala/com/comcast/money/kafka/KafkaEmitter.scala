package com.comcast.money.kafka

import java.util.Properties

import akka.actor.Actor
import com.comcast.money.wire.AvroConversions
import com.comcast.money.core
import com.comcast.money.internal.EmitterProtocol.EmitSpan
import com.typesafe.config.Config
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}

// We use the producer maker so that we can mock this out
trait ProducerMaker {
  def makeProducer(conf: Config): Producer[Array[Byte], Array[Byte]]
}

trait ConfigDrivenProducerMaker extends ProducerMaker {

  def makeProducer(conf: Config): Producer[Array[Byte], Array[Byte]] = {

    val props = new Properties()

    props.put("compression.codec", conf.getString("compression.codec"))
    props.put("producer.type", conf.getString("producer.type"))
    props.put("batch.num.messages", conf.getString("batch.num.messages"))
    props.put("message.send.max.retries", conf.getString("message.send.max.retries"))
    props.put("metadata.broker.list", conf.getString("metadata.broker.list"))

    new Producer[Array[Byte], Array[Byte]](new ProducerConfig(props))
  }
}

// The config constructor arg has to be a `val` in order to comply with the `Configurable` trait
class KafkaEmitter(conf: Config) extends Actor with ConfigDrivenProducerMaker {

  import AvroConversions._

  private val topic = conf.getString("topic")
  private var producer: Producer[Array[Byte], Array[Byte]] = _

  override def preStart() = {
    producer = makeProducer(conf)
  }

  def receive = {
    case EmitSpan(t: core.Span) =>
      producer.send(new KeyedMessage(topic, t.convertTo[Array[Byte]]))
  }
}
