package com.fitflow.training.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val pageNarration = listOf(
    "从动作库建立今日计划。按图完成一次力量训练计划，截图来自当前安卓版本。第一步，选择动作：首页点力量训练，进入力量动作库。可搜索动作，或按训练部位和难度筛选。点动作右侧加号，把动作加入今日计划。第二步，设置每组内容：填写重量、计划组数、每组次数及组间休息，然后保存。系统生成逐组小卡，点修改可单独调整每组。动作间休息可另设。检查顺序后点底部开始训练。下次直接套用：今日计划可保存为预设；下次点导入预设，选择覆盖或追加。共 7 个预设槽位。",
    "训练、助教和每日打卡。训练按组推进，完成后可自动打卡，也能在日历补记过去的训练。第三步，训练与休息：做完一组点完成本组，休息倒计时自动启动。可跳过休息或延长三十秒，右上角可暂停或直接结束训练。提前结束会保留已完成组数并进入总结。开启 AI 助教并允许麦克风，先说你好教练，五秒内说一条指令。识别到完成、休息或跳过、暂停会执行对应操作；多种操作同句不执行。第四步，总结与打卡：训练结束后，在总结页点完成训练并打卡。首页点查看每日打卡，选择日期查看训练内容和完成度。过去日期可补卡，补填动作、小卡内容与已完成组数。使用提示：AI 助教可选男声或女声；天气需联网。若出现红色提醒权限提示，请按界面开启通知。训练记录保存在本机。",
)

private class TutorialPdf(
    private val temporaryFile: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    val pageCount: Int get() = renderer.pageCount

    @Synchronized fun render(index: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    @Synchronized override fun close() {
        renderer.close()
        descriptor.close()
        temporaryFile.delete()
    }

    companion object {
        fun open(context: Context): TutorialPdf {
            val file = File(context.cacheDir, "fitness-tutorial-guide.pdf")
            try {
                context.assets.open("tutorial/guide.pdf").use { source ->
                    file.outputStream().use { destination -> source.copyTo(destination) }
                }
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    return TutorialPdf(file, descriptor, PdfRenderer(descriptor))
                } catch (error: Exception) {
                    descriptor.close()
                    throw error
                }
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }
}

@Composable
fun TutorialScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val document = remember(context) { runCatching { TutorialPdf.open(context) }.getOrNull() }
    DisposableEffect(document) { onDispose { document?.close() } }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var zoom by rememberSaveable { mutableIntStateOf(1) }
    val pageResult by produceState<Pair<Int, Result<Bitmap>>?>(null, document, pageIndex) {
        val requestedPage = pageIndex
        value = document?.let {
            requestedPage to withContext(Dispatchers.IO) {
                runCatching { it.render(requestedPage.coerceIn(0, it.pageCount - 1)) }
            }
        }
    }
    val currentPageResult = pageResult?.takeIf { it.first == pageIndex }?.second
    val bitmap = currentPageResult?.getOrNull()

    Column(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF8F5EC))
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回首页") }
            Text("新手教程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (document == null) {
            Text("教程暂时无法打开，请稍后重试。")
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { zoom = 1 }, enabled = zoom > 1) { Text("缩小") }
                Text(if (zoom == 1) "双倍放大后可滚动查看" else "已放大，可滚动查看")
                TextButton(onClick = { zoom = 2 }, enabled = zoom < 2) { Text("放大") }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val imageWidth = maxWidth * zoom
                when {
                    currentPageResult == null -> Text("正在加载教程…")
                    bitmap == null -> Text("教程页面暂时无法显示，请返回后重试。")
                    else -> key(pageIndex, zoom) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "教程第 ${pageIndex + 1} 页。${pageNarration[pageIndex]}",
                                modifier = Modifier.width(imageWidth).aspectRatio(bitmap.width.toFloat() / bitmap.height),
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("上一页") }
                Text("第 ${pageIndex + 1} / ${document.pageCount} 页")
                TextButton(onClick = { pageIndex++ }, enabled = pageIndex < document.pageCount - 1) { Text("下一页") }
            }
        }
    }
}
