package ink.gim.lfw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF1333CB),
  secondary = Color(0xFF3B52C2),
  tertiary = Color(0xFF2344E1),
  background = Color(0xFF000000)
)

@Composable
fun LFWAppTheme(
  content: @Composable () -> Unit
) {

  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}