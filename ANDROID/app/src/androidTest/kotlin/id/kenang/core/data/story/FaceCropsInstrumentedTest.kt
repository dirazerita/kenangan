package id.kenang.core.data.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * The Android face-crop path is the one piece of the face lock (owner
 * 2026-09-12) with no desktop twin: it decodes only the face window from the
 * stored JPEG with BitmapRegionDecoder. This runs on a real device and
 * checks that the window lands where the box says, stays square, and is
 * capped - a mistake here would hand the model a crop of the wrong person.
 */
@RunWith(AndroidJUnit4::class)
class FaceCropsInstrumentedTest {

    private val dir: File
        get() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "facecrops-test")
            .apply { mkdirs() }

    /** A grey photo with one bright red "face" square. */
    private fun photo(width: Int, height: Int, faceLeft: Int, faceTop: Int, faceSide: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        canvas.drawRect(
            faceLeft.toFloat(), faceTop.toFloat(),
            (faceLeft + faceSide).toFloat(), (faceTop + faceSide).toFloat(),
            Paint().apply { color = Color.RED },
        )
        val file = File(dir, "photo_${width}x$height.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        return file
    }

    private fun isReddish(argb: Int) = Color.red(argb) > 180 && Color.green(argb) < 90 && Color.blue(argb) < 90

    @Test
    fun cropIsSquareCappedAndCentredOnTheFace() {
        val src = photo(3000, 2000, faceLeft = 1350, faceTop = 700, faceSide = 300)
        val box = listOf(1350.0 / 3000, 700.0 / 2000, 1650.0 / 3000, 1000.0 / 2000)
        val out = FaceCrops.crop(src, box, File(dir, "face.jpg"))
        assertNotNull("no crop produced", out)
        val img = BitmapFactory.decodeFile(out!!.absolutePath)
        assertEquals("reference must be square", img.width, img.height)
        assertTrue("crop should be padded beyond the face box", img.width > 300 / 2)
        assertTrue("crop is capped at the output side", img.width <= 1024)
        // The face sits in the middle of the window, the corners do not.
        assertTrue("centre pixel is not the face", isReddish(img.getPixel(img.width / 2, img.height / 2)))
        assertTrue("corner should be background", !isReddish(img.getPixel(4, 4)))
        img.recycle()
    }

    @Test
    fun faceAtTheEdgeStillYieldsASquare() {
        val src = photo(2000, 2000, faceLeft = 1820, faceTop = 60, faceSide = 150)
        val box = listOf(0.91, 0.03, 0.985, 0.105)
        val out = FaceCrops.crop(src, box, File(dir, "edge.jpg"))
        assertNotNull(out)
        val img = BitmapFactory.decodeFile(out!!.absolutePath)
        assertEquals(img.width, img.height)
        img.recycle()
    }

    @Test
    fun tooSmallAFaceYieldsNothing() {
        val src = photo(1200, 800, faceLeft = 600, faceTop = 400, faceSide = 30)
        val tiny = listOf(0.50, 0.50, 0.525, 0.5375)
        assertNull(FaceCrops.crop(src, tiny, File(dir, "tiny.jpg")))
    }
}
