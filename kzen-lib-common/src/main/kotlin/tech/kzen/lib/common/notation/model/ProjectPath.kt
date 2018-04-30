package tech.kzen.lib.common.notation.model


// TODO: refactor model to have uniform composable structure
data class ProjectPath(val relativeLocation: String) {
    private object Patterns {
        val resource = Regex(
                "([a-zA-Z0-9_-]+/)*[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+")
    }

    init {
        if (relativeLocation.startsWith("/")) {
            throw IllegalArgumentException(
                    "must be relative: " + relativeLocation)
        }
        if (! Patterns.resource.matches(relativeLocation)) {
            throw IllegalArgumentException(
                    "must be slash-delimited ascii path: " + relativeLocation)
        }
    }
}