//package tech.kzen.lib.client.util
//
//
//import org.w3c.xhr.XMLHttpRequest
//import kotlin.browser.window
//import kotlin.coroutines.experimental.*
//import kotlin.js.Promise
//
//
//external fun encodeURIComponent(str: String): String
//
//
////private val spaRoot = window.location.pathname.substringBeforeLast("/")
//
//
////private fun getWsServer(): String {
////    val location = window.location
////    val wsProtocol = if (location.protocol == "https:") "wss" else "ws"
////    return "$wsProtocol://${location.host}"
////}
//
//
//suspend fun httpGet(url: String): String = suspendCoroutine { c ->
//    console.log("^^^ httpGet", url)
//
//    val xhr = XMLHttpRequest()
//    xhr.onreadystatechange = {
//        if (xhr.readyState == XMLHttpRequest.DONE) {
//            if (xhr.status / 100 == 2) {
//                c.resume(xhr.response as String)
//            }
//            else {
//                c.resumeWithException(RuntimeException("HTTP error: ${xhr.status}"))
//            }
//        }
//        null
//    }
//    xhr.open("GET", url)
//    xhr.send()
//}
//
//
////fun <T> async(x: suspend () -> T): Promise<T> {
////    return Promise { resolve, reject ->
////        x.startCoroutine(object : Continuation<T> {
////            override val context = EmptyCoroutineContext
////
////            override fun resume(value: T) {
////                resolve(value)
////            }
////
////            override fun resumeWithException(exception: Throwable) {
////                reject(exception)
////            }
////        })
////    }
////}