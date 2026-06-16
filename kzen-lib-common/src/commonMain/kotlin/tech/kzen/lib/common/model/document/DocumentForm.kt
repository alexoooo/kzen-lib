package tech.kzen.lib.common.model.document


// The on-disk shape a DocumentPath denotes. Made explicit (rather than a `directory: Boolean`) so a pure folder
// can never be confused with a directory-document — they are distinct cases with distinct path encodings:
//   Document  -> <nesting>/<name>.yaml          (a regular notation file)
//   Directory -> <nesting>/<name>/~main.yaml    (a directory document that owns a resource subtree, e.g. Feature)
//   Folder    -> <nesting>/<name>/              (a pure, markerless directory that contains nested documents)
enum class DocumentForm {
    Document,
    Directory,
    Folder
}
