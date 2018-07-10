//package tech.kzen.lib.client.util
//
//import org.w3c.xhr.XMLHttpRequest
//import kotlin.coroutines.experimental.suspendCoroutine
//
//
//internal object ClientRestUtils {
//
//
//    suspend fun httpGet(url: String): String = suspendCoroutine { c ->
//        val xhr = XMLHttpRequest()
//        xhr.onreadystatechange = {
//            if (xhr.readyState == XMLHttpRequest.DONE) {
//                if (xhr.status / 100 == 2) {
//                    c.resume(xhr.response as String)
//                }
//                else {
//                    c.resumeWithException(RuntimeException("HTTP error: ${xhr.status}"))
//                }
//            }
//            null
//        }
//        xhr.open("GET", url)
//        xhr.send()
//    }
//}