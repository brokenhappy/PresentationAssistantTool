package com.woutwerkman.pa.ui

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
