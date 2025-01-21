package com.swooby.alfredai

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
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

object AppUtils {
    private val TAG = AppUtils::class.java.simpleName

    fun showToast(context: Context,
                  message: String,
                  duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
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
