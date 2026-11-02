package com.nakamura196.kotenocr.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.KotenStamp
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import kotlinx.coroutines.delay

/**
 * First-launch / model-loading screen (`LaunchView.swift` port).
 * Displays the vermilion "典" seal stamp (animated on first launch), Mincho title, and tagline.
 */
@Composable
fun LaunchScreen(
    animateStamp: Boolean,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val stampScale = remember { Animatable(if (animateStamp) 1.35f else 1.0f) }
    val stampAlpha = remember { Animatable(if (animateStamp) 0f else 1f) }
    val textAlpha = remember { Animatable(if (animateStamp) 0f else 1f) }

    LaunchedEffect(animateStamp) {
        if (animateStamp) {
            delay(80)
            stampAlpha.animateTo(1f, tween(200))
            stampScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            textAlpha.animateTo(1f, tween(350))
        }
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KotenStamp(
                    size = 96.dp,
                    modifier = Modifier
                        .scale(stampScale.value)
                        .rotate(-1.5f)
                        .alpha(stampAlpha.value)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "KotenOCR",
                    style = minchoStyle(size = 34.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.alpha(textAlpha.value)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.brand_tagline),
                    fontSize = 15.sp,
                    color = KotenColor.inkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(textAlpha.value)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        color = KotenColor.shu,
                        textAlign = TextAlign.Center
                    )
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        KotenProminentButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.ocr_error_retake))
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(bottom = 36.dp)
                        .alpha(textAlpha.value)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = KotenColor.shu,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.loading_models),
                        fontSize = 13.sp,
                        color = KotenColor.inkSecondary
                    )
                }
            }
        }
    }
}
