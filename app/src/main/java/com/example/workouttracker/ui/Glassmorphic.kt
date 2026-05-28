package com.example.workouttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f // rough heuristic or rely on MaterialTheme
    val bgColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.5f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
        content = content
    )
}

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F0C29),
                Color(0xFF302B63),
                Color(0xFF0F0C29)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFDFBFB),
                Color(0xFFEBEDEE),
                Color(0xFFFDFBFB)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
        content = content
    )
}

val motivationQuotes = listOf(
    Triple("Consistency is the key", "Small steps every day lead to big changes.", "🏆"),
    Triple("No pain, no gain", "Push through the burn to see results.", "🔥"),
    Triple("Believe in yourself", "You are stronger than you think.", "💪"),
    Triple("Sweat is magic", "Cover yourself in it daily.", "💦"),
    Triple("Stay focused", "Keep your eyes on the prize.", "🎯"),
    Triple("Don't quit", "You're already in pain. Get a reward from it.", "🛑"),
    Triple("Push harder", "Than yesterday if you want a different tomorrow.", "🚀"),
    Triple("Make it happen", "Shock everyone.", "⚡"),
    Triple("Be a visionary", "Work hard in silence, let success make noise.", "🤫"),
    Triple("Train insane", "Or remain the same.", "😤"),
    Triple("Your body hears", "Everything your mind says. Stay positive.", "🧠"),
    Triple("Excuses burn zero calories", "Get up and get moving.", "🏃"),
    Triple("It never gets easier", "You just get stronger.", "🏋️"),
    Triple("Strive for progress", "Not perfection.", "📈"),
    Triple("Wake up determined", "Go to bed satisfied.", "😌"),
    Triple("Prove them wrong", "Show them what you are made of.", "👊"),
    Triple("Fall in love", "With taking care of your body.", "❤️"),
    Triple("Sore today", "Strong tomorrow.", "💪"),
    Triple("You can do it", "Quitting won't speed it up.", "⏳"),
    Triple("Don't stop", "When you're tired. Stop when you're done.", "🏁"),
    Triple("Do it for you", "Not for them.", "👤"),
    Triple("Doubt kills dreams", "More than failure ever will.", "☁️"),
    Triple("Hustle for muscle", "Earn your body.", "💰"),
    Triple("Focus on goals", "The rest is just noise.", "🎧"),
    Triple("Mind over matter", "Your mind gives up before your body does.", "🧘"),
    Triple("Be stronger", "Than your strongest excuse.", "🛡️"),
    Triple("Every workout counts", "Even the bad ones.", "💯"),
    Triple("Keep going", "You did not wake up to be mediocre.", "🌅"),
    Triple("Trust the process", "Great things take time.", "🕰️"),
    Triple("Stop wishing", "Start doing.", "✨"),
    Triple("Action is key", "To all success.", "🗝️"),
    Triple("Push yourself", "No one else is going to do it for you.", "🤝"),
    Triple("Success starts", "With self-discipline.", "📏"),
    Triple("Embrace the struggle", "It's part of the journey.", "⛰️"),
    Triple("Be relentless", "In the pursuit of your goals.", "🐅"),
    Triple("Rise and grind", "Make today count.", "☕"),
    Triple("Find your fire", "And let it burn.", "🔥"),
    Triple("You are your limit", "Break past your boundaries.", "🚧"),
    Triple("Sweat, smile, repeat", "The formula for success.", "😊"),
    Triple("Commit to be fit", "It's a lifestyle.", "🥗"),
    Triple("Train like a beast", "Look like a beauty.", "🦁"),
    Triple("Stay dedicated", "Make time for it.", "⏱️"),
    Triple("Conquer yourself", "The hardest battle is your mind.", "⚔️"),
    Triple("Fuel your passion", "Let it drive you forward.", "⛽"),
    Triple("Defy the odds", "Show the world what you can do.", "🌍"),
    Triple("Never settle", "Always strive for more.", "⬆️"),
    Triple("Challenge yourself", "Growth happens outside your comfort zone.", "🌱"),
    Triple("Keep pushing", "You're closer than you think.", "👀"),
    Triple("Unleash potential", "Discover what you're capable of.", "🔓"),
    Triple("Sweat is fat crying", "Make it pour.", "🌧️")
)

@Composable
fun MotivationCard(
    modifier: Modifier = Modifier
) {
    val quote = remember { motivationQuotes.random() }
    val title = quote.first
    val subtitle = quote.second
    val icon = quote.third
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
    val titleColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor
                )
            }
            Text(text = icon, fontSize = 48.sp)
        }
    }
}
