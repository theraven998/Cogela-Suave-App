package com.cogelasuave.data.system

import com.cogelasuave.domain.util.DateProvider
import java.time.LocalDate
import javax.inject.Inject

class SystemDateProvider @Inject constructor() : DateProvider {
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}
