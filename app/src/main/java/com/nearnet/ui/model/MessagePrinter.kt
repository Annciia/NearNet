package com.nearnet.ui.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.data.model.UserData
import java.text.SimpleDateFormat
import java.util.Locale

const val PAGE_WIDTH = 595
const val PAGE_HEIGHT = 842
const val MARGIN = 40f
const val TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN

fun saveMessagesToUri(
    context: Context,
    uri: Uri,
    messages: List<Message>,
    users: List<UserData>,
    textColor: Int
) {
    val paint = TextPaint().apply {
        textSize = 12f
        isAntiAlias = true
        color = textColor
    }
    val headerPaint = TextPaint().apply {
        textSize = 12f
        isFakeBoldText = true
        isAntiAlias = true
        color = textColor
    }
    val datePaint = TextPaint().apply {
        textSize = 8f
        isAntiAlias = true
        color = textColor
    }
    val dateFormat = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault())

    val pdfDocument = PdfDocument()

    // Start first page
    var pageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    var y = MARGIN

    messages.forEach({ message ->

        // Date
        val dateText = dateFormat.format(message.timestamp.toLong())
        val dateHeight = datePaint.descent() - datePaint.ascent() + 5f

        // Header
        val user = users.find { user -> user.id == message.userId }
        val headerText = user?.name ?: "Unknown"
        val headerHeight = headerPaint.descent() - headerPaint.ascent() + 5f

        // Message
        var messageHeight = 0f
        var messageStaticLayout: StaticLayout? = null
        if (message.messageType == MessageType.TEXT.name) {
            messageStaticLayout = StaticLayout.Builder
                .obtain(message.message, 0, message.message.length, paint, TEXT_WIDTH.toInt())
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            messageHeight = messageStaticLayout.height.toFloat()
        }
        var bitmap: Bitmap? = null
        if (message.messageType == MessageType.IMAGE.name) {
            bitmap = decodeBase64Bitmap(message.message)
            if (bitmap != null) {
                messageHeight = bitmap.height.toFloat()
            }
        }
        if (message.messageType == MessageType.FILE.name) {
            messageHeight = paint.descent() - paint.ascent() + 5f
        }

        val totalHeight = dateHeight + headerHeight + messageHeight

        // Create new page if needed
        if (y + totalHeight > PAGE_HEIGHT - MARGIN) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        // Draw date
        canvas.drawText(dateText, MARGIN, y - datePaint.ascent(), datePaint)
        y += dateHeight + 5f

        // Draw header
        canvas.drawText(headerText, MARGIN, y - headerPaint.ascent(), headerPaint)
        y += headerHeight + 5f

        // Draw message
        if (messageStaticLayout != null) {
            canvas.save()
            canvas.translate(MARGIN, y)
            messageStaticLayout.draw(canvas)
            canvas.restore()
            y += messageStaticLayout.height + 5f
        }

        // Draw image
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, MARGIN, y, null)
            y += bitmap.height + 5f
        }

        // Draw file
        if (message.messageType == MessageType.FILE.name) {
            val (metadata) = decodeMessageFileMetadata(message.message)
            canvas.drawText(metadata.filename, MARGIN, y - paint.ascent(), paint)
            y += paint.descent() - paint.ascent() + 5f
        }

        y += 15f
    })
    pdfDocument.finishPage(page)

    context.contentResolver.openOutputStream(uri)?.use { output->
        pdfDocument.writeTo(output)
    }
    pdfDocument.close()
}
