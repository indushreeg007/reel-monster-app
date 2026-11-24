package com.reelmonster.app.monster

object Dialogues {
    private val milestones = mapOf(
        1 to "Yayyy! Just one? You're doing great! 🥹",
        5 to "Bestie, we're just getting started?",
        10 to "ONE MORE? That's what you said 10 reels ago. 💀",
        20 to "Bro, do you even remember why you opened this app?",
        30 to "My legs are tired from watching with you.",
        50 to "PLEASE. TOUCH. GRASS. 🌱",
        75 to "I was born to be cute, not to witness this.",
        100 to "Congratulations! You've officially adopted me as your full-time scrolling companion."
    )
    private val pools: List<Pair<IntRange, List<String>>> = listOf(
        0..5 to listOf("Ooh, a new one! 👀", "Okay okay, just a quick peek.", "I'm stretching. Take your time~", "Tiny me is watching with you!"),
        6..15 to listOf("Just a few more, right? Right?", "I'm getting comfy on this screen.", "That one was cute, I'll allow it.", "Your thumb is warming up 🔥", "Snack break soon?"),
        16..30 to listOf("I've grown a little just watching this.", "Do your eyes feel okay? Mine don't.", "Reel #? I lost count. No I didn't.", "Remember water? It exists. 💧", "We said five minutes. 👁️👄👁️"),
        31..50 to listOf("I'm getting chubby from all this content.", "Your thumb needs a raise.", "Plot twist: the algorithm is winning.", "I can feel my corners getting round-er.", "Blink. Just once. For me."),
        51..75 to listOf("This is a lot of reels, bestie.", "I'm being DRAMATIC because it's deserved.", "Look at me. Look at how big I am. LOOK.", "The grass misses you. 🌱", "I need a nap, and so do you."),
        76..99 to listOf("*yawns in monster* 😮‍💨", "I'm exhausted. You're exhausted. Why?", "Giant sleepy me says: put the phone down.", "My eyelids weigh 40 kilos.", "We could've learned a language by now."),
        100..Int.MAX_VALUE to listOf("I've stopped counting. I'm just here now.", "Legend status: unlocked. Sleep status: locked.", "Please. Water. Sunlight. Anything. 🥲", "I'm not mad, just ... giant and tired.")
    )
    val welcomeBack = listOf("You took a break! I'm so proud of you 🥹", "Welcome back! Did you drink water?", "Look at you, being all disciplined 🌟")
    val nudges = listOf("Bestie, let's drink some water. 💧", "Your eyes need a vacation too!", "Let's take a 5-minute break together?", "Stretch time! Arms up, big monster energy 🙆", "Look at something far away for 20 seconds 👀")

    private val recent = ArrayDeque<String>()
    fun isMilestone(n: Int) = n in milestones

    fun pick(n: Int): String {
        val m = milestones[n]
        if (m != null) return remember(m)
        val pool = pools.first { n in it.first }.second
        val fresh = pool.filter { it !in recent }.ifEmpty { pool }
        return remember(fresh.random())
    }
    fun welcome() = welcomeBack.random()
    private fun remember(s: String): String { recent.addLast(s); while (recent.size > 6) recent.removeFirst(); return s }
}
