package org

import java.util

import javafx.scene.control.Alert
import org.apache.flink.cep.functions.PatternProcessFunction
import org.apache.flink.cep.scala.CEP
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.util.Collector
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.fiware.cosmos.orion.flink.connector._
import java.text.SimpleDateFormat
import java.util.Calendar

import scala.collection.immutable



/**
  * Skeleton for a Flink Streaming Job.
  *
  * For a tutorial how to write a Flink streaming application, check the
  * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
  *
  * To package your application into a JAR file for execution, run
  * 'mvn clean package' on the command line.
  *
  * If you change the name of the main class (with the public static void main(String[] args))
  * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
  */
object StreamingJob {

  final val URL_CB = "http://orion:1026/v2/entities/"
  final val CONTENT_TYPE = ContentType.JSON
  final val METHOD = HTTPMethod.POST


  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Create Orion Source. Receive notifications on port 9001
    val eventStream = env.addSource(new OrionSource(9001))

    // Process event stream
    val processedDataStream: DataStream[DataEvent]= eventStream
      .flatMap(event => event.entities)
      .map(entity => {
        val parameter = "temperature"
        val value = entity.attrs(parameter).value.asInstanceOf[Number].floatValue()
        val dateObserved = entity.attrs("dateObserved").value.asInstanceOf[String].toString()
//        val message = entity.attrs("counter").value.asInstanceOf[String].toString()
        new DataEvent(
          entity.id,
          parameter,
          value,
          dateObserved
//          message
        )
      })
    //      .keyBy("id")
    //      .timeWindow(Time.seconds(5), Time.seconds(2))
    //      .min("temperature")
    //      .map(dataNode => {
    //        var id = dataNode .id
    //        id = id.replace("Module","Alert")
    //        val url = URL_CB + id + "/attrs"
    ////        OrionSinkObject(tempNode.toString, url, CONTENT_TYPE, METHOD)
    //      })

    val pattern = Pattern.begin[DataEvent]("start").where(
      _.data >= 40
    )

    val patternStream = CEP.pattern(processedDataStream, pattern)

    val outputTag = OutputTag[String]("output")

    val alerts: DataStream[OrionSinkObject] = patternStream.process(
      new PatternProcessFunction[DataEvent, OrionSinkObject]() {
        override def processMatch(
                                   map: util.Map[String, util.List[DataEvent]],
                                   context: PatternProcessFunction.Context,
                                   collector: Collector[OrionSinkObject]): Unit = {




          val alert = map.get("start").get(0).alert()

          val alertid =  map.get("start").get(0).id.replace("DataObserved","Alert")
          println( URL_CB+alertid+"/attrs")
          collector.collect(OrionSinkObject(alert, URL_CB+alertid+"/attrs", CONTENT_TYPE, METHOD))
        }
      })

    alerts.print()
    OrionSink.addSink(alerts)

    //    processedDataStream.print()
    // Add Orion Sink
    //    OrionSink.addSink( processedDataStream )

    // print the results with a single thread, rather than in parallel
    //    processedDataStream.map(orionSinkObject => orionSinkObject.content).print().setParallelism(1)
    env.execute("FIWARE Cosmos Example")

    //    TODO
    /*
    *  Send alerts to Sink
    *  Model should be Alert { id : "Alert"+ModuleID}
    *  GetId from the source
    *  Add flat map function
    *  then send alert to sink with the correct id
    * */
  }

  case class DataEvent(id: String, parameter: String, data: Float, dateObserved: String) extends  Serializable {
    def alert() : String = {
      return "{\"module\": { \"value\":\""+id+"\",\"type\": \"Text\"}, " +
        "\"dateObserved\": { \"value\":\""+dateObserved+"\",\"type\": \"Text\"}," +
        "\"temperature\": { \"value\":\""+data+"\",\"type\": \"Float\"}}"
    }

    def getAlert :String = { "{\"module\": { \"value\":\""+id+"\",\"type\": \"Text\"}, " +
      "\"dateObserved\": { \"value\":\""+dateObserved+"\",\"type\": \"Text\"}," +
      "\"condition\": { \"value\":\"Condition example\",\"type\": \"Text\"}," +
      "\"temperature\": { \"value\":\""+data+"\",\"type\": \"Float\"}}" }
  }
}


/*
*
*
* while true; do     temp=$(shuf -i 18-53 -n 1);     number=$(shuf -i 1-3113 -n 1);      curl -v -s -S -X POST http://localhost:9001     --header 'Content-Type: application/json; charset=utf-8'     --header 'Accept: application/json'     --header 'User-Agent: orion/0.10.0'     --header "Fiware-Service: demo"     --header "Fiware-ServicePath: /test"     -d  '{
         "data": [
             {
                 "id": "R1","type": "Node",
                 "co": {"type": "Float","value": 0,"metadata": {}},
                 "co2": {"type": "Float","value": 0,"metadata": {}},
                 "humidity": {"type": "Float","value": 40,"metadata": {}},
                 "pressure": {"type": "Float","value": '$number',"metadata": {}},
                 "temperature": {"type": "Float","value": '$temp',"metadata": {}},
                 "wind_speed": {"type": "Float","value": 1.06,"metadata": {}}
             }
         ],
         "subscriptionId": "57458eb60962ef754e7c0998"
     }';      sleep 1; done


    curl -iX POST \
  --url 'http://localhost:1026/v2/entities' \
  --header 'Content-Type: application/json' \
  --data ' {
      "id":"Alert:MOD1", "type":"Alert",
      "name":{"type":"Text", "value":"Aleeeeeert"},
      "condition":{"type":"Text", "value": "This is a test alert"}
}'

curl -iX PATCH \
  --url 'http://localhost:1026/v2/entities/Alert:MOD1/attrs' \
  --header 'Content-Type: application/json' \
  --data ' {
      "condition":{"type":"Text", "value": "ALERT"}
}'
*/

