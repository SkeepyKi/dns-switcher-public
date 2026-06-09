package com.example.dns_switcher

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale

/**
 * Утилита для проверки задержки (ping) DNS-серверов.
 * Измеряет время, необходимое для выполнения запроса типа A (IPv4) для конкретного домена.
 */
object DnsPingTester {

    private const val DNS_PORT = 53
    private const val TRANSACTION_ID = 0x4A7B // Произвольный ID транзакции

    /**
     * Выполняет DNS-запрос для домена через указанный DNS-сервер и замеряет время отклика.
     *
     * @param dnsServer Адрес DNS-сервера (IP или хостнейм, например, "8.8.8.8" или "dns.google")
     * @param domain Тестируемый домен (например, "gemini.google.com")
     * @param timeoutMs Таймаут ожидания ответа в миллисекундах
     * @return Задержка в мс, или -1 в случае ошибки/таймаута
     */
    fun testDnsPing(dnsServer: String, domain: String, timeoutMs: Int = 1500): Int {
        if (dnsServer.isBlank() || domain.isBlank()) return -1

        var plainSocket: java.net.Socket? = null
        var sslSocket: javax.net.ssl.SSLSocket? = null
        return try {
            val queryBytes = buildDnsQuery(domain)
            val serverAddress = InetAddress.getByName(dnsServer)

            plainSocket = java.net.Socket()
            val startTime = System.nanoTime()
            plainSocket.connect(java.net.InetSocketAddress(serverAddress, 853), timeoutMs)
            plainSocket.soTimeout = timeoutMs

            val sf = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            sslSocket = sf.createSocket(plainSocket, dnsServer, 853, true) as javax.net.ssl.SSLSocket
            sslSocket.startHandshake()

            val dos = java.io.DataOutputStream(sslSocket.outputStream)
            val dis = java.io.DataInputStream(sslSocket.inputStream)

            // DNS over TCP requires 2-byte length prefix
            dos.writeShort(queryBytes.size)
            dos.write(queryBytes)
            dos.flush()

            val length = dis.readShort().toInt() and 0xFFFF
            val receiveBuffer = ByteArray(length)
            dis.readFully(receiveBuffer)
            
            val endTime = System.nanoTime()

            // Проверяем ID транзакции в ответе (первые 2 байта)
            val responseId = ((receiveBuffer[0].toInt() and 0xFF) shl 8) or (receiveBuffer[1].toInt() and 0xFF)
            if (responseId == TRANSACTION_ID) {
                val durationMs = (endTime - startTime) / 1_000_000
                durationMs.toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        } finally {
            try {
                sslSocket?.close()
            } catch (e: Exception) {}
            try {
                plainSocket?.close()
            } catch (e: Exception) {}
        }
    }

    /**
     * Формирует байтовый массив стандартного DNS-запроса (тип A, класс IN).
     */
    private fun buildDnsQuery(domain: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Заголовок DNS (12 байт)
        dos.writeShort(TRANSACTION_ID)              // ID транзакции
        dos.writeShort(0x0100)                      // Флаги: стандартный запрос, желательна рекурсия
        dos.writeShort(1)                           // QDCOUNT: 1 вопрос
        dos.writeShort(0)                           // ANCOUNT: 0 ответов
        dos.writeShort(0)                           // NSCOUNT: 0 записей авторитета
        dos.writeShort(0)                           // ARCOUNT: 0 дополнительных записей

        // Секция вопроса (QNAME)
        // Домен форматируется в виде набора меток: длина + ASCII символы (например, "gemini.google.com" -> 6"gemini"6"google"3"com"0)
        val cleanDomain = domain.trim().lowercase(Locale.US)
        val labels = cleanDomain.split(".")
        for (label in labels) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)                            // Нулевой байт окончания QNAME

        dos.writeShort(1)                           // QTYPE: A (IPv4 адрес)
        dos.writeShort(1)                           // QCLASS: IN (Internet)

        return baos.toByteArray()
    }
}
