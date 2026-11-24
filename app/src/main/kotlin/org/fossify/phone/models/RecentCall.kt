package org.fossify.phone.models

import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.phone.extensions.getDayCode

/**
 * Used at displaying recent calls.
 * For contacts with multiple numbers specify the number and type
 */
@kotlinx.serialization.Serializable
data class RecentCall(
    val id: Int,
    val phoneNumber: String,
    val name: String,
    val photoUri: String,
    val startTS: Long,
    val duration: Int,
    val type: Int,
    val simID: Int,
    val simColor: Int,
    val specificNumber: String,
    val specificType: String,
    val isUnknownNumber: Boolean,
    val groupedCalls: MutableList<RecentCall>? = null,
    /** Rosenweg: Eintrag aus der Telefonanlage (Call-ID dort), 0 = Android-Anrufprotokoll. */
    val anlageId: Int = 0,
    /** Rosenweg: Zusammenfassung einer Voicemail (falls vorhanden). */
    val voicemailText: String = "",
) : CallLogItem() {
    val dayCode = startTS.getDayCode()

    fun doesContainPhoneNumber(text: String): Boolean {
        return if (text.toLongOrNull() != null) {
            val normalizedText = text.normalizePhoneNumber()
            PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
                    phoneNumber.contains(text) ||
                    phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
                    phoneNumber.contains(normalizedText)
        } else {
            false
        }
    }
}
