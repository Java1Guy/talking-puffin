package org.talkingpuffin.twitter

import scala.xml.{Node, XML}
import swing.Publisher
import java.net.{URL, HttpURLConnection, URLEncoder}
import java.io.{DataOutputStream, BufferedReader, InputStreamReader}
import org.apache.commons.codec.binary.Base64
import org.apache.log4j.Logger
import swing.event.Event

/**
* Handles HTTP requests.
*/
class Http(user: Option[String], password: Option[String]) extends Publisher {
  private val log = Logger.getLogger("Http")

  /** the encoded authentication string.  This is null if user or password is null. */
  private val encoding = if(user.isDefined && password.isDefined) 
    Some(new String(Base64.encodeBase64((user.get + ":" + password.get).getBytes())))
  else 
    None
  
  /**
  * Fetch an XML document from the given URL
  */
  def doGet(url: URL): Node = {
    val conn = url.openConnection.asInstanceOf[HttpURLConnection]
    logAction("GET", url)
    setAuth(conn)
    getXML(conn)
  }

  def doDelete(url: URL) = {
    val conn = url.openConnection.asInstanceOf[HttpURLConnection]
    logAction("DELETE", url)
    setAuth(conn)
    conn.setRequestMethod("DELETE")
    getXML(conn)
  }
  /*
  * post to the specified URL with the given params, return an XML node built from the response
  * @param url the URL to post to
  * @param params a List of String tuples, the first entry being the param, the second being the value
  */
  def doPost(url: URL, params: List[(String,String)]): Node = {
    val conn = url.openConnection.asInstanceOf[HttpURLConnection]
    logAction("POST", url)
    setAuth(conn)
    conn.setDoInput(true)
    conn.setRequestMethod("POST")
    val content = buildParams(params)

    if(content != null){
      conn.setUseCaches(false)
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setDoOutput(true)
      val printout = new DataOutputStream(conn.getOutputStream())
      if(content != null){
        printout.writeBytes (content)
        printout.flush ()
        printout.close ()
      }
    }
    getXML(conn)
  }
  
  private def logAction(action: String, url: URL) = log.debug(user.getOrElse("") + " " + action + " " + url)

  private def setAuth(conn: HttpURLConnection) {
    if (encoding.isDefined) {
      conn.setRequestProperty ("Authorization", "Basic " + encoding.get);
    }
  }

  /*
  * take an opened (and posted to, if applicable) connection, read the response code, and take appropriate action.
  * If the response code is 200, return an XML node built on the response.
  * If the response code is anything else, throw a new TwitterException based on the code. 
  * This path also reads from conn.getErrorStream() to populate the twitterMessage field
  * in the thrown exception.
  */
  private def getXML(conn: HttpURLConnection): Node = {
    val response = conn.getResponseCode()
    response match {
      case 200 => {
        publishRateLimitInfo(conn)
        XML.load(conn.getInputStream())
      }
      case _ => throw TwitterException({
          var errMsg = ""
          val reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))
          var line = reader.readLine()
          while(line != null){
            errMsg += line
            line = reader.readLine()
          }
          errMsg  
        },response)
    }
  }
  
  private def publishRateLimitInfo(conn: HttpURLConnection) {
    val hRemaining = conn.getHeaderField("X-RateLimit-Remaining")
    val hLimit = conn.getHeaderField("X-RateLimit-Limit")
    val hReset = conn.getHeaderField("X-RateLimit-Reset")

    if (hRemaining != null && hLimit != null && hReset != null) {
      publish(RateLimitStatusEvent(new TwitterRateLimitStatus {
        remainingHits      = hRemaining.toInt
        hourlyLimit        = hLimit.toInt
        resetTimeInSeconds = hReset.toInt
      }))
    }
  }

  private def buildParams(params: List[(String,String)]): String = {
    params match {
      case Nil => null
      case (param,value) :: rest => {
        val end = buildParams(rest)
        param + "=" + URLEncoder.encode(value, "UTF-8") + (if (end == null) "" else "&" + end)
      }
    }
  }
}

case class RateLimitStatusEvent(status: TwitterRateLimitStatus) extends Event