package app.yadavar.shamsi.ui

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import app.yadavar.shamsi.R

/** Light Lottie animations kept in res/raw: tick, empty list and ringing bell. */
@Composable
fun LottieOnce(@RawRes res: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}

@Composable
fun LottieLoop(@RawRes res: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}

/** The green check that draws itself when a reminder is ticked. */
@Composable
fun TickAnimation(modifier: Modifier = Modifier) = LottieOnce(R.raw.anim_check, modifier)

/** Shown when a list has nothing in it. */
@Composable
fun EmptyAnimation(modifier: Modifier = Modifier) = LottieLoop(R.raw.anim_empty, modifier)

/** The bell of the alarm screen. */
@Composable
fun BellAnimation(modifier: Modifier = Modifier) = LottieLoop(R.raw.anim_bell, modifier)
