package com.healthsync.phone.data.watchface

/**
 * Curated online catalog of premium dynamic watch faces (.hswf) for Kolabee U8 Ultra.
 * Contains 52+ distinct, high-definition watch faces across Luxury, Sports, Cyberpunk,
 * Retro, Minimal, and Art categories.
 */
object OnlineWatchFaceCatalog {

    data class StoreItem(
        val pkg: HswfPackage,
        val category: String, // "Luxury", "Sports", "Cyberpunk", "Retro", "Minimal", "Art"
        val tags: List<String>,
        val downloadCount: String = "1.2k"
    )

    // ── Helper Constructors for Clean Declarative Dial Generation ───────────────────

    private fun analog(
        id: String,
        name: String,
        category: String,
        description: String,
        tags: List<String>,
        downloads: String = "2.4k",
        bgType: String = "gradient_radial",
        bgColor: String = "#0C1017",
        bgStart: String = "#1A2332",
        bgEnd: String = "#080B10",
        tickCount: Int = 60,
        majorTickColor: String = "#D4AF37",
        minorTickColor: String = "#475569",
        showNumbers: Boolean = true,
        numberType: String = "arabic",
        numberColor: String = "#F1F5F9",
        hourColor: String = "#D4AF37",
        minColor: String = "#F8FAFC",
        secColor: String = "#EF4444",
        handShape: String = "sword",
        heartColor: String = "#EF4444",
        stepColor: String = "#38BDF8",
        batteryColor: String = "#10B981",
        dateColor: String = "#CBD5E1"
    ): StoreItem {
        return StoreItem(
            pkg = HswfPackage(
                id = id,
                name = name,
                author = "HealthSync Atelier",
                version = "1.2.0",
                description = description,
                type = "analog",
                background = BackgroundConfig(
                    type = bgType,
                    colorHex = bgColor,
                    gradientStartHex = bgStart,
                    gradientEndHex = bgEnd
                ),
                dial = DialConfig(
                    showTicks = true,
                    tickCount = tickCount,
                    majorTickLength = 15f,
                    minorTickLength = 7f,
                    tickWidth = 2.2f,
                    majorTickColorHex = majorTickColor,
                    minorTickColorHex = minorTickColor,
                    showNumbers = showNumbers,
                    numberType = numberType,
                    numberColorHex = numberColor,
                    numberSizeSp = 15f,
                    dialRadiusRatio = 0.88f
                ),
                hands = HandsConfig(
                    hourHand = HandStyle(
                        lengthRatio = 0.52f,
                        widthDp = 5.2f,
                        colorHex = hourColor,
                        shape = handShape,
                        capRadius = 7f
                    ),
                    minuteHand = HandStyle(
                        lengthRatio = 0.78f,
                        widthDp = 3.6f,
                        colorHex = minColor,
                        shape = handShape,
                        capRadius = 6f
                    ),
                    secondHand = HandStyle(
                        lengthRatio = 0.88f,
                        widthDp = 1.8f,
                        colorHex = secColor,
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
                    colorHex = dateColor,
                    hasFrame = true,
                    frameColorHex = "#1E293B"
                ),
                complications = ComplicationsConfig(
                    heartRate = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.72f,
                        style = "subdial",
                        colorHex = heartColor,
                        accentColorHex = "#334155"
                    ),
                    steps = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.28f,
                        yRatio = 0.52f,
                        style = "subdial",
                        colorHex = stepColor,
                        accentColorHex = "#334155"
                    ),
                    battery = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.24f,
                        style = "text_only",
                        colorHex = batteryColor,
                        accentColorHex = "#334155"
                    )
                ),
                aod = AodConfig(
                    mode = "match_dim",
                    hideSecondsHand = true,
                    hideBackground = true,
                    monochromeHex = "#E2E8F0"
                )
            ),
            category = category,
            tags = tags,
            downloadCount = downloads
        )
    }

    private fun digital(
        id: String,
        name: String,
        category: String,
        description: String,
        tags: List<String>,
        downloads: String = "3.2k",
        bgType: String = "solid",
        bgColor: String = "#080B10",
        bgStart: String = "#0F172A",
        bgEnd: String = "#020617",
        timeColor: String = "#00E5FF",
        glowColor: String? = null,
        fontFamily: String = "monospace",
        heartColor: String = "#FF3366",
        stepColor: String = "#00E5FF",
        batteryColor: String = "#10B981",
        dateColor: String = "#94A3B8"
    ): StoreItem {
        return StoreItem(
            pkg = HswfPackage(
                id = id,
                name = name,
                author = "CyberSync Labs",
                version = "1.2.0",
                description = description,
                type = "digital",
                background = BackgroundConfig(
                    type = bgType,
                    colorHex = bgColor,
                    gradientStartHex = bgStart,
                    gradientEndHex = bgEnd
                ),
                dial = DialConfig(
                    showTicks = false,
                    showNumbers = false
                ),
                hands = null,
                digitalClock = DigitalClockConfig(
                    enabled = true,
                    format = "HH:mm",
                    xRatio = 0.5f,
                    yRatio = 0.36f,
                    fontSizeSp = 48f,
                    colorHex = timeColor,
                    glowColorHex = glowColor,
                    isBold = true,
                    fontFamily = fontFamily
                ),
                date = DateConfig(
                    enabled = true,
                    format = "EEE, MMM d",
                    xRatio = 0.5f,
                    yRatio = 0.20f,
                    fontSizeSp = 13f,
                    colorHex = dateColor,
                    hasFrame = false
                ),
                complications = ComplicationsConfig(
                    heartRate = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.30f,
                        yRatio = 0.68f,
                        style = "arc_gauge",
                        colorHex = heartColor,
                        accentColorHex = "#1E293B"
                    ),
                    steps = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.70f,
                        yRatio = 0.68f,
                        style = "arc_gauge",
                        colorHex = stepColor,
                        accentColorHex = "#1E293B"
                    ),
                    battery = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.52f,
                        style = "text_only",
                        colorHex = batteryColor,
                        accentColorHex = "#1E293B"
                    )
                ),
                aod = AodConfig(
                    mode = "digital_minimal",
                    hideSecondsHand = true,
                    hideBackground = true,
                    monochromeHex = "#64748B"
                )
            ),
            category = category,
            tags = tags,
            downloadCount = downloads
        )
    }

    private fun minimal(
        id: String,
        name: String,
        category: String,
        description: String,
        tags: List<String>,
        downloads: String = "2.1k",
        bgColor: String = "#0B0E14",
        tickColor: String = "#E2E8F0",
        hourColor: String = "#F8FAFC",
        minColor: String = "#94A3B8",
        secColor: String = "#38BDF8",
        stepColor: String = "#38BDF8",
        heartColor: String = "#F43F5E"
    ): StoreItem {
        return StoreItem(
            pkg = HswfPackage(
                id = id,
                name = name,
                author = "Nordic Clean Studio",
                version = "1.1.0",
                description = description,
                type = "analog",
                background = BackgroundConfig(
                    type = "solid",
                    colorHex = bgColor
                ),
                dial = DialConfig(
                    showTicks = true,
                    tickCount = 12,
                    majorTickLength = 12f,
                    minorTickLength = 0f,
                    tickWidth = 2.4f,
                    majorTickColorHex = tickColor,
                    showNumbers = false
                ),
                hands = HandsConfig(
                    hourHand = HandStyle(
                        lengthRatio = 0.52f,
                        widthDp = 4.2f,
                        colorHex = hourColor,
                        shape = "baton",
                        capRadius = 6f
                    ),
                    minuteHand = HandStyle(
                        lengthRatio = 0.76f,
                        widthDp = 3.0f,
                        colorHex = minColor,
                        shape = "baton",
                        capRadius = 5f
                    ),
                    secondHand = HandStyle(
                        lengthRatio = 0.86f,
                        widthDp = 1.6f,
                        colorHex = secColor,
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
                        colorHex = heartColor,
                        accentColorHex = "#1E293B"
                    ),
                    steps = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.66f,
                        yRatio = 0.65f,
                        style = "arc_gauge",
                        colorHex = stepColor,
                        accentColorHex = "#1E293B"
                    ),
                    battery = ComplicationWidget(enabled = false)
                ),
                aod = AodConfig(
                    mode = "match_dim",
                    hideSecondsHand = true,
                    monochromeHex = "#94A3B8"
                )
            ),
            category = category,
            tags = tags,
            downloadCount = downloads
        )
    }

    private fun sports(
        id: String,
        name: String,
        description: String,
        tags: List<String>,
        downloads: String = "4.5k",
        accentColor: String = "#10B981",
        secondaryAccent: String = "#06B6D4",
        heartColor: String = "#EF4444"
    ): StoreItem {
        return StoreItem(
            pkg = HswfPackage(
                id = id,
                name = name,
                author = "HealthSync Performance",
                version = "1.3.0",
                description = description,
                type = "hybrid",
                background = BackgroundConfig(
                    type = "gradient_radial",
                    colorHex = "#05070A",
                    gradientStartHex = "#0C131E",
                    gradientEndHex = "#020305"
                ),
                dial = DialConfig(
                    showTicks = true,
                    tickCount = 60,
                    majorTickLength = 12f,
                    minorTickLength = 5f,
                    tickWidth = 2.0f,
                    majorTickColorHex = accentColor,
                    minorTickColorHex = "#1E293B",
                    showNumbers = false
                ),
                hands = HandsConfig(
                    hourHand = HandStyle(
                        lengthRatio = 0.48f,
                        widthDp = 4.8f,
                        colorHex = "#FFFFFF",
                        shape = "sword",
                        capRadius = 6f
                    ),
                    minuteHand = HandStyle(
                        lengthRatio = 0.72f,
                        widthDp = 3.4f,
                        colorHex = accentColor,
                        shape = "sword",
                        capRadius = 5f
                    ),
                    secondHand = HandStyle(
                        lengthRatio = 0.85f,
                        widthDp = 1.6f,
                        colorHex = secondaryAccent,
                        shape = "needle",
                        tailRatio = 0.2f
                    )
                ),
                digitalClock = DigitalClockConfig(
                    enabled = true,
                    format = "HH:mm",
                    xRatio = 0.5f,
                    yRatio = 0.32f,
                    fontSizeSp = 28f,
                    colorHex = "#FFFFFF",
                    fontFamily = "sans"
                ),
                date = DateConfig(
                    enabled = true,
                    format = "EEE d",
                    xRatio = 0.5f,
                    yRatio = 0.20f,
                    fontSizeSp = 12f,
                    colorHex = "#94A3B8"
                ),
                complications = ComplicationsConfig(
                    heartRate = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.28f,
                        yRatio = 0.65f,
                        style = "arc_gauge",
                        colorHex = heartColor,
                        accentColorHex = "#1E293B"
                    ),
                    steps = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.72f,
                        yRatio = 0.65f,
                        style = "arc_gauge",
                        colorHex = accentColor,
                        accentColorHex = "#1E293B"
                    ),
                    battery = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.80f,
                        style = "text_only",
                        colorHex = secondaryAccent
                    )
                ),
                aod = AodConfig(
                    mode = "digital_minimal",
                    monochromeHex = accentColor
                )
            ),
            category = "Sports",
            tags = tags,
            downloadCount = downloads
        )
    }

    // ── 52 Curated Watch Faces ────────────────────────────────────────────────────────

    val items: List<StoreItem> by lazy {
        listOf(
            // ── LUXURY & CHRONOGRAPH (10 Faces) ──────────────────────────────────
            analog(
                id = "chrono_prestige",
                name = "Chrono Prestige",
                category = "Luxury",
                description = "Masterpiece chronograph with brushed titanium sunburst dial, rose-gold indices, and functional subdials.",
                tags = listOf("Chronograph", "Titanium", "Gold", "Subdials"),
                downloads = "5.8k",
                majorTickColor = "#D4AF37",
                hourColor = "#D4AF37",
                minColor = "#F8FAFC",
                secColor = "#EF4444"
            ),
            analog(
                id = "royal_oak_slate",
                name = "Royal Oak Slate",
                category = "Luxury",
                description = "Brushed octagonal luxury dial with tapisserie slate pattern, polished white baton hands, and date aperture.",
                tags = listOf("Octagonal", "Slate", "Steel", "Haute"),
                downloads = "4.2k",
                bgColor = "#0F141C",
                bgStart = "#1E2633",
                bgEnd = "#0A0D12",
                majorTickColor = "#E2E8F0",
                hourColor = "#FFFFFF",
                minColor = "#CBD5E1",
                secColor = "#38BDF8"
            ),
            analog(
                id = "nautilus_blue",
                name = "Nautical Master",
                category = "Luxury",
                description = "Deep marine sunburst radial gradient dial, luminous arrow hands, and polished rose-gold hour batons.",
                tags = listOf("Marine", "Sunburst", "Navy", "Diver"),
                downloads = "6.1k",
                bgColor = "#081226",
                bgStart = "#0F284C",
                bgEnd = "#040914",
                majorTickColor = "#D4AF37",
                hourColor = "#D4AF37",
                minColor = "#E2E8F0",
                secColor = "#E11D48"
            ),
            analog(
                id = "submariner_emerald",
                name = "Heritage Diver 300",
                category = "Luxury",
                description = "Emerald green ceramic bezel dial with oversized diver indices, luminous circle sword hands, and 300M water resistance badge.",
                tags = listOf("Ceramic", "Emerald", "Green", "Diver"),
                downloads = "3.9k",
                bgColor = "#022417",
                bgStart = "#04422B",
                bgEnd = "#01120B",
                majorTickColor = "#10B981",
                hourColor = "#FFFFFF",
                minColor = "#A7F3D0",
                secColor = "#F59E0B"
            ),
            analog(
                id = "daytona_platinum",
                name = "Ice Speedster",
                category = "Luxury",
                description = "Platinum ice-blue dial with chestnut ceramic tachymeter bezel, triple racing subdials, and red chrono sweep hand.",
                tags = listOf("Platinum", "Ice Blue", "Racing", "Tachymeter"),
                downloads = "5.3k",
                bgColor = "#0E1E28",
                bgStart = "#1B3B4F",
                bgEnd = "#060E14",
                majorTickColor = "#38BDF8",
                hourColor = "#E0F2FE",
                minColor = "#BAE6FD",
                secColor = "#EF4444"
            ),
            analog(
                id = "monaco_carrera",
                name = "Grand Prix 1970",
                category = "Luxury",
                description = "Vintage 1970s racing chronograph with dual rounded subdials, vibrant orange chrono second hand, and heritage rally font.",
                tags = listOf("Vintage", "Rally", "Racing", "Chrono"),
                downloads = "2.9k",
                bgColor = "#141724",
                bgStart = "#232A3E",
                bgEnd = "#0B0D14",
                majorTickColor = "#F59E0B",
                hourColor = "#FFFFFF",
                minColor = "#E2E8F0",
                secColor = "#F97316"
            ),
            analog(
                id = "tourbillon_haute",
                name = "Tourbillon Skeleton",
                category = "Luxury",
                description = "Exquisite gold mechanical skeleton dial with visible tourbillon balance wheel cutouts, Roman numeral pips, and Breguet hands.",
                tags = listOf("Tourbillon", "Skeleton", "Gold", "Breguet"),
                downloads = "3.6k",
                bgColor = "#141009",
                bgStart = "#2E2415",
                bgEnd = "#0A0804",
                numberType = "roman",
                majorTickColor = "#D4AF37",
                hourColor = "#FBBF24",
                minColor = "#FDE68A",
                secColor = "#D97706"
            ),
            analog(
                id = "aviator_flieger",
                name = "Pilot Heritage B-Uhr",
                category = "Luxury",
                description = "High-legibility aviator pilot dial with iconic orientation triangle at 12, matte black non-reflective dial, and sword hands.",
                tags = listOf("Aviator", "Pilot", "Flieger", "Military"),
                downloads = "4.0k",
                bgColor = "#080B0F",
                bgStart = "#121820",
                bgEnd = "#040507",
                majorTickColor = "#F8FAFC",
                hourColor = "#FFFFFF",
                minColor = "#E2E8F0",
                secColor = "#EAB308"
            ),
            analog(
                id = "rose_gold_luxe",
                name = "Rose Gold Executive",
                category = "Luxury",
                description = "Ultra-clean champagne dial with refined rose-gold indices, slender needle hands, and subtle date aperture.",
                tags = listOf("Rose Gold", "Dress Watch", "Minimal", "Executive"),
                downloads = "4.8k",
                bgColor = "#1A1412",
                bgStart = "#332622",
                bgEnd = "#0D0A09",
                majorTickColor = "#FB7185",
                hourColor = "#FDA4AF",
                minColor = "#FFE4E6",
                secColor = "#F43F5E"
            ),
            analog(
                id = "moonphase_celestial",
                name = "Starlight Astronomer",
                category = "Luxury",
                description = "Midnight navy starfield dial with constellation markers, celestial moonphase subdial, and fine starlight needle hands.",
                tags = listOf("Moonphase", "Constellation", "Celestial", "Midnight"),
                downloads = "3.4k",
                bgColor = "#070B19",
                bgStart = "#111B3D",
                bgEnd = "#03060C",
                majorTickColor = "#A5B4FC",
                hourColor = "#C7D2FE",
                minColor = "#E0E7FF",
                secColor = "#818CF8"
            ),

            // ── FITNESS & TRI-SPORT (10 Faces) ──────────────────────────────────
            sports(
                id = "tri_rings_pro",
                name = "Activity Rings Pro",
                description = "Three concentric activity rings for Move, Exercise, and Stand with live BPM, step odometer, and calories.",
                tags = listOf("Fitness", "Rings", "Steps", "Activity"),
                downloads = "7.8k",
                accentColor = "#10B981",
                secondaryAccent = "#06B6D4"
            ),
            sports(
                id = "velocity_runner",
                name = "Velocity Pace",
                description = "High-contrast runner's dial with real-time pace splits, cadence arc, and heart-rate training zones.",
                tags = listOf("Running", "Pace", "Cardio", "Splits"),
                downloads = "5.1k",
                accentColor = "#F59E0B",
                secondaryAccent = "#EF4444"
            ),
            sports(
                id = "apex_metabolic",
                name = "Apex Calorie Burn",
                description = "Metabolic burn tracker with dynamic calorie gauge, active distance odometer, and target heart-rate arc.",
                tags = listOf("Calories", "Metabolic", "HIIT", "Cardio"),
                downloads = "4.3k",
                accentColor = "#EC4899",
                secondaryAccent = "#8B5CF6"
            ),
            sports(
                id = "alpine_altimeter",
                name = "Alpine Trekker",
                description = "Rugged mountain expedition dial with altimeter glance, step counter, compass pips, and weather indicator.",
                tags = listOf("Hiking", "Altitude", "Trek", "Outdoor"),
                downloads = "3.7k",
                accentColor = "#14B8A6",
                secondaryAccent = "#0EA5E9"
            ),
            sports(
                id = "aero_marathon",
                name = "Aero 42K Marathon",
                description = "Marathon pacer face with 42.2KM completion progress arc, live elapsed clock, and target split gauge.",
                tags = listOf("Marathon", "Long Run", "42K", "Endurance"),
                downloads = "3.2k",
                accentColor = "#3B82F6",
                secondaryAccent = "#6366F1"
            ),
            sports(
                id = "iron_pulse",
                name = "Iron Core HIIT",
                description = "Cardio-intensive dial with real-time animated ECG pulse wave and color-coded heart training zones.",
                tags = listOf("HIIT", "Cardio", "Heart Rate", "Gym"),
                downloads = "4.9k",
                accentColor = "#EF4444",
                secondaryAccent = "#F97316"
            ),
            sports(
                id = "crossfit_blitz",
                name = "Interval Blitz",
                description = "Segmented circular interval timer ring with bold central digital clock and instant lap glances.",
                tags = listOf("Interval", "Crossfit", "Timer", "WOD"),
                downloads = "2.8k",
                accentColor = "#EAB308",
                secondaryAccent = "#10B981"
            ),
            sports(
                id = "hydro_diver_pro",
                name = "Hydro Master 500",
                description = "High-contrast diving dial with 60-minute elapsed immersion bezel, bold luminous sword hands, and battery ring.",
                tags = listOf("Diver", "Marine", "Swimming", "Waterproof"),
                downloads = "3.5k",
                accentColor = "#06B6D4",
                secondaryAccent = "#0284C7"
            ),
            sports(
                id = "carbon_apex",
                name = "Carbon Fiber Sport",
                description = "Textured woven carbon fiber dial with racing red tick accents, sub-seconds sweep, and step goal ring.",
                tags = listOf("Carbon", "Motorsport", "Racing", "Sport"),
                downloads = "4.1k",
                accentColor = "#DC2626",
                secondaryAccent = "#991B1B"
            ),
            sports(
                id = "kinetic_cadence",
                name = "Kinetic Flow",
                description = "Dynamic step cadence gauge that pulses with motion, vivid gradient progress arc, and battery percentage.",
                tags = listOf("Cadence", "Kinetic", "Motion", "Steps"),
                downloads = "3.0k",
                accentColor = "#84CC16",
                secondaryAccent = "#22C55E"
            ),

            // ── CYBERPUNK & SCI-FI (10 Faces) ──────────────────────────────────
            digital(
                id = "neon_matrix",
                name = "Cyberpunk Matrix HUD",
                category = "Cyberpunk",
                description = "Futuristic neon matrix HUD with glowing cyan digits, battery gauge arc, and live heart rate monitor.",
                tags = listOf("HUD", "Sci-Fi", "Neon", "Cyberpunk"),
                downloads = "8.2k",
                timeColor = "#00E5FF",
                glowColor = "#00838F",
                heartColor = "#FF007F",
                stepColor = "#00E5FF"
            ),
            digital(
                id = "quantum_reactor",
                name = "Quantum Reactor Core",
                category = "Cyberpunk",
                description = "Rotating holographic plasma core with digital hex status, energy battery gauge, and pulse sensor.",
                tags = listOf("Reactor", "Plasma", "Core", "Sci-Fi"),
                downloads = "4.7k",
                timeColor = "#A855F7",
                glowColor = "#6B21A8",
                heartColor = "#EC4899",
                stepColor = "#C084FC"
            ),
            digital(
                id = "glitch_terminal",
                name = "Terminal 2077",
                category = "Cyberpunk",
                description = "Phosphor green command-line terminal with hexadecimal timestamps, uptime counter, and system diagnostic.",
                tags = listOf("Terminal", "Matrix", "Hacker", "Monospace"),
                downloads = "6.5k",
                bgColor = "#020A04",
                timeColor = "#00FF66",
                glowColor = "#006622",
                heartColor = "#33FF88",
                stepColor = "#00FF66",
                batteryColor = "#00FF66"
            ),
            digital(
                id = "synthwave_84",
                name = "Synthwave Sunset",
                category = "Cyberpunk",
                description = "1980s retro-futuristic wireframe sunset with glowing magenta/cyan gradient and vintage digital clock.",
                tags = listOf("Synthwave", "Outrun", "Sunset", "80s"),
                downloads = "5.9k",
                bgColor = "#120524",
                bgStart = "#280A4E",
                bgEnd = "#07020D",
                timeColor = "#FF007F",
                glowColor = "#B00058",
                heartColor = "#FF007F",
                stepColor = "#00F0FF"
            ),
            digital(
                id = "hologram_fighter",
                name = "Fighter Jet HUD",
                category = "Cyberpunk",
                description = "Military jet tactical collimator HUD with altitude ladder, target lock heart rate, and ammo battery gauge.",
                tags = listOf("Jet", "Tactical", "Fighter", "Collimator"),
                downloads = "3.8k",
                timeColor = "#22C55E",
                glowColor = "#15803D",
                heartColor = "#EF4444",
                stepColor = "#4ADE80"
            ),
            digital(
                id = "void_stalker",
                name = "Dark Matter Stealth",
                category = "Cyberpunk",
                description = "Stealth matte carbon dial with ultraviolet accents, glowing phosphor pips, and stealth battery readout.",
                tags = listOf("Stealth", "Ultraviolet", "Dark Matter", "Carbon"),
                downloads = "4.1k",
                bgColor = "#0A0512",
                timeColor = "#C084FC",
                glowColor = "#7E22CE",
                heartColor = "#F43F5E",
                stepColor = "#A855F7"
            ),
            digital(
                id = "titan_mech",
                name = "Titan Mech Pilot",
                category = "Cyberpunk",
                description = "Heavy industrial mecha cockpit cluster with hazard warning stripes and armor battery percentage.",
                tags = listOf("Mech", "Industrial", "Hazard", "Titan"),
                downloads = "2.9k",
                timeColor = "#F59E0B",
                glowColor = "#B45309",
                heartColor = "#EF4444",
                stepColor = "#FBBF24"
            ),
            digital(
                id = "tokyo_neon",
                name = "Tokyo Neon Nights",
                category = "Cyberpunk",
                description = "Electric violet & cyan neon signage glow with bold digital time and heart rate pulse visualizer.",
                tags = listOf("Tokyo", "Shibuya", "Neon", "Electric"),
                downloads = "4.6k",
                timeColor = "#38BDF8",
                glowColor = "#0284C7",
                heartColor = "#F43F5E",
                stepColor = "#818CF8"
            ),
            digital(
                id = "orbital_station",
                name = "Orbital Deep Space",
                category = "Cyberpunk",
                description = "Spacecraft orbital telemetry face with solar battery charge percentage and trajectory arcs.",
                tags = listOf("Space", "Orbital", "NASA", "Telemetry"),
                downloads = "3.3k",
                timeColor = "#67E8F9",
                glowColor = "#0E7490",
                heartColor = "#F87171",
                stepColor = "#22D3EE"
            ),
            digital(
                id = "hex_glitch",
                name = "Hexadecimal Glitch",
                category = "Cyberpunk",
                description = "Honeycomb hexagonal complications with cyberpunk digital artifact styling and battery warning gauge.",
                tags = listOf("Honeycomb", "Glitch", "Hex", "Artifact"),
                downloads = "3.1k",
                timeColor = "#F43F5E",
                glowColor = "#BE123C",
                heartColor = "#FB7185",
                stepColor = "#FDA4AF"
            ),

            // ── RETRO LCD & VINTAGE (8 Faces) ──────────────────────────────────
            digital(
                id = "retro_lcd_91",
                name = "Vintage Digital 1991",
                category = "Retro",
                description = "Authentic slanted 7-segment digital LCD dial with water resist badges, day of week matrix, and step odometer.",
                tags = listOf("LCD", "Vintage", "90s", "Casio Style"),
                downloads = "8.9k",
                bgColor = "#08100C",
                timeColor = "#34D399",
                glowColor = "#059669",
                heartColor = "#F87171",
                stepColor = "#34D399"
            ),
            digital(
                id = "mil_spec_field",
                name = "Mil-Spec Rugged LCD",
                category = "Retro",
                description = "Olive drab tactical casing with negative high-contrast LCD display, 24-hour military clock, and step counter.",
                tags = listOf("Military", "Tactical", "Mil-Spec", "Rugged"),
                downloads = "4.4k",
                bgColor = "#0C120C",
                timeColor = "#A3E635",
                glowColor = "#4D7C0F",
                heartColor = "#EF4444",
                stepColor = "#A3E635"
            ),
            digital(
                id = "arcade_8bit",
                name = "Arcade 8-Bit Pixel",
                category = "Retro",
                description = "Charming 8-bit pixel art font with gaming heart containers for battery life and pixel step counter.",
                tags = listOf("Pixel Art", "8-Bit", "Arcade", "Retro Gaming"),
                downloads = "5.2k",
                bgColor = "#050510",
                timeColor = "#FACC15",
                heartColor = "#EF4444",
                stepColor = "#38BDF8"
            ),
            digital(
                id = "vfd_fluorescent",
                name = "VFD Fluorescent Glow",
                category = "Retro",
                description = "Vacuum fluorescent display from vintage hi-fi receivers with bright cyan glowing digits and audio level meters.",
                tags = listOf("VFD", "Hi-Fi", "Glow", "70s"),
                downloads = "3.7k",
                bgColor = "#020C10",
                timeColor = "#22D3EE",
                glowColor = "#0891B2",
                heartColor = "#F43F5E",
                stepColor = "#22D3EE"
            ),
            digital(
                id = "databank_85",
                name = "Retro Databank 1985",
                category = "Retro",
                description = "Classic calculator watch face with dual screen matrix, grid lines, and daily step odometer.",
                tags = listOf("Databank", "Calculator", "80s", "Tech"),
                downloads = "3.9k",
                bgColor = "#0B0F14",
                timeColor = "#E2E8F0",
                heartColor = "#EF4444",
                stepColor = "#38BDF8"
            ),
            digital(
                id = "amber_crt_console",
                name = "Amber Console CRT",
                category = "Retro",
                description = "Warm amber monochrome CRT glow with scanline feel, monospace numbers, and retro battery indicator.",
                tags = listOf("Amber", "CRT", "Monochrome", "Vintage"),
                downloads = "4.1k",
                bgColor = "#140A02",
                timeColor = "#F59E0B",
                glowColor = "#B45309",
                heartColor = "#EF4444",
                stepColor = "#F59E0B"
            ),
            digital(
                id = "liquid_crystal_gold",
                name = "Gold Digital 1978",
                category = "Retro",
                description = "Vintage dress digital watch face with polished gold bezel styling and classic grey LCD panel.",
                tags = listOf("Gold", "Vintage Dress", "1978", "LCD"),
                downloads = "2.8k",
                bgColor = "#141208",
                timeColor = "#FDE047",
                heartColor = "#EF4444",
                stepColor = "#FDE047"
            ),
            digital(
                id = "depth_master_lcd",
                name = "Twin-Display Marine LCD",
                category = "Retro",
                description = "Split twin-window LCD layout with upper time matrix, lower health dashboard, and dive timer glances.",
                tags = listOf("Marine", "Twin Display", "Split", "LCD"),
                downloads = "3.3k",
                bgColor = "#061017",
                timeColor = "#38BDF8",
                glowColor = "#0284C7",
                heartColor = "#F43F5E",
                stepColor = "#38BDF8"
            ),

            // ── MINIMAL & BAUHAUS (8 Faces) ────────────────────────────────────
            minimal(
                id = "bauhaus_slate",
                name = "Bauhaus Slate Clean",
                category = "Minimal",
                description = "Pure Swiss minimalism with fine hour batons, whisper-thin hands, clean date aperture, and slate background.",
                tags = listOf("Swiss", "Bauhaus", "Minimal", "Slate"),
                downloads = "5.6k",
                bgColor = "#080B10",
                tickColor = "#D4AF37",
                hourColor = "#F8FAFC",
                minColor = "#E2E8F0",
                secColor = "#E11D48"
            ),
            minimal(
                id = "nordic_dawn",
                name = "Nordic Dawn Slate",
                category = "Minimal",
                description = "Frosted charcoal background with muted rose-gold needle hands and clean dual health arcs.",
                tags = listOf("Nordic", "Slate", "Clean", "Dual Arc"),
                downloads = "3.9k",
                bgColor = "#0E1117",
                tickColor = "#94A3B8",
                hourColor = "#FDA4AF",
                minColor = "#F1F5F9",
                secColor = "#F43F5E"
            ),
            minimal(
                id = "pure_eclipse",
                name = "Pure Eclipse AMOLED",
                category = "Minimal",
                description = "Pitch black dial with single white cardinal pip, floating needle hands, and maximum battery savings.",
                tags = listOf("AMOLED", "Eclipse", "Pitch Black", "Zero Battery"),
                downloads = "4.7k",
                bgColor = "#000000",
                tickColor = "#475569",
                hourColor = "#FFFFFF",
                minColor = "#94A3B8",
                secColor = "#38BDF8"
            ),
            minimal(
                id = "mono_dot",
                name = "Mono Accent Dot",
                category = "Minimal",
                description = "Ultra-clean dial where a single vibrant cyan accent dot marks the current hour with slender minute baton.",
                tags = listOf("Dot", "Modernist", "Conceptual", "Minimal"),
                downloads = "2.9k",
                bgColor = "#0A0D12",
                tickColor = "#334155",
                hourColor = "#38BDF8",
                minColor = "#F8FAFC",
                secColor = "#38BDF8"
            ),
            minimal(
                id = "arctic_white",
                name = "Arctic Polar Frost",
                category = "Minimal",
                description = "Clean frosted polar aesthetic with steel blue skeleton batons and subtle grey minute markers.",
                tags = listOf("Polar", "Frost", "Arctic", "Clean"),
                downloads = "3.2k",
                bgColor = "#0A1017",
                tickColor = "#38BDF8",
                hourColor = "#E0F2FE",
                minColor = "#BAE6FD",
                secColor = "#0284C7"
            ),
            minimal(
                id = "charcoal_zen",
                name = "Charcoal Zen",
                category = "Minimal",
                description = "Meditative balance with deep grey brushed dial, whisper-thin second hand, and no extraneous complications.",
                tags = listOf("Zen", "Quiet", "Charcoal", "Simplicity"),
                downloads = "2.6k",
                bgColor = "#111418",
                tickColor = "#64748B",
                hourColor = "#E2E8F0",
                minColor = "#94A3B8",
                secColor = "#F59E0B"
            ),
            minimal(
                id = "aura_radial",
                name = "Aura Spectral Gradient",
                category = "Minimal",
                description = "Smooth violet-to-cyan ambient glow ring with floating minimalist hands and subtle date aperture.",
                tags = listOf("Aura", "Spectral", "Gradient", "Ambient"),
                downloads = "4.2k",
                bgColor = "#0C0717",
                tickColor = "#818CF8",
                hourColor = "#C084FC",
                minColor = "#38BDF8",
                secColor = "#EC4899"
            ),
            minimal(
                id = "monolith_bold",
                name = "Monolith Typo",
                category = "Minimal",
                description = "Oversized Swiss typography hour numerals with clean minimal second sweep and subtle date glance.",
                tags = listOf("Typography", "Swiss", "Bold", "Monolith"),
                downloads = "3.8k",
                bgColor = "#090B0E",
                tickColor = "#E2E8F0",
                hourColor = "#FFFFFF",
                minColor = "#CBD5E1",
                secColor = "#EF4444"
            ),

            // ── ARTISTIC & NATURE (6 Faces) ────────────────────────────────────
            analog(
                id = "deep_nebula",
                name = "Deep Cosmic Nebula",
                category = "Art",
                description = "Stunning celestial deep space nebula swirls with starlight indices, rose-gold hands, and starlight health subdials.",
                tags = listOf("Nebula", "Space", "Cosmic", "Astronomy"),
                downloads = "6.7k",
                bgColor = "#0A0518",
                bgStart = "#1C0D3A",
                bgEnd = "#05020D",
                majorTickColor = "#C084FC",
                hourColor = "#E879F9",
                minColor = "#F0ABFC",
                secColor = "#38BDF8"
            ),
            analog(
                id = "sakura_blossom",
                name = "Sakura Spring Blossom",
                category = "Art",
                description = "Delicate Japanese cherry blossom aesthetic with soft petal pink tones, rose-gold indices, and serene needle hands.",
                tags = listOf("Sakura", "Japan", "Cherry Blossom", "Nature"),
                downloads = "5.4k",
                bgColor = "#180C14",
                bgStart = "#301426",
                bgEnd = "#0D060B",
                majorTickColor = "#FDA4AF",
                hourColor = "#FECDD3",
                minColor = "#FFE4E6",
                secColor = "#FB7185"
            ),
            analog(
                id = "stellar_supernova",
                name = "Solar Supernova",
                category = "Art",
                description = "Radiant golden flare center with dark coronal outer ring, luminous gold hands, and solar battery gauge.",
                tags = listOf("Sun", "Flare", "Supernova", "Gold"),
                downloads = "3.9k",
                bgColor = "#180E04",
                bgStart = "#381E08",
                bgEnd = "#0A0501",
                majorTickColor = "#FBBF24",
                hourColor = "#FDE68A",
                minColor = "#FEF3C7",
                secColor = "#F59E0B"
            ),
            analog(
                id = "abyssal_trench",
                name = "Abyssal Trench Biolum",
                category = "Art",
                description = "Deep oceanic navy gradient with bioluminescent cyan complication pips and deep-sea exploration styling.",
                tags = listOf("Ocean", "Abyss", "Bioluminescent", "Marine"),
                downloads = "4.1k",
                bgColor = "#030E17",
                bgStart = "#072033",
                bgEnd = "#01060A",
                majorTickColor = "#06B6D4",
                hourColor = "#67E8F9",
                minColor = "#A5F3FC",
                secColor = "#14B8A6"
            ),
            analog(
                id = "golden_horizon",
                name = "Golden Hour Horizon",
                category = "Art",
                description = "Warm amber sunset horizon gradient with silhouetted minimalist hands and subtle evening date aperture.",
                tags = listOf("Sunset", "Golden Hour", "Horizon", "Warm"),
                downloads = "3.5k",
                bgColor = "#1C0D06",
                bgStart = "#3D1A0A",
                bgEnd = "#0E0502",
                majorTickColor = "#F97316",
                hourColor = "#FB923C",
                minColor = "#FED7AA",
                secColor = "#EA580C"
            ),
            analog(
                id = "geometric_prism",
                name = "Prism Facets",
                category = "Art",
                description = "Modern low-poly polygon geometric gradient with diamond indices and sleek contemporary hands.",
                tags = listOf("Geometric", "Prism", "Polygon", "Modern"),
                downloads = "3.2k",
                bgColor = "#0C101A",
                bgStart = "#182033",
                bgEnd = "#06080E",
                majorTickColor = "#818CF8",
                hourColor = "#A5B4FC",
                minColor = "#C7D2FE",
                secColor = "#6366F1"
            )
        )
    }
}
