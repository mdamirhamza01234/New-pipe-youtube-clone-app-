package com.youtubeclone.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** NewPipeExtractor ko internet se baat karne ke liye OkHttp wala Downloader chahiye. */
class DownloaderImpl private constructor() : Downloader() {

    val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        var body: okhttp3.RequestBody? = dataToSend?.toRequestBody()
        if (body == null && (httpMethod == "POST" || httpMethod == "PUT")) {
            body = ByteArray(0).toRequestBody()
        }

        val builder = okhttp3.Request.Builder()
            .method(httpMethod, body)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((name, values) in headers) {
            builder.removeHeader(name)
            for (value in values) builder.addHeader(name, value)
        }

        val response = client.newCall(builder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val text = response.body?.string()
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), text, latestUrl)
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        val instance: DownloaderImpl by lazy { DownloaderImpl() }
    }
}
