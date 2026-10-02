package com.eqv.visualizer.ui

/*
 * The written tutorials: one guide per screen, explaining every setting on it in plain words,
 * plus the step-by-step walkthrough for Create. Shown from the "?" in the header, from the
 * "New here?" banner the first time a screen opens, and from Home → Guides.
 */

data class GuideItem(val name: String, val text: String)

data class GuideSection(val title: String, val text: String? = null, val items: List<GuideItem> = emptyList())

data class Guide(val key: String, val title: String, val intro: String, val sections: List<GuideSection>)

private fun item(name: String, text: String) = GuideItem(name, text)

object Guides {
    /** The guide for a screen, or null when the screen has none. */
    fun forScreen(screen: Screen): Guide? = when (screen) {
        Screen.HOME -> home
        Screen.LAYERS -> visuals
        Screen.FILTER -> filter
        Screen.MOTION -> motion
        Screen.BEAT -> beat
        Screen.HAPTICS -> haptics
        Screen.THUMP -> shake
        Screen.BEHAVIOR -> behavior
        Screen.AUDIO -> audio
        Screen.PERFORMANCE -> performance
        Screen.PRESETS -> presets
        Screen.LAB -> lab
        Screen.CRAFT -> create
        Screen.ROOM -> room
        Screen.PERMISSIONS -> setup
        Screen.DEBUG, Screen.GUIDES -> null
    }

    /** Every guide, in the order the Guides page lists them. */
    val all: List<Guide> by lazy {
        listOf(home, create, visuals, filter, motion, beat, room, lab, presets, haptics, shake, audio, behavior, performance, setup)
    }

    /** The colors part, shared by every layer's Color group. */
    private val colorItems = listOf(
        item("Type", "How the layer is colored. Solid: one color. Gradient: fades from one color to another along the layer. Rainbow: every color, slowly cycling. Album: the 3 main colors of the song's cover art. Per band: one color for bass, one for mids, one for treble."),
        item("Color / From / To", "Tap the circle to open the color picker. Pick a swatch, drag Hue, Saturation and Brightness, or type a hex code like #FF0000."),
        item("Cycle speed", "Rainbow only: how fast the colors move. 0 stands still."),
        item("Fallback 1 / 2", "Album only: used when the song has no cover art."),
        item("Bass / Mids / Treble", "Per band only: the color of the low, middle and high part of the spectrum."),
        item("Glow / bloom", "A soft light around the shapes. More glow looks dreamier but costs a bit of battery. Performance → Quality Low turns glow off."),
        item("Opacity", "How see-through the layer is. Lower it to make a layer sit in the background."),
    )

    // ------------------------------------------------------------------------------ home

