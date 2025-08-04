package to.bitkit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import to.bitkit.ui.theme.Colors

@Composable
fun SheetDragHandle(
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Colors.White32,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { contentDescription = "Drag handle" }
    ) {
        Box(Modifier.size(width = 32.dp, height = 4.dp))
    }
}
