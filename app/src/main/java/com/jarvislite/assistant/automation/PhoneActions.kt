package com.jarvislite.assistant.automation

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.jarvislite.assistant.CameraCapture
import com.jarvislite.assistant.JarvisAccessibilityService
import com.jarvislite.assistant.JarvisApp
import com.jarvislite.assistant.MemoryStore
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The single place where a "tool call" from Gemini turns into a real action
 * on the phone. Screen actions rely on JarvisAccessibilityService being
 * enabled; photo/call/memory actions work independently of it.
 */
object PhoneActions {

    /** Set by JarvisForegroundService once it starts, since taking a photo needs a Lifecycle. */
    var cameraCapture: CameraCapture? = null

    /** Set by JarvisForegroundService - lets an action push an image straight into the live conversation. */
    var imageSender: ((ByteArray) -> Unit)? = null

    private val commonApps = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps"
    )

    fun execute(functionName: String, args: JSONObject): String {
        return try {
            when (functionName) {
                // --- actions that don't need the accessibility service ---
                "take_photo" -> takePhoto(args.optString("camera", "back"))
                "make_phone_call" -> makePhoneCall(args.getString("contact_name"))
                "remember_fact" -> {
                    MemoryStore.remember(JarvisApp.appContext, args.getString("key"), args.getString("value"))
                    "remembered: ${args.getString("key")} = ${args.getString("value")}"
                }
                "forget_fact" -> {
                    MemoryStore.forget(JarvisApp.appContext, args.getString("key"))
                    "forgot ${args.getString("key")}"
                }

                // --- actions that need the accessibility service ---
                else -> withAccessibility { service ->
                    when (functionName) {
                        "open_app" -> openApp(service, args.getString("app_name"))
                        "search_youtube_and_play" -> searchYoutubeAndPlay(service, args.getString("query"))
                        "send_whatsapp_message" -> sendWhatsappMessage(
                            service, args.getString("contact_name"), args.getString("message")
                        )
                        "open_chrome_tabs" -> openChromeTabs(service, args.getString("urls"))
                        "tap_text_on_screen" -> {
                            val ok = service.tapByText(args.getString("text"))
                            if (ok) "tapped" else "could not find that on screen"
                        }
                        "go_back" -> { service.pressBack(); "went back" }
                        else -> "unknown action"
                    }
                }
            }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun withAccessibility(block: (JarvisAccessibilityService) -> String): String {
        val service = JarvisAccessibilityService.instance
            ?: return "Accessibility service is not enabled - ask the user to turn it on in Settings."
        return block(service)
    }

    // ---------------- Camera ----------------

    private fun takePhoto(camera: String): String {
        val capture = cameraCapture ?: return "camera is not ready yet - the assistant needs to be running as the background service"
        val latch = CountDownLatch(1)
        var resultFile: File? = null
        capture.takePhoto(camera.equals("front", ignoreCase = true)) { file ->
            resultFile = file
            latch.countDown()
        }
        latch.await(6, TimeUnit.SECONDS)
        val file = resultFile ?: return "could not take a photo - check camera permission is granted"
        val bytes = file.readBytes()
        imageSender?.invoke(bytes)
        file.delete()
        return "photo taken - here's what I can see"
    }

    // ---------------- Phone calls ----------------

    private fun makePhoneCall(contactName: String): String {
        val context = JarvisApp.appContext
        val number = lookupContactNumber(contactName)
            ?: return "could not find a contact named \"$contactName\" - check Contacts permission is granted"

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "calling $contactName"
    }

    private fun lookupContactNumber(name: String): String? {
        val context = JarvisApp.appContext
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactDisplayName = cursor.getString(nameIdx) ?: continue
                if (contactDisplayName.contains(name, ignoreCase = true)) {
                    return cursor.getString(numIdx)
                }
            }
        }
        return null
    }

    // ---------------- App/screen automation (unchanged) ----------------

    private fun openApp(service: JarvisAccessibilityService, appName: String): String {
        val pkg = commonApps[appName.lowercase()] ?: appName
        return if (service.launchApp(pkg)) "opened $appName" else "could not find app $appName"
    }

    private fun searchYoutubeAndPlay(service: JarvisAccessibilityService, query: String): String {
        service.launchApp(commonApps["youtube"]!!)
        Thread.sleep(1500)
        service.tapByText("Search")
        Thread.sleep(700)
        service.typeText(query)
        Thread.sleep(300)
        service.tapByText("Search")
        Thread.sleep(1200)
        val tapped = service.tapAt(400f, 400f)
        return if (tapped) "playing \"$query\" on YouTube" else "searched YouTube for \"$query\" but could not confirm playback"
    }

    private fun sendWhatsappMessage(service: JarvisAccessibilityService, contact: String, message: String): String {
        service.launchApp(commonApps["whatsapp"]!!)
        Thread.sleep(1500)
        service.tapByText("Search")
        Thread.sleep(700)
        service.typeText(contact)
        Thread.sleep(1000)
        service.tapByText(contact)
        Thread.sleep(1000)
        service.typeText(message)
        Thread.sleep(300)
        val sent = service.tapByText("Send")
        return if (sent) "sent WhatsApp message to $contact" else "opened chat with $contact but could not confirm send - check the screen"
    }

    private fun openChromeTabs(service: JarvisAccessibilityService, urlsCsv: String): String {
        val sites = urlsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        service.launchApp(commonApps["chrome"]!!)
        Thread.sleep(1200)
        for (site in sites) {
            service.tapByText("New tab")
            Thread.sleep(500)
            service.typeText(if (site.startsWith("http")) site else "https://$site")
            Thread.sleep(200)
        }
        return "opened ${sites.size} tab(s) in Chrome: ${sites.joinToString()}"
    }
}
