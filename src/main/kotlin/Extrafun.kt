import java.text.SimpleDateFormat
import java.util.Date
import java.awt.Desktop
import java.net.URI


fun openWebpage(url: String) {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(URI(url))
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm")
    return sdf.format(Date(timestamp))
}