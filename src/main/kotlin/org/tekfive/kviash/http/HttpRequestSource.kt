package org.tekfive.kviash.http

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

interface HttpRequestSource {
    /**
     * The name of the scheme used to make this request, for example, http or https.
     */
    val urlProtocol: String

    /**
     * The name and version of the protocol the request uses in the form protocol/majorVersion.minorVersion, for example, HTTP/1.1.
     */
    val httpProtocol: String

    /**
     * Returns the port number to which the request was sent. It is the value of the part after ":" in the Host header value, if any, or the server port where the client connection was accepted on.
     */
    val port: Int

    /**
     * The name of the HTTP method with which this request was made, for example, GET, POST, or PUT.
     */
    val method: String

    /**
     * The full path of the request minus the query string.
     */
    val path: String

    /**
     * The query string that is contained in the request URL after the path.
     */
    val queryString: String?

    val headers: List<Pair<String, List<String>>>

    val parameters: List<Pair<String, List<String>>>

    val clientIp: String

    val inputStream: InputStream?

    fun getAttribute(name: String): Any?

    fun setAttribute(name: String, value: Any?)

    fun getSession(createIfNotExists: Boolean = true): org.tekfive.kviash.http.HttpSession?

    fun getCookies(): List<org.tekfive.kviash.http.RequestCookie> {
        val cookieHeaders = headers.filter { it.first.equals("Cookie", true) }
        return cookieHeaders.map { it.second.flatMap { RequestCookie.parseHeader(it) }}.flatten()
    }

    fun getInputReader(): Reader? {
        return inputStream?.let { InputStreamReader(it) }
    }
}