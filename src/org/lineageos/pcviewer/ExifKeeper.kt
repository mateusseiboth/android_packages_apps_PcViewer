package org.lineageos.pcviewer

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Carries a photo's EXIF data (date, camera, exposure, place) over to an edited copy. The orientation is
 * reset and the size updated, because the saved pixels are already turned and cropped; the old thumbnail is
 * left behind. A photo without EXIF, or a format that can't hold it, is saved without.
 */
object ExifKeeper {
    private val KEPT = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_SCENE_CAPTURE_TYPE, ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    )

    fun copy(resolver: ContentResolver, source: Uri, target: File, width: Int, height: Int) {
        runCatching {
            val from = resolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
            val values = KEPT.mapNotNull { tag -> from.getAttribute(tag)?.let { tag to it } }
            if (values.isEmpty()) return
            val to = ExifInterface(target.absolutePath)
            values.forEach { (tag, value) -> to.setAttribute(tag, value) }
            to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            to.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            to.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
            to.saveAttributes()
        }
    }
}
