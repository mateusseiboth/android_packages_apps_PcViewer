package org.lineageos.pcviewer.core

/** File names in the order people expect: "img2" before "img10", case ignored. */
object NaturalOrder : Comparator<String> {
    private val CHUNK = Regex("""\d+|\D+""")

    override fun compare(a: String, b: String): Int {
        val left = CHUNK.findAll(a).map { it.value }.toList()
        val right = CHUNK.findAll(b).map { it.value }.toList()
        val first = left.zip(right).map { (x, y) -> compareChunks(x, y) }.firstOrNull { it != 0 }
        return first ?: left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b, ignoreCase = true).takeIf { it != 0 }
            ?: a.compareTo(b)
    }

    private fun compareChunks(x: String, y: String): Int {
        val bothNumbers = x[0].isDigit() && y[0].isDigit()
        if (!bothNumbers) return x.compareTo(y, ignoreCase = true)
        val a = x.trimStart('0')
        val b = y.trimStart('0')
        return a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
    }
}

/** Document ids of the providers whose folder structure can be read from the id itself. */
object DocumentIds {
    const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** "primary:DCIM/Camera/a.jpg" is in "primary:DCIM/Camera"; a root ("primary:") has no parent. */
    fun parentOf(authority: String, documentId: String): String? {
        if (authority != EXTERNAL_STORAGE || ':' !in documentId) return null
        val root = documentId.substringBefore(':')
        val path = documentId.substringAfter(':').takeIf { it.isNotEmpty() } ?: return null
        return "$root:" + path.substringBeforeLast('/', "")
    }
}
