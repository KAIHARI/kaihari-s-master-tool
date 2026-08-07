package com.kaiharimoto.mastertool.ui.fx

import java.util.Calendar

actual fun localHour(): Int =
    Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
