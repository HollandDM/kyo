package kyo.internal.util

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.*

/** JS (Node.js) implementation of [[Crypto]] using the built-in `crypto` module.
  *
  * v1 supports Node only. Browser environments would need Web Crypto, which is async and would force the rest of the S3 API to surface
  * Promises even on JVM/Native — not worth the contortion for v1.
  */
private[kyo] object CryptoImpl extends Crypto:

    def sha256(data: Array[Byte]): Array[Byte] =
        val hash = CryptoNode.createHash("sha256")
        hash.update(toBuffer(data))
        bufferToArray(hash.digest())

    def hmacSha256(key: Array[Byte], data: Array[Byte]): Array[Byte] =
        val mac = CryptoNode.createHmac("sha256", toBuffer(key))
        mac.update(toBuffer(data))
        bufferToArray(mac.digest())

    private def toBuffer(arr: Array[Byte]): Uint8Array =
        val u = new Uint8Array(arr.length)
        var i = 0
        while i < arr.length do
            u(i) = (arr(i) & 0xff).toShort
            i += 1
        u

    private def bufferToArray(buf: Uint8Array): Array[Byte] =
        val out = new Array[Byte](buf.length)
        var i   = 0
        while i < buf.length do
            out(i) = buf(i).toByte
            i += 1
        out

end CryptoImpl

@js.native
@JSImport("crypto", JSImport.Namespace)
private object CryptoNode extends js.Object:
    def createHash(algorithm: String): NodeHash                   = js.native
    def createHmac(algorithm: String, key: Uint8Array): NodeHash = js.native

@js.native
private trait NodeHash extends js.Object:
    def update(data: Uint8Array): NodeHash = js.native
    def digest(): Uint8Array               = js.native
