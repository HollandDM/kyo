package kyo.internal.util

/** Lowercase hex encoding used throughout SigV4 (canonical payload hash, signature output).
  *
  * Inlined to avoid allocation on the signing hot path.
  */
private[kyo] object Hex:

    private val Digits = "0123456789abcdef".toCharArray

    def encode(bytes: Array[Byte]): String =
        val out = new Array[Char](bytes.length * 2)
        var i   = 0
        while i < bytes.length do
            val b = bytes(i) & 0xff
            out(i * 2)     = Digits(b >>> 4)
            out(i * 2 + 1) = Digits(b & 0x0f)
            i += 1
        new String(out)
end Hex
