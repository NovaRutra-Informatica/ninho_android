package app.ninho.android

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal val NinhoBodyFont = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal), Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold), Font(R.font.dm_sans_bold, FontWeight.Bold),
)
internal val NinhoDisplayFont = FontFamily(Font(R.font.fraunces_medium, FontWeight.Medium))
private val baseTypography = Typography()
internal val NinhoTypography = Typography(
    displayLarge = baseTypography.displayLarge.copy(fontFamily = NinhoDisplayFont),
    displayMedium = baseTypography.displayMedium.copy(fontFamily = NinhoDisplayFont),
    displaySmall = baseTypography.displaySmall.copy(fontFamily = NinhoDisplayFont),
    headlineLarge = baseTypography.headlineLarge.copy(fontFamily = NinhoDisplayFont),
    headlineMedium = baseTypography.headlineMedium.copy(fontFamily = NinhoDisplayFont),
    headlineSmall = baseTypography.headlineSmall.copy(fontFamily = NinhoDisplayFont),
    titleLarge = baseTypography.titleLarge.copy(fontFamily = NinhoBodyFont),
    titleMedium = baseTypography.titleMedium.copy(fontFamily = NinhoBodyFont),
    titleSmall = baseTypography.titleSmall.copy(fontFamily = NinhoBodyFont),
    bodyLarge = baseTypography.bodyLarge.copy(fontFamily = NinhoBodyFont),
    bodyMedium = baseTypography.bodyMedium.copy(fontFamily = NinhoBodyFont),
    bodySmall = baseTypography.bodySmall.copy(fontFamily = NinhoBodyFont),
    labelLarge = baseTypography.labelLarge.copy(fontFamily = NinhoBodyFont),
    labelMedium = baseTypography.labelMedium.copy(fontFamily = NinhoBodyFont),
    labelSmall = baseTypography.labelSmall.copy(fontFamily = NinhoBodyFont),
)
