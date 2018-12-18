package tech.kzen.lib.common.notation

import tech.kzen.lib.common.notation.model.ProjectPath


object NotationConventions {
    const val prefix: String = "notation/"
    const val suffix: String = ".yaml"
    val mainPath = ProjectPath("notation/main/main.yaml")
}