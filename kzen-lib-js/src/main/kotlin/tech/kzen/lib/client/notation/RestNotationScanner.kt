//package tech.kzen.lib.client.notation
//
////import tech.kzen.lib.client.util.ClientRestUtils.httpGet
//import tech.kzen.lib.client.util.httpGet
//import tech.kzen.lib.common.notation.model.ProjectPath
//import tech.kzen.lib.common.notation.scan.NotationScanner
//import kotlin.js.Json
//
//
//class RestNotationScanner(
//        private val baseUrl: String
//) : NotationScanner {
//    override suspend fun scan(): List<ProjectPath> {
//        val scanText = httpGet("$baseUrl/scan")
//
//        val builder = mutableListOf<ProjectPath>()
//
//        JSON.parse<Array<Json>>(scanText)
//                .map { it["relativeLocation"] as String }
//                .mapTo(builder) { ProjectPath(it) }
//
//        return builder
//    }
//}