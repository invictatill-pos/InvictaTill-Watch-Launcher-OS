package com.healthsync.phone.data.watchface

/**
 * Curated online catalog of premium dynamic watch faces (.hswf) for Kolabee U8 Ultra.
 * Categorized by Luxury, Sports, Minimal, and Cyberpunk/Digital.
 */
object OnlineWatchFaceCatalog {

    data class StoreItem(
        val pkg: HswfPackage,
        val category: String, // "Luxury", "Sports", "Minimal", "Cyberpunk"
        val tags: List<String>,
        val downloadCount: String = "1.2k"
    )

    val items: List<StoreItem> by lazy {
        listOf(
            StoreItem(
                pkg = HswfPackage(
                    id = "chrono_prestige",
                    name = "Chrono Prestige",
                    author = "HealthSync Atelier",
                    version = "1.2.0",
                    description = "Masterpiece chronograph with brushed titanium sunburst dial, rose-gold indices, functional subdials for Heart Rate, Battery, and Daily Steps.",
                    type = "analog",
                    background = BackgroundConfig(
                        type = "gradient_radial",
                        colorHex = "#0C1017",
                        gradientStartHex = "#1A2332",
                        gradientEndHex = "#080B10"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 60,
                        majorTickLength = 16f,
                        minorTickLength = 7f,
                        tickWidth = 2.2f,
                        majorTickColorHex = "#D4AF37", // Gold
                        minorTickColorHex = "#475569",
                        showNumbers = true,
                        numberType = "arabic",
                        numberColorHex = "#F1F5F9",
                        numberSizeSp = 15f,
                        dialRadiusRatio = 0.88f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.52f,
                            widthDp = 5.2f,
                            colorHex = "#D4AF37",
                            shape = "sword",
                            capRadius = 7f
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.78f,
                            widthDp = 3.6f,
                            colorHex = "#F8FAFC",
                            shape = "sword",
                            capRadius = 6f
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.88f,
                            widthDp = 1.8f,
                            colorHex = "#EF4444",
                            shape = "needle",
                            tailRatio = 0.22f,
                            hasCounterweight = true,
                            smoothSweep = true
                        )
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "EEE d",
                        xRatio = 0.74f,
                        yRatio = 0.5f,
                        fontSizeSp = 12f,
                        colorHex = "#CBD5E1",
                        hasFrame = true,
                        frameColorHex = "#1E293B"
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.72f,
                            style = "subdial",
                            colorHex = "#EF4444",
                            accentColorHex = "#334155"
                        ),
                        steps = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.28f,
                            yRatio = 0.52f,
                            style = "subdial",
                            colorHex = "#38BDF8",
                            accentColorHex = "#334155"
                        ),
                        battery = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.28f,
                            style = "subdial",
                            colorHex = "#10B981",
                            accentColorHex = "#334155"
                        )
                    ),
                    aod = AodConfig(
                        mode = "match_dim",
                        hideSecondsHand = true,
                        hideComplications = false,
                        monochromeHex = "#D4AF37"
                    )
                ),
                category = "Luxury",
                tags = listOf("Chronograph", "Rose Gold", "Subdials", "AOD"),
                downloadCount = "4.8k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "neon_cyberpunk",
                    name = "Neon Cyberpunk",
                    author = "NeoTokyo Labs",
                    version = "2.0.1",
                    description = "Futuristic neon interface with glowing cyan telemetry rings, high-contrast digital clock, and real-time biometric gauges.",
                    type = "hybrid",
                    background = BackgroundConfig(
                        type = "solid",
                        colorHex = "#05070D"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 60,
                        majorTickLength = 12f,
                        minorTickLength = 5f,
                        tickWidth = 1.8f,
                        majorTickColorHex = "#00F0FF",
                        minorTickColorHex = "#1E293B",
                        showNumbers = true,
                        numberType = "minimal_cardinal",
                        numberColorHex = "#FF007F",
                        numberSizeSp = 14f,
                        dialRadiusRatio = 0.90f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.50f,
                            widthDp = 4.5f,
                            colorHex = "#00F0FF",
                            shape = "baton",
                            glowColorHex = "#00F0FF"
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.74f,
                            widthDp = 3.2f,
                            colorHex = "#FF007F",
                            shape = "baton",
                            glowColorHex = "#FF007F"
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.85f,
                            widthDp = 1.5f,
                            colorHex = "#FFE600",
                            shape = "needle",
                            tailRatio = 0.2f
                        )
                    ),
                    digitalClock = DigitalClockConfig(
                        enabled = true,
                        format = "HH:mm",
                        xRatio = 0.5f,
                        yRatio = 0.36f,
                        fontSizeSp = 28f,
                        colorHex = "#00F0FF",
                        glowColorHex = "#00F0FF",
                        fontFamily = "monospace"
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "EEE d",
                        xRatio = 0.5f,
                        yRatio = 0.46f,
                        fontSizeSp = 11f,
                        colorHex = "#94A3B8",
                        hasFrame = false
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.28f,
                            yRatio = 0.65f,
                            style = "arc_gauge",
                            colorHex = "#FF007F",
                            accentColorHex = "#1F2937"
                        ),
                        steps = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.72f,
                            yRatio = 0.65f,
                            style = "arc_gauge",
                            colorHex = "#00F0FF",
                            accentColorHex = "#1F2937"
                        ),
                        battery = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.78f,
                            style = "text_only",
                            colorHex = "#10B981"
                        )
                    ),
                    aod = AodConfig(
                        mode = "monochrome",
                        hideSecondsHand = true,
                        hideComplications = true,
                        monochromeHex = "#00F0FF"
                    )
                ),
                category = "Cyberpunk",
                tags = listOf("Neon", "Digital", "HUD", "Cyber"),
                downloadCount = "6.1k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "bauhaus_minimal",
                    name = "Bauhaus Minimal",
                    author = "Studio Weimar",
                    version = "1.1.0",
                    description = "Timeless German modernist aesthetic. Slender geometric needle hands, subtle typography, and pure monochrome harmony.",
                    type = "analog",
                    background = BackgroundConfig(
                        type = "solid",
                        colorHex = "#09090B"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 12,
                        majorTickLength = 10f,
                        minorTickLength = 4f,
                        tickWidth = 1.5f,
                        majorTickColorHex = "#E4E4E7",
                        minorTickColorHex = "#27272A",
                        showNumbers = true,
                        numberType = "minimal_cardinal",
                        numberColorHex = "#FAFAFA",
                        numberSizeSp = 16f,
                        dialRadiusRatio = 0.85f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.48f,
                            widthDp = 3.5f,
                            colorHex = "#FAFAFA",
                            shape = "needle",
                            capRadius = 5f
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.75f,
                            widthDp = 2.4f,
                            colorHex = "#A1A1AA",
                            shape = "needle",
                            capRadius = 4f
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.86f,
                            widthDp = 1.4f,
                            colorHex = "#E11D48",
                            shape = "needle",
                            tailRatio = 0.25f,
                            hasCounterweight = true
                        )
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "d",
                        xRatio = 0.5f,
                        yRatio = 0.32f,
                        fontSizeSp = 13f,
                        colorHex = "#71717A",
                        hasFrame = false
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.68f,
                            style = "text_only",
                            colorHex = "#E11D48"
                        ),
                        steps = ComplicationWidget(
                            enabled = false
                        ),
                        battery = ComplicationWidget(
                            enabled = false
                        )
                    ),
                    aod = AodConfig(
                        mode = "match_dim",
                        hideSecondsHand = true,
                        hideComplications = true,
                        monochromeHex = "#E4E4E7"
                    )
                ),
                category = "Minimal",
                tags = listOf("Bauhaus", "Minimalist", "Monochrome", "Clean"),
                downloadCount = "3.4k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "vanguard_diver",
                    name = "Vanguard Diver 300M",
                    author = "Oceanic Instruments",
                    version = "1.0.4",
                    description = "Professional marine diving instrument. Deep oceanic blue sunburst dial, luminescent markers, and bold emergency orange second hand.",
                    type = "analog",
                    background = BackgroundConfig(
                        type = "gradient_radial",
                        colorHex = "#061325",
                        gradientStartHex = "#0C2444",
                        gradientEndHex = "#030A14"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 60,
                        majorTickLength = 15f,
                        minorTickLength = 6f,
                        tickWidth = 2.5f,
                        majorTickColorHex = "#38BDF8",
                        minorTickColorHex = "#1E3A5F",
                        showNumbers = true,
                        numberType = "arabic",
                        numberColorHex = "#F0F9FF",
                        numberSizeSp = 16f,
                        dialRadiusRatio = 0.87f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.50f,
                            widthDp = 6.0f,
                            colorHex = "#E0F2FE",
                            shape = "sword",
                            capRadius = 7f
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.76f,
                            widthDp = 4.2f,
                            colorHex = "#38BDF8",
                            shape = "sword",
                            capRadius = 6f
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.88f,
                            widthDp = 2.0f,
                            colorHex = "#F97316", // Bold diver orange
                            shape = "arrow",
                            tailRatio = 0.22f,
                            hasCounterweight = true,
                            smoothSweep = true
                        )
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "EEE d",
                        xRatio = 0.74f,
                        yRatio = 0.5f,
                        fontSizeSp = 12f,
                        colorHex = "#BAE6FD",
                        hasFrame = true,
                        frameColorHex = "#0C2444"
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.70f,
                            style = "arc_gauge",
                            colorHex = "#F97316",
                            accentColorHex = "#0C2444"
                        ),
                        steps = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.28f,
                            yRatio = 0.52f,
                            style = "arc_gauge",
                            colorHex = "#38BDF8",
                            accentColorHex = "#0C2444"
                        ),
                        battery = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.28f,
                            style = "text_only",
                            colorHex = "#10B981"
                        )
                    ),
                    aod = AodConfig(
                        mode = "match_dim",
                        hideSecondsHand = true,
                        monochromeHex = "#38BDF8"
                    )
                ),
                category = "Sports",
                tags = listOf("Diver", "Marine", "Lume", "Rugged"),
                downloadCount = "2.9k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "aero_flight",
                    name = "Aero Flight Mk.II",
                    author = "Aviation Horology",
                    version = "1.0.2",
                    description = "Classic Flieger pilot instrument with high-visibility sword hands, altimeter-inspired subdial gauges, and matte obsidian canvas.",
                    type = "analog",
                    background = BackgroundConfig(
                        type = "solid",
                        colorHex = "#0B0C0E"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 60,
                        majorTickLength = 14f,
                        minorTickLength = 5f,
                        tickWidth = 2.0f,
                        majorTickColorHex = "#FBBF24",
                        minorTickColorHex = "#334155",
                        showNumbers = true,
                        numberType = "arabic",
                        numberColorHex = "#F8FAFC",
                        numberSizeSp = 17f,
                        dialRadiusRatio = 0.88f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.53f,
                            widthDp = 5.6f,
                            colorHex = "#F8FAFC",
                            shape = "sword",
                            capRadius = 7f
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.78f,
                            widthDp = 3.8f,
                            colorHex = "#FBBF24",
                            shape = "sword",
                            capRadius = 6f
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.86f,
                            widthDp = 1.6f,
                            colorHex = "#EF4444",
                            shape = "needle",
                            tailRatio = 0.24f,
                            hasCounterweight = true
                        )
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "d",
                        xRatio = 0.75f,
                        yRatio = 0.5f,
                        fontSizeSp = 13f,
                        colorHex = "#FBBF24",
                        hasFrame = true,
                        frameColorHex = "#1E293B"
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.72f,
                            style = "subdial",
                            colorHex = "#EF4444",
                            accentColorHex = "#1E293B"
                        ),
                        steps = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.28f,
                            yRatio = 0.52f,
                            style = "subdial",
                            colorHex = "#FBBF24",
                            accentColorHex = "#1E293B"
                        ),
                        battery = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.30f,
                            style = "text_only",
                            colorHex = "#10B981"
                        )
                    ),
                    aod = AodConfig(
                        mode = "match_dim",
                        hideSecondsHand = true,
                        monochromeHex = "#FBBF24"
                    )
                ),
                category = "Sports",
                tags = listOf("Pilot", "Flieger", "Military", "High-Vis"),
                downloadCount = "2.1k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "quantum_pulse",
                    name = "Quantum Pulse",
                    author = "CyberGrid Works",
                    version = "1.0.0",
                    description = "Electroluminescent phosphor green digital display with live biometric telemetry arcs and sci-fi aesthetic.",
                    type = "digital",
                    background = BackgroundConfig(
                        type = "solid",
                        colorHex = "#040A06"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 60,
                        majorTickLength = 10f,
                        minorTickLength = 4f,
                        tickWidth = 1.6f,
                        majorTickColorHex = "#10B981",
                        minorTickColorHex = "#064E3B",
                        showNumbers = false,
                        dialRadiusRatio = 0.92f
                    ),
                    hands = null,
                    digitalClock = DigitalClockConfig(
                        enabled = true,
                        format = "HH:mm",
                        xRatio = 0.5f,
                        yRatio = 0.40f,
                        fontSizeSp = 38f,
                        colorHex = "#10B981",
                        glowColorHex = "#10B981",
                        isBold = true,
                        fontFamily = "monospace"
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "EEE, MMM d",
                        xRatio = 0.5f,
                        yRatio = 0.52f,
                        fontSizeSp = 12f,
                        colorHex = "#6EE7B7",
                        hasFrame = false
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.30f,
                            yRatio = 0.68f,
                            style = "arc_gauge",
                            colorHex = "#EF4444",
                            accentColorHex = "#064E3B"
                        ),
                        steps = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.70f,
                            yRatio = 0.68f,
                            style = "arc_gauge",
                            colorHex = "#10B981",
                            accentColorHex = "#064E3B"
                        ),
                        battery = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.25f,
                            style = "text_only",
                            colorHex = "#34D399"
                        )
                    ),
                    aod = AodConfig(
                        mode = "digital_minimal",
                        monochromeHex = "#10B981"
                    )
                ),
                category = "Cyberpunk",
                tags = listOf("Phosphor", "Terminal", "Digital", "Matrix"),
                downloadCount = "3.9k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "solaris_gold",
                    name = "Solaris Executive",
                    author = "Haute Horlogerie",
                    version = "1.0.1",
                    description = "Ultra-luxurious dress watch dial with champagne sunburst finish, refined Roman numeral markers, and gold Dauphine hands.",
                    type = "analog",
                    background = BackgroundConfig(
                        type = "gradient_radial",
                        colorHex = "#141108",
                        gradientStartHex = "#261F0E",
                        gradientEndHex = "#0D0A04"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 60,
                        majorTickLength = 12f,
                        minorTickLength = 5f,
                        tickWidth = 2.0f,
                        majorTickColorHex = "#EAB308",
                        minorTickColorHex = "#713F12",
                        showNumbers = true,
                        numberType = "roman",
                        numberColorHex = "#FEF08A",
                        numberSizeSp = 15f,
                        dialRadiusRatio = 0.86f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.50f,
                            widthDp = 5.0f,
                            colorHex = "#EAB308",
                            shape = "sword",
                            capRadius = 6f
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.75f,
                            widthDp = 3.4f,
                            colorHex = "#FEF08A",
                            shape = "sword",
                            capRadius = 5f
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.85f,
                            widthDp = 1.4f,
                            colorHex = "#CA8A04",
                            shape = "needle",
                            tailRatio = 0.2f
                        )
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "d",
                        xRatio = 0.74f,
                        yRatio = 0.5f,
                        fontSizeSp = 12f,
                        colorHex = "#FEF08A",
                        hasFrame = true,
                        frameColorHex = "#422006"
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.70f,
                            style = "text_only",
                            colorHex = "#EAB308"
                        ),
                        steps = ComplicationWidget(enabled = false),
                        battery = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.5f,
                            yRatio = 0.32f,
                            style = "text_only",
                            colorHex = "#CA8A04"
                        )
                    ),
                    aod = AodConfig(
                        mode = "match_dim",
                        hideSecondsHand = true,
                        monochromeHex = "#EAB308"
                    )
                ),
                category = "Luxury",
                tags = listOf("Gold", "Roman", "Dress Watch", "Classic"),
                downloadCount = "1.8k"
            ),

            StoreItem(
                pkg = HswfPackage(
                    id = "nordic_slate",
                    name = "Nordic Slate",
                    author = "Nordic Concept",
                    version = "1.0.0",
                    description = "Muted Scandinavian slate blue dial with pure geometry, dual concentric health arcs, and minimalist modern typography.",
                    type = "analog",
                    background = BackgroundConfig(
                        type = "solid",
                        colorHex = "#0F172A"
                    ),
                    dial = DialConfig(
                        showTicks = true,
                        tickCount = 12,
                        majorTickLength = 11f,
                        minorTickLength = 5f,
                        tickWidth = 2.0f,
                        majorTickColorHex = "#94A3B8",
                        minorTickColorHex = "#334155",
                        showNumbers = true,
                        numberType = "dots",
                        numberColorHex = "#E2E8F0",
                        numberSizeSp = 15f,
                        dialRadiusRatio = 0.87f
                    ),
                    hands = HandsConfig(
                        hourHand = HandStyle(
                            lengthRatio = 0.52f,
                            widthDp = 4.2f,
                            colorHex = "#F8FAFC",
                            shape = "baton",
                            capRadius = 6f
                        ),
                        minuteHand = HandStyle(
                            lengthRatio = 0.76f,
                            widthDp = 3.0f,
                            colorHex = "#94A3B8",
                            shape = "baton",
                            capRadius = 5f
                        ),
                        secondHand = HandStyle(
                            lengthRatio = 0.86f,
                            widthDp = 1.6f,
                            colorHex = "#38BDF8",
                            shape = "needle",
                            tailRatio = 0.2f
                        )
                    ),
                    date = DateConfig(
                        enabled = true,
                        format = "EEE d",
                        xRatio = 0.5f,
                        yRatio = 0.34f,
                        fontSizeSp = 12f,
                        colorHex = "#64748B",
                        hasFrame = false
                    ),
                    complications = ComplicationsConfig(
                        heartRate = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.34f,
                            yRatio = 0.65f,
                            style = "arc_gauge",
                            colorHex = "#F43F5E",
                            accentColorHex = "#1E293B"
                        ),
                        steps = ComplicationWidget(
                            enabled = true,
                            xRatio = 0.66f,
                            yRatio = 0.65f,
                            style = "arc_gauge",
                            colorHex = "#38BDF8",
                            accentColorHex = "#1E293B"
                        ),
                        battery = ComplicationWidget(
                            enabled = false
                        )
                    ),
                    aod = AodConfig(
                        mode = "match_dim",
                        hideSecondsHand = true,
                        monochromeHex = "#94A3B8"
                    )
                ),
                category = "Minimal",
                tags = listOf("Nordic", "Slate", "Clean", "Dual Arc"),
                downloadCount = "2.5k"
            )
        )
    }
}
