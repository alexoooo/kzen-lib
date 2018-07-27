//package tech.kzen.lib.client.command
//
//import tech.kzen.lib.client.util.encodeURIComponent
//import tech.kzen.lib.client.util.httpGet
//
//
//class RestCommandApi {
//    suspend fun edit(
//            objectName: String,
//            parameterPath: String,
//            valueYaml: String
//    ) {
//        val encodedName = encodeURIComponent(objectName)
//        val encodedParameter = encodeURIComponent(parameterPath)
//        val encodedValue = encodeURIComponent(valueYaml)
//
//        httpGet("/command/edit?object=$encodedName&parameter=$encodedParameter&value=$encodedValue")
//    }
//}