    val home = Guide(
        key = "home",
        title = "Home",
        intro = "Everything starts here: turn the visualizer on, pick a preset, and open any setting. The live preview at the top always shows exactly what you'll see over your apps.",
        sections = listOf(
            GuideSection(
                "First time",
                items = listOf(
                    item("1. Setup", "If you see \"Finish setup\", tap Open setup and allow each item. Without \"Display over other apps\" the visuals can't appear over Spotify, YouTube and the rest."),
                    item("2. Turn it on", "Flip the Visualizer switch. With \"Start with music\" on (Behavior), it appears by itself when music plays and leaves when it stops."),
                    item("3. Pick a preset", "Tap a group (Everyday, Party, Chill…) then a preset. It changes right away."),
                    item("4. Make it yours", "Tap + Create for a guided walkthrough, or open Visuals, Filter, Motion and Beat to tweak the current preset."),
                ),
            ),
            GuideSection(
                "What's on this page",
                items = listOf(
                    item("Live preview", "A tiny copy of your screen with the visuals on it. Tap \"Demo off\" to switch on a built-in beat if no music is playing."),
                    item("Visualizer", "The main on/off switch. The line under it says what it's doing: Armed (waiting for music), Active, or why it's hidden or paused."),
                    item("Preset", "\"· edited\" means you changed the preset. Edits save into it automatically; All presets can reset a built-in one."),
                    item("Customize", "Visuals: the shapes (edge, bars, ring, wave, pulse). Filter: old TV, film and other screen effects. Motion: how the shapes move. Beat: how beats are found."),
                    item("Try it", "Test lab: test presets with your own songs or test sounds. Room: the visuals full screen on green or any color."),
                    item("More", "Audio source, Behavior (auto start, hiding, pauses, sync), Haptics, Shake, Performance and Setup."),
                    item("?", "Every page has a ? at the top right. Tap it any time for that page's guide."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ create

    val create = Guide(
        key = "create",
        title = "Create a preset",
        intro = "Create walks you through building a preset in 6 steps. The preview at the top updates with every tap, so just try things. Back goes one step back; leaving without saving puts everything back the way it was.",
        sections = listOf(
            GuideSection(
                "How it works",
                items = listOf(
                    item("Next / Back", "The buttons at the bottom move between steps. The dots at the top show where you are. Your choices stay when you go back."),
                    item("Nothing is saved until the end", "You're working on a draft. Cancel or the back gesture on step 1 throws it away and restores your old preset."),
                    item("Tips", "The TIP card at the top of each step says what to do. Close them with HIDE TIPS; turn them back on in Home → Guides."),
                    item("Test while you build", "Turn on Demo in the preview to see the visuals move without music, or play a song in any app."),
                ),
            ),
            GuideSection(
                "Step 1 · Start from",
                "Pick where to begin. Everything can be changed later, so choose whatever looks closest to what you want.",
                listOf(
                    item("Blank", "Only bottom bars, everything else off. Best when you want to build from scratch."),
                    item("Mine", "Your saved presets, if you have any. Great for making a variation of one."),
                    item("Built-in groups", "Everyday, Filters, Chill, Party, Bass & gym, Night, Focus, Retro & games. Tap one; the preview shows it."),
                ),
            ),
            GuideSection(
                "Step 2 · Layers",
                "Layers are the shapes on screen. Turn on as many as you like; they're drawn on top of each other.",
                listOf(
                    item("Edge glow", "Light that runs around the screen edge, hugging the rounded corners. Full: the whole edge. Running: a light that travels around. Level meter: fills up with the loudness. Corners: the corners pump with the bass. Thickness sets the line width."),
                    item("EQ bars", "Classic equalizer bars. Choose where (Bottom, Top, Top + bottom, Sides), the look (Rounded, Blocks, Dots, Line) and how tall. Mirror puts the bass in the middle."),
                    item("Radial ring", "A circle of bars, usually in the middle. Pick a style (Bars, Dots, Line, Filled, Segments), which way the bars grow, the size, bar length, rotation and how much it bounces on beats."),
                    item("Wave", "A flowing line. Line, Filled or Mirrored, and how far down the screen it sits."),
                    item("Beat ring", "A ring that bursts out from the center on each beat. Set how wide its line is and how many rings follow each other."),
                    item("Want more?", "Every layer has many more options in Visuals on the home screen. They keep working on your preset after you save it."),
                ),
            ),
            GuideSection(
                "Step 3 · Colors",
                "One tap colors every layer at once. Then fine-tune each layer if you want.",
                listOf(
                    item("Palettes", "Nothing (red/white), Mono, Album art (follows the song's cover), Rainbow, Sunset, Ocean, Neon, Lava, Aurora, Candy. The selected one has a white border."),
                    item("Fine-tune", "Pick a layer tab, then change its color type, colors, glow and opacity. See \"Colors\" below for what each one does."),
                ),
            ),
            GuideSection("Colors (every layer)", items = colorItems),
            GuideSection(
                "Step 4 · Range & timing",
                "How the shapes move and which part of the sound they show.",
                listOf(
                    item("Feels", "Ready-made timings. Snappy: jumps and drops fast, very twitchy. Punchy: fast up, quick fall. Smooth: calm and flowing. Dreamy: slow, floating."),
                    item("Rise", "How fast a bar jumps up when the sound gets louder. Low = instant, high = soft."),
                    item("Fall", "How slowly it drops back down. Low = drops right away, high = slow, floaty."),
                    item("Peak hold", "How long the little peak caps and dots wait at the top before falling."),
                    item("Sensitivity", "Overall size of the movement. Turn it up if the shapes barely move, down if they're always maxed out."),
                    item("Bars / bands", "How many slices the sound is cut into. Fewer = chunky bars, more = fine detail."),
                    item("Lowest / highest note", "The sound range shown, from deep bass on the left to treble on the right. Lower the highest note if the right side stays flat."),
                    item("Bass boost", "Makes the bass side bigger. Good for hip hop and EDM."),
                ),
            ),
            GuideSection(
                "Step 5 · Filter",
                "Optional: an effect over the whole screen, like an old TV, tape or film.",
                listOf(
                    item("None / styles", "None turns it off. CRT, VHS, Film, Night vision, Pocket LCD, Dot matrix and Glitch are ready-made looks."),
                    item("Strength", "How strong the whole filter is."),
                    item("Follow the music", "The filter gets stronger when the song is loud. 0 = it stays still."),
                    item("Punch on hits", "Each kick, snare or hi-hat makes its part of the filter pump."),
                    item("Wash color from album art", "Tints the screen with the cover's color."),
                    item("On the beat", "An extra hit on each beat: Flicker, Scan jump, Glitch, Grain burst, Vignette pump or Color pulse."),
                    item("More", "Scanlines, grain, tint color and the rest are in Filter on the home screen (its own guide explains each one)."),
                ),
            ),
            GuideSection(
                "Step 6 · Name & save",
                items = listOf(
                    item("Name", "Type a name (up to 32 letters). Leave it empty and it gets a name for you."),
                    item("Summary", "A last look at your layers, colors, timing, range and filter. Go Back to change anything."),
                    item("Save preset", "Saves it under \"Mine\" and switches to it. Tweaks you make later save into it automatically."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ visuals

    val visuals = Guide(
        key = "visuals",
        title = "Visuals",
        intro = "The shapes on screen, called layers. Each tab is one layer; a dot (●) on the tab means it's on. Changes save into the current preset and show in the preview right away.",
        sections = listOf(
            GuideSection(
                "Edge",
                "Light around the whole screen edge that follows the real rounded corners and the camera hole.",
                listOf(
                    item("Edge lighting", "Turns the layer on or off."),
                    item("Style", "Full: the whole edge glows with the music. Running: a light travels around the edge. Level meter: the edge fills up as it gets louder. Corners: the four corners pump with the bass."),
                    item("Line thickness", "Width of the bright core line."),
                    item("Glow width", "How far the soft glow spreads into the screen."),
                    item("Length", "Running and Level meter: how much of the edge is lit."),
                    item("Run speed", "Running: laps per second."),
                    item("Mirror", "Running: two lights going opposite ways."),
                    item("Corner radius adjust", "Fine-tunes the curve if the glow doesn't sit exactly on your screen's corners."),
                    item("Camera ring", "A glowing ring around the punch-hole camera."),
                    item("Reacts to", "Left: only the bass moves it. Right: everything (all of the sound)."),
                    item("Idle glow", "How much it still glows when the music is quiet. 0 = fully dark between sounds."),
                ),
            ),
            GuideSection(
                "Bars",
                "Classic equalizer bars. Left is bass, right is treble (unless Mirror is on).",
                listOf(
                    item("Position", "Bottom, Top, Top + bottom, or Sides (left and right edges)."),
                    item("Style", "Rounded, Blocks (square), Dots (stacked dots), or Line (one line joining the bar tops)."),
                    item("Height", "Tallest a bar can get, as a part of the screen."),
                    item("Thickness", "Bar width. 100% = no gap between bars."),
                    item("Span", "How much of the edge the bars cover, centered."),
                    item("Corner radius", "How round the bar ends are."),
                    item("Edge margin", "Moves the bars away from the screen edge."),
                    item("Mirror", "Bass in the middle, treble at both ends."),
                    item("Peak hold", "Little caps that hang at the recent highest point."),
                ),
            ),
            GuideSection(
                "Ring",
                "The radial ring: a circle of bars that dances to the music. Bass starts at the top and goes around clockwise.",
                listOf(
                    item("Radial ring", "Turns the ring on or off."),
                    item("Style", "Bars: lines out from the circle. Dots: a dot at each tip. Line: one line joining the tips. Filled: the area between the circle and the tips is filled. Segments: bars made of little blocks, like an LED meter."),
                    item("Bars grow", "Outward: away from the center. Inward: toward the center. Both ways: out and in at the same time."),
                    item("Radius", "Size of the circle the bars start from."),
                    item("Bar length", "How long the bars get at full volume."),
                    item("Thickness", "Width of each bar, dot or line."),
                    item("Bars around", "How many bars make the ring. Auto uses one per band (Motion → Bands, doubled with Mirror). Set it higher for a dense ring, up to 180."),
                    item("Arc", "How much of the circle is used. Full = a whole ring; 180° = a half ring (a rainbow arch), and so on."),
                    item("Start angle", "Turns where the ring begins. With a half ring, 90° or -90° puts the arch on its side."),
                    item("Oval", "Stretches the circle into an oval: wide or tall."),
                    item("Mirror", "Makes both halves match: bass at the start, treble in the middle."),
                    item("Round ends", "Rounded bar ends. Off gives flat, square ends."),
                    item("Center X / Y", "Where the ring sits on screen (50% / 50% = middle)."),
                    item("Rotation", "Slow spin, in turns per second. Negative spins the other way. 0 = still."),
                    item("Spin on beats", "Each beat gives the ring a quick spin kick."),
                    item("Beat bounce", "The ring gets bigger for a moment on each beat. How long it lasts follows Pulse → Decay."),
                    item("Breathe with bass", "The ring grows and shrinks with the bass all the time, not just on beats."),
                    item("Base circle", "A thin circle where the bars start. Off at 0."),
                    item("Center fill", "Fills the inside of the circle with the ring's color. It glows brighter when the music is loud."),
                    item("Peak dots", "Dots that hang at each bar's recent peak (Bars, Dots and Segments styles)."),
                ),
            ),
            GuideSection(
                "Wave",
                "A flowing line across the screen.",
                listOf(
                    item("Style", "Line, Filled (colored underneath), or Mirrored (a copy flipped upside down)."),
                    item("Shape from", "Spectrum: smooth hills that follow bass to treble. Raw wave: the actual sound wave, jittery and fast."),
                    item("Vertical position", "How far down the screen the line sits."),
                    item("Amplitude", "How high the wave moves."),
                    item("Thickness", "Line width."),
                    item("Smoothness", "Higher = softer curves with less detail."),
                    item("Mirror", "Line style: adds a flipped copy."),
                ),
            ),
            GuideSection(
                "Pulse",
                "Something that happens on each beat (found as described in Beat).",
                listOf(
                    item("Style", "Vignette: the screen edges light up. Flash: the whole screen flashes. Ring: a ring bursts out from the center."),
                    item("Strength", "How bright the pulse gets."),
                    item("Decay", "How long it takes to fade. This also sets how long the ring's Beat bounce lasts."),
                    item("Vignette size", "Vignette: how much of the middle stays clear."),
                    item("Ring start size", "Ring: how big the ring is at the moment of the beat."),
                    item("Ring grows", "Ring: how far it spreads while it fades."),
                    item("Ring width", "Ring: line width at the start (it thins out as it fades)."),
                    item("Rings", "Ring: 1 to 3 rings, each a little smaller and fainter, like ripples."),
                    item("Ring fill", "Ring: fills the ring with a soft light, not just the line."),
                ),
            ),
            GuideSection("Color (every layer)", items = colorItems),
        ),
    )

    // ------------------------------------------------------------------------------ filter

    val filter = Guide(
        key = "filter",
        title = "Filter",
        intro = "A screen effect drawn over every app, like an old TV, tape or film. Android doesn't let apps change other apps' pixels, so filters add light and shade on top: lines, stripes, grain, dark corners and a color wash. Tweaking any slider turns the style into Custom.",
        sections = listOf(
            GuideSection(
                "Style",
                items = listOf(
                    item("Off", "No filter."),
                    item("CRT", "Old tube TV: scanlines, RGB stripes, curved dark edges, a rolling bar."),
                    item("VHS", "Tape: warm tint, tracking noise at the bottom, grain."),
                    item("Film", "Cinema: grain, flicker, warm wash, dark corners."),
                    item("Night vision", "Green wash, heavy dark corners, grain."),
                    item("Pocket LCD", "Old handheld console: square pixel grid, green tint."),
                    item("Dot matrix", "Round dots like a LED sign."),
                    item("Glitch", "Digital breakup that reacts hard to the music."),
                    item("Strength", "Master amount: scales every part of the filter at once."),
                ),
            ),
            GuideSection(
                "Music",
                items = listOf(
                    item("Follow the music", "The filter gets stronger when the song is loud. Bass drives the lines, shade and tube edge; mids the stripes and grid; treble the grain and noise. 0 = static."),
                    item("Punch on hits", "Each kick, snare or hi-hat makes its part pump, measured against the song's own recent peaks, so quiet and loud songs hit equally hard."),
                    item("On the beat", "An extra effect on each beat: Flicker, Scan jump, Glitch (sliced bands), Grain burst, Vignette pump or Color pulse."),
                    item("Hit strength / Hit length", "How strong and how long that beat effect is."),
                ),
            ),
            GuideSection(
                "Lines",
                items = listOf(
                    item("Scanlines", "Dark horizontal lines like a CRT."),
                    item("Line spacing", "Distance between scanlines."),
                    item("Line drift", "Scanlines slowly move down."),
                    item("Roll bar", "A bright band that rolls down the screen."),
                    item("Roll speed", "How fast the roll bar moves."),
                ),
            ),
            GuideSection(
                "Pixels",
                items = listOf(
                    item("RGB stripes", "Thin red, green and blue stripes, like a TV up close."),
                    item("Stripe width", "Width of one stripe."),
                    item("Pixel grid", "Dark gaps between big pixels."),
                    item("Grid size", "Size of each pixel cell."),
                    item("Round dots", "Round dots (dot matrix) or square cells (LCD)."),
                ),
            ),
            GuideSection(
                "Light & shade",
                items = listOf(
                    item("Vignette", "Darkens the corners."),
                    item("Tube edge", "Shadow and glass shine around the edges that fakes a curved TV screen."),
                    item("Flicker", "Constant small brightness flicker."),
                    item("Color wash", "Tints the whole screen. Keep it low: it also lightens dark areas."),
                    item("Wash from album art", "Uses the song's cover color for the wash."),
                    item("Wash color", "Pick the wash color yourself."),
                ),
            ),
            GuideSection(
                "Noise",
                items = listOf(
                    item("Grain", "Film or video noise."),
                    item("Grain speed", "How often the grain changes per second (film is 24)."),
                    item("VHS tracking", "Noisy band at the bottom and a wandering noise line, like a worn tape."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ motion

    val motion = Guide(
        key = "motion",
        title = "Motion",
        intro = "How the visuals turn sound into movement: how big, how fast, and which part of the sound. It's part of the preset.",
        sections = listOf(
            GuideSection(
                "Level",
                items = listOf(
                    item("Sensitivity", "Overall size of the movement. Up if the shapes barely move, down if they're always at the top."),
                    item("Auto-gain", "Adjusts itself so quiet and loud songs both fill the bars. Turn it off for a fixed scale where loud songs really look louder."),
                    item("Reference level", "Auto-gain off only: the volume that fills the bars to the top. Lower = quieter music already reaches the top."),
                    item("Dynamic range", "How much quieter than the top still shows. Small = only loud parts move; large = even soft details move."),
                ),
            ),
            GuideSection(
                "Smoothing",
                items = listOf(
                    item("Rise", "How fast bars jump up. 1–10 ms is instant, 50+ ms is soft."),
                    item("Fall", "How slowly they drop. 100 ms is twitchy, 500+ ms is floaty."),
                    item("Peak hold", "How long peak caps and dots wait before falling."),
                    item("Peak fall", "How fast they fall after that."),
                ),
            ),
            GuideSection(
                "Spectrum",
                items = listOf(
                    item("Bands", "How many slices the sound is cut into (8–64). Also the default number of bars."),
                    item("Lowest / Highest frequency", "The sound range shown. 35 Hz is deep bass, 14 kHz is the top of cymbals."),
                    item("FFT size", "2048 shows bass in finer detail; 1024 reacts a little faster."),
                ),
            ),
            GuideSection(
                "Balance",
                items = listOf(
                    item("Bass / Mids / Treble", "Make one part of the spectrum bigger or smaller."),
                    item("Tilt", "Music has less energy in the highs; tilt lifts them so the treble side moves as much as the bass side."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ beat

    val beat = Guide(
        key = "beat",
        title = "Beat",
        intro = "Beats are found by watching for sudden jumps of energy in the bass (the kick drum). They drive the pulse, ring bounce and spin, filter hits, shake and haptics.",
        sections = listOf(
            GuideSection(
                "Detection",
                items = listOf(
                    item("Sensitivity", "Higher finds more beats (also softer ones); lower only catches clear, strong kicks."),
                    item("Minimum gap", "Shortest time between two beats, so one kick isn't counted twice. Raise it if things fire too often."),
                    item("Ripple strength", "How strong beat effects are overall (pulse brightness and friends)."),
                ),
            ),
            GuideSection(
                "Kick range",
                items = listOf(
                    item("From / To", "The frequency range watched for kicks. 30–180 Hz fits most music; raise To for songs with a punchy, higher kick."),
                    item("Check it", "Test lab → Kick only shows the beat light flashing on each kick."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ room

    val room = Guide(
        key = "room",
        title = "Room",
        intro = "The room fills the whole screen with one color and plays the visuals on top, bigger than ever. Use it to just enjoy the visuals, as a light show on a TV or tablet, or as a green screen: record the screen, then remove the color in a video editor (CapCut, Premiere, DaVinci, OBS) to put the visuals over your own video.",
        sections = listOf(
            GuideSection(
                "Using the room",
                items = listOf(
                    item("Open it", "Home → Try it → Room."),
                    item("Show / hide controls", "Tap anywhere on the screen."),
                    item("Leave", "Exit in the controls, or the back gesture."),
                    item("Status and navigation bars", "Hidden while you're in the room. Swipe from the edge to see them for a moment."),
                    item("Screen stays on", "The phone won't sleep while the room is open."),
                    item("The overlay", "If the visualizer is running over other apps, it hides while the room is open so nothing is drawn twice."),
                ),
            ),
            GuideSection(
                "Controls",
                items = listOf(
                    item("Background", "Green screen (#00B140, the standard key color), Bright green, Blue screen, Magenta, Black, White, Grey, or Custom to pick any color."),
                    item("Preset ‹ ›", "Switch presets without leaving."),
                    item("Screen filter", "Draw the preset's filter in the room. Keep it off for keying: filters add lines and shade to the background."),
                    item("Song & preset name", "Shows them in the top corner."),
                    item("Demo beat", "A built-in beat when no music is playing."),
                ),
            ),
            GuideSection(
                "Green screen tips",
                items = listOf(
                    item("Pick a key color the visuals don't use", "Green background → use red, white, blue or pink visuals. Blue background → red, yellow, white. Avoid green visuals on green."),
                    item("Keep glow moderate", "A big glow blends into the background and keys out softly. That can look great, or wash out; try both."),
                    item("Turn off Pulse Flash and Vignette", "They tint the whole background on beats, which breaks the key."),
                    item("Record", "Use Android's screen recorder (Quick Settings → Screen record) with audio, then key the color out in your editor."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ test lab

    val lab = Guide(
        key = "lab",
        title = "Test lab",
        intro = "Try presets with your own songs or special test sounds, through the real analyzer, with a big preview and live meters.",
        sections = listOf(
            GuideSection(
                "On this page",
                items = listOf(
                    item("Preview and meters", "BASS, MID, HIGH and ALL show the live levels; BEAT lights on each detected beat."),
                    item("‹ preset ›", "Step through presets while the music keeps playing."),
                    item("Pick a song", "Plays an audio file saved on the phone, perfectly in sync. Pause, Stop and the seek bar control it."),
                    item("What's playing on the phone", "Goes back to the normal source (Spotify, YouTube…)."),
                    item("Test sounds", "Each one tests one thing: Kick only (beats and bass), Bassline, Chords (mids), Hi-hats (treble), Sweep (a tone gliding across all bars), Build & drop (quiet to loud) and Silence (the idle look)."),
                    item("Show over other apps", "Keeps the test playing so you can open any app and see the overlay for real."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ presets

    val presets = Guide(
        key = "presets",
        title = "Presets",
        intro = "A preset is a whole look: layers, colors, motion, beat, filter and shake. Haptics and behavior stay the same for every preset.",
        sections = listOf(
            GuideSection(
                "Using presets",
                items = listOf(
                    item("Tap a preset", "Switches to it."),
                    item("Auto-save", "Any change you make saves into the preset you're on. Built-ins remember your edits and show \"edited\"."),
                    item("Reset", "Puts an edited built-in back to how it came."),
                    item("+ Create", "The step-by-step creator (it has its own guide)."),
                    item("Save a copy", "Saves the current look as a new preset under Mine."),
                    item("⋮ menu", "Rename, Reset to original, Duplicate, Export file, Copy JSON, Delete (your own presets only)."),
                ),
            ),
            GuideSection(
                "Share",
                items = listOf(
                    item("Import from file", "Loads presets from a .json file."),
                    item("Paste preset text", "Paste JSON someone sent you."),
                    item("Export all to a file", "Backs up all your presets in one file."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ haptics, shake

    val haptics = Guide(
        key = "haptics",
        title = "Haptics",
        intro = "Feel the kicks: the phone vibrates on strong beats. It's one setting for all presets, so switching presets never starts vibrating by surprise.",
        sections = listOf(
            GuideSection(
                "Settings",
                items = listOf(
                    item("Vibrate on kicks", "On or off."),
                    item("Intensity", "How strong each vibration is."),
                    item("Only beats stronger than", "Weaker beats don't vibrate. Raise it so only the real kicks get through."),
                    item("Minimum gap", "Shortest time between two vibrations, so it never buzzes nonstop."),
                    item("Pattern", "Tap (crisp), Kick (deeper), Sharp, Soft, Double, Rumble."),
                    item("Try it", "Feel the pattern right now."),
                    item("Feel nothing?", "Phone Settings → Sound & vibration → Vibration → Media must be on."),
                ),
            ),
        ),
    )

    val shake = Guide(
        key = "shake",
        title = "Shake",
        intro = "A fake screen shake on kicks: the visuals jump a little, with a colorful flash along the edge. Other apps can't really be shaken, so only the visuals move.",
        sections = listOf(
            GuideSection(
                "Settings",
                items = listOf(
                    item("Shake on kicks", "On or off (part of the preset)."),
                    item("Strength", "How far the visuals jump."),
                    item("Duration", "How long each shake lasts."),
                    item("Only beats stronger than", "Weaker beats don't shake."),
                    item("Edge flash", "A brief flash along the edge glow."),
                    item("Color split", "How much the flash splits into red, green and blue."),
                    item("Vibrate with it", "Vibrates with each shake even when Haptics are off."),
                    item("Shake the whole preview", "In the app preview, shake the whole simulated screen for real."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ audio, behavior, performance, setup

    val audio = Guide(
        key = "audio",
        title = "Audio source",
        intro = "Where EQV listens. Nothing is recorded or sent anywhere.",
        sections = listOf(
            GuideSection(
                "Sources",
                items = listOf(
                    item("Auto", "Best choice: the system visualizer, falling back to the microphone if it stays silent."),
                    item("Visualizer", "Android's built-in music analyzer. Works with every player, a bit less detail."),
                    item("HQ capture", "Full quality from the playing app. Asks permission every time and shows a red chip; Spotify blocks it."),
                    item("Mic", "Hears the room through the microphone. Works anywhere, even with music from another device."),
                    item("Demo", "A built-in beat, no music needed."),
                    item("Now", "Which source is really in use, and why others were skipped."),
                ),
            ),
        ),
    )

    val behavior = Guide(
        key = "behavior",
        title = "Behavior",
        intro = "When the visualizer appears and hides. These settings are the same for every preset.",
        sections = listOf(
            GuideSection(
                "Start & stop",
                items = listOf(
                    item("Start with music", "Appears when music plays and leaves when it stops. Needs notification access."),
                    item("Stay after music stops", "Seconds to wait before hiding, so it doesn't blink off between songs."),
                    item("Silence fallback", "If the source hears nothing while music plays, it tries the next source."),
                ),
            ),
            GuideSection(
                "Which players start it",
                items = listOf(
                    item("All apps / Only listed / All except listed", "Choose which music apps can start the visualizer, then pick them in Listed players."),
                ),
            ),
            GuideSection(
                "Hide",
                items = listOf(
                    item("In fullscreen", "Hides during videos and games."),
                    item("In these apps", "Hides while one of the chosen apps is open (needs Usage access)."),
                ),
            ),
            GuideSection(
                "Pause",
                items = listOf(
                    item("Screen off", "Stops when the screen turns off (saves battery)."),
                    item("During calls", "Stops during phone calls."),
                    item("Battery saver below", "Stops below this battery % when not charging. 0 = never."),
                ),
            ),
            GuideSection(
                "Sync with sound",
                items = listOf(
                    item("Phone speaker / Wired / Bluetooth", "Delays the visuals to match what you hear. Bluetooth headphones lag, usually 150–250 ms. If the beat light flashes before you hear the kick, raise it."),
                ),
            ),
        ),
    )

    val performance = Guide(
        key = "performance",
        title = "Performance",
        intro = "Smoothness versus battery.",
        sections = listOf(
            GuideSection(
                "Display",
                items = listOf(
                    item("Frame rate", "30, 60, 90 or 120 frames per second. 60 is smooth and saves battery; 120 is the smoothest."),
                    item("Quality", "Low skips the glow; Medium uses a smaller glow; High is full glow."),
                    item("Overlay opacity", "How see-through all visuals are over other apps. Android caps overlays at 80% so touches still pass through."),
                ),
            ),
            GuideSection(
                "Debug",
                items = listOf(
                    item("FPS counter", "Shows frames per second on the overlay."),
                    item("Debug page", "Adds a Debug page to Home."),
                    item("Debug info on overlay", "Source, levels and beat data on screen."),
                ),
            ),
        ),
    )

    val setup = Guide(
        key = "setup",
        title = "Setup",
        intro = "Each permission and why it's needed. Nothing leaves your phone.",
        sections = listOf(
            GuideSection(
                "Needed",
                items = listOf(
                    item("Display over other apps", "Draws the visuals on top of every app; touches pass through."),
                    item("Microphone", "Android requires it for the system audio visualizer. EQV never records."),
                    item("Notifications", "The on/off and next-preset buttons in the notification."),
                    item("Notification access", "Sees what's playing: start with music, album colors, song name."),
                    item("\"Restricted setting\"?", "Apps installed from a browser need one extra step: tap Allow once, then App info → ⋮ → Allow restricted settings, then Allow again."),
                ),
            ),
            GuideSection(
                "Optional",
                items = listOf(
                    item("Usage access", "Only for hiding EQV in chosen apps."),
                    item("Battery: unrestricted", "Keeps auto-start instant."),
                    item("Quick Settings tiles", "Pull down Quick Settings → pencil → drag in \"EQV\" and \"EQV preset\"."),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------------------ create step tips

    /** The short "what to do here" card at the top of each Create step, by step index. */
    val createTips: List<List<String>> = listOf(
        listOf(
            "Tap Blank to start from nothing, or any preset to start from it.",
            "Watch the preview: it changes as soon as you tap.",
            "Press Next when it looks close to what you want.",
            "Need more help? Tap ? at the top for the full guide to every step.",
        ),
        listOf(
            "Switch on the shapes you want; combine as many as you like.",
            "Tap the chips under each one to change its style.",
            "Try the radial ring with Filled or Segments style for a different look.",
        ),
        listOf(
            "Tap a palette: it colors every layer at once.",
            "Then pick a layer under Fine-tune to change just that one.",
            "Glow makes it softer and dreamier; Opacity makes it see-through.",
        ),
        listOf(
            "Tap a feel: Snappy, Punchy, Smooth or Dreamy.",
            "Rise = how fast bars jump up. Fall = how slowly they come down.",
            "Shapes barely moving? Turn up Sensitivity.",
        ),
        listOf(
            "Optional: pick a screen filter, or None.",
            "Follow the music and Punch on hits make it react to the song.",
            "On the beat adds an extra hit on each kick.",
        ),
        listOf(
            "Give your preset a name.",
            "Check the summary; Back changes anything.",
            "Save preset: it's added under Mine and switched on.",
        ),
    )
}
