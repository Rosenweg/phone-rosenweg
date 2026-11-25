package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isConference
import org.fossify.phone.models.CallContact

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val number = uri.substringAfter("tel:")
            ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
                val numbersToContactIDMap = HashMap<String, Int>()
                contactsWithMultipleNumbers.forEach { contact ->
                    contact.phoneNumbers.forEach { phoneNumber ->
                        numbersToContactIDMap[phoneNumber.value] = contact.contactId
                        numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
                    }
                }

                callContact.number = if (context.config.formatPhoneNumbers) {
                    number.formatPhoneNumber()
                } else {
                    number
                }

                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.getNameToDisplay()
                    callContact.photoUri = contact.photoUri

                    if (contact.phoneNumbers.size > 1) {
                        val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                        if (specificPhoneNumber != null) {
                            callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                        }
                    }
                } else {
                    // Rosenweg: nicht im Adressbuch -> den Namen nehmen, den die
                    // Telefonanlage mitschickt (CNAP / SIP-Anzeigename), sonst die Nummer.
                    val cnap = try {
                        call?.details?.callerDisplayName?.trim()
                    } catch (e: Exception) {
                        null
                    }
                    callContact.name = if (!cnap.isNullOrEmpty() && cnap != number) cnap else callContact.number
                }

                callback(callContact)
            }
        } else {
            // Rosenweg: SIP-Absender (sip:anonymous, sip:name@host) - Fossify rief hier den
            // callback nie auf, dann gab es weder Benachrichtigung noch Anruffenster.
            val benutzer = uri.substringAfter(":").substringBefore("@").substringBefore(";")
            val cnap = try {
                call?.details?.callerDisplayName?.trim()
            } catch (e: Exception) {
                null
            }
            callContact.number = if (benutzer.equals("anonymous", true)) "" else benutzer
            callContact.name = when {
                !cnap.isNullOrEmpty() -> cnap
                benutzer.isEmpty() || benutzer.equals("anonymous", true) -> "Unbekannt"
                else -> benutzer
            }
            callback(callContact)
        }
    }
}
