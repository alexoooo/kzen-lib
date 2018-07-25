package tech.kzen.lib.common.notation.model


// TODO: refactor model to have uniform composable structure
data class ProjectPath(val relativeLocation: String) {
    companion object {
        private val resource = Regex(
                "([a-zA-Z0-9_\\-]+/)*[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+")

        fun matches(relativeLocation: String): Boolean {
            return ! relativeLocation.startsWith("/") &&
                    resource.matches(relativeLocation)
        }
    }

    init {
        if (! matches(relativeLocation)) {
            throw IllegalArgumentException(
                    "Not a valid project path: $relativeLocation")
        }
    }
}