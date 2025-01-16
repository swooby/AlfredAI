package com.swooby.alfredai

import android.content.Context
import android.media.MediaPlayer
import android.util.JsonReader
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.io.StringReader
import kotlin.reflect.KClass

object Utils {
    private val TAG = Utils::class.java.simpleName

    fun quote(value: Any?, typeOnly: Boolean = false): String {
        if (value == null) {
            return "null"
        }

        if (typeOnly) {
            return getShortClassName(value)
        }

        if (value is String) {
            return "\"$value\""
        }

        if (value is CharSequence) {
            return "\"$value\""
        }

        return value.toString()
    }

    fun getShortClassName(value: Any?): String {
        return when (value) {
            is KClass<*> -> value.simpleName ?: "null"
            else -> value?.javaClass?.simpleName ?: "null"
        }
    }

    fun playAudioResourceOnce(
        context: Context,
        audioResourceId: Int,
        volume: Float = 0.7f,
        state: Any? = null,
        onCompletion: ((Any?) -> Unit)? = null
    ) {
        Log.d(TAG, "+playAudioResourceOnce(..., audioResourceId=$audioResourceId, ...)")
        MediaPlayer.create(context, audioResourceId).apply {
            setVolume(volume, volume)
            setOnCompletionListener {
                onCompletion?.invoke(state)
                it.release()
                Log.d(TAG, "-playAudioResourceOnce(..., audioResourceId=$audioResourceId, ...)")
            }
            start()
        }
    }

    fun extractValue(key: String, jsonString: String): String? {
        try {
            val reader = JsonReader(StringReader(jsonString))
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == key) {
                    val type = reader.nextString()
                    reader.close()
                    return type
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()
        } catch (e: IOException) {
            Log.e(TAG, "extractValue: Error parsing JSON: ${e.message}")
        }
        return null
    }

    @Composable
    fun annotatedStringFromHtml(resId: Int): AnnotatedString {
        return annotatedStringFromHtml(stringResource(id = resId))
    }

    @Composable
    fun annotatedStringFromHtml(html: String): AnnotatedString {
        return buildAnnotatedString {
            AppendHtmlNode(Jsoup.parse(html).body())
        }
    }

    @Composable
    private fun AnnotatedString.Builder.AppendHtmlNode(node: Node) {
        when (node) {
            is TextNode -> {
                // Just text (leaf node)
                var text = node.toString()
                if (text.isBlank() or text.startsWith('\n')) {
                    text = text.trimStart('\n', ' ')
                }
                append(text)
            }
            is Element -> {
                // Element node: check the tag
                when (node.tagName().lowercase()) {
                    "a" -> {
                        // Handle the <a> tag, e.g. get the href
                        withLink(
                            LinkAnnotation.Url(
                                node.attr("href"),
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            node.childNodes().forEach { child ->
                                AppendHtmlNode(child)
                            }
                        }
                    }
                    "br" -> {
                        // Insert a newline
                        append("\n")
                    }
                    "b", "strong" -> {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            node.childNodes().forEach { child ->
                                AppendHtmlNode(child)
                            }
                        }
                    }
                    "i", "em" -> {
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            node.childNodes().forEach { child ->
                                AppendHtmlNode(child)
                            }
                        }
                    }
                    "pre" -> {
                        withStyle(
                            style = SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                background = MaterialTheme.colorScheme.background,
                            )
                        ) {
                            node.childNodes().forEach { child ->
                                AppendHtmlNode(child)
                            }
                        }
                    }
                    else -> {
                        // For any other tag, just recursively append children
                        node.childNodes().forEach { child ->
                            AppendHtmlNode(child)
                        }
                    }
                }
            }
        }
    }
}
