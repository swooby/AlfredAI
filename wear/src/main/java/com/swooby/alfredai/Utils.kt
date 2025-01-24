package com.swooby.alfredai

import androidx.wear.phone.interactions.PhoneTypeHelper
import com.swooby.alfred.common.Utils.getMapOfIntFieldsToNames

object Utils {
    fun phoneDeviceTypeToString(phoneType: Int): String {
        val map = getMapOfIntFieldsToNames(
            PhoneTypeHelper::class,
            "DEVICE_TYPE_")
        return (map[phoneType] ?: "INVALID") + "($phoneType)"
    }
}