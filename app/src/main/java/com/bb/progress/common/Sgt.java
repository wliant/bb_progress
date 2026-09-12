package com.bb.progress.common;

import java.time.LocalDate;
import java.time.ZoneId;

/** The app-wide default timezone: Singapore Time. */
public final class Sgt {

    public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    private Sgt() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
