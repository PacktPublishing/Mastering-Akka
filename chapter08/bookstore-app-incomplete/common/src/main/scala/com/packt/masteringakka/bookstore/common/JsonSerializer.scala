package com.packt.masteringakka.bookstore.common

import akka.serialization.SerializerWithStringManifest
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import org.json4s.ext.EnumNameSerializer


/**
 * Generic json based serializer that will use the FQ class name as the manifest so that
 * it can properly recreate the object
 */
class JsonSerializer extends SerializerWithStringManifest{
  implicit val formats = Serialization.formats(NoTypeHints)
  
  def toBinary(o:AnyRef):Array[Byte] = {
    val json = write(o)
    json.getBytes()
  }
  
  def fromBinary(bytes:Array[Byte], manifest:String):AnyRef = {
    val m = Manifest.classType[AnyRef](Class.forName(manifest))
    val json = new String(bytes, "utf8")
    read[AnyRef](json)(formats, m)
  }  
  
  def identifier:Int = 999
  def manifest(o:AnyRef):String = o.getClass.getName
}