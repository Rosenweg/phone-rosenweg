package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.initiateCall
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.launchCallIntent
import org.fossify.commons.extensions.openFullScreenIntentSettings
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.BuildConfig
import org.fossify.phone.activities.DialerActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.dialogs.SelectSIMDialog
import org.fossify.phone.helpers.Betriebsart
import org.fossify.phone.helpers.EigenerAnruf
import org.fossify.phone.helpers.PersoenlichesKonto

/**
 * Rosenweg (26.11.2026): auf privaten Handys (ohne Rosenweg Geraet) ruft Rosenweg Telefon nur
 * ueber die Rosenweg-Anlage an, ganz in der App (EigenerAnruf) - nie ueber Telecom/Mobilfunk.
 * Notrufe oeffnen die Telefon-App des Handys. true = erledigt (auch mit Meldung).
 */
fun BaseSimpleActivity.rosenwegPrivatAnrufen(recipient: String, name: String? = null): Boolean {
    if (!Betriebsart.privat(this)) return false
    if (EigenerAnruf.istNotruf(this, recipient)) {
        EigenerAnruf.handyTelefonOeffnen(this, recipient)
        return true
    }
    handlePermission(org.fossify.commons.helpers.PERMISSION_RECORD_AUDIO) { mikrofon ->
        if (!mikrofon) {
            toast("Rosenweg Telefon braucht das Mikrofon zum Telefonieren")
            return@handlePermission
        }
        if (!EigenerAnruf.waehlen(this, recipient, name)) {
            val text = if (PersoenlichesKonto.privatNummer(this) == null && !PersoenlichesKonto.manuellDa(this)) {
                "Rosenweg Telefon ist nicht angemeldet. Bitte im Menü „Mit Rosenweg anmelden“."
            } else {
                "Rosenweg Telefon ist gerade nicht mit der Rosenweg-Anlage verbunden (kein Netz?). " +
                    "Über die Telefon-App des Handys anrufen?"
            }
            org.fossify.commons.dialogs.ConfirmationDialog(
                this, message = text,
                positive = if (text.endsWith("?")) R.string.yes else R.string.ok,
                negative = if (text.endsWith("?")) R.string.no else 0
            ) {
                if (text.endsWith("?")) EigenerAnruf.handyTelefonOeffnen(this, recipient)
            }
        }
    }
    return true
}

fun SimpleActivity.startCallIntent(
    recipient: String,
    forceSimSelector: Boolean = false
) {
    if (rosenwegPrivatAnrufen(recipient)) return
    if (isDefaultDialer()) {
        getHandleToUse(
            intent = null,
            phoneNumber = recipient,
            forceSimSelector = forceSimSelector
        ) { handle ->
            launchCallIntent(recipient, handle)
        }
    } else {
        launchCallIntent(recipient, null)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(
    recipient: String,
    name: String,
    forceSimSelector: Boolean = false
) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            startCallIntent(recipient, forceSimSelector)
        }
    } else {
        startCallIntent(recipient, forceSimSelector)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(contact: Contact) {
    val anrufen: (String) -> Unit = {
        if (!rosenwegPrivatAnrufen(it, contact.getNameToDisplay())) launchCallIntent(it)
    }
    if (config.showCallConfirmation) {
        CallConfirmationDialog(
            activity = this,
            callee = contact.getNameToDisplay()
        ) {
            initiateCall(contact, anrufen)
        }
    } else {
        initiateCall(contact, anrufen)
    }
}

fun BaseSimpleActivity.callContactWithSim(
    recipient: String,
    useMainSIM: Boolean
) {
    if (rosenwegPrivatAnrufen(recipient)) return
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        val wantedSimIndex = if (useMainSIM) 0 else 1
        val handle = getAvailableSIMCardLabels()
            .sortedBy { it.id }
            .getOrNull(wantedSimIndex)?.handle
        launchCallIntent(recipient, handle)
    }
}

fun BaseSimpleActivity.callContactWithSimWithConfirmationCheck(
    recipient: String,
    name: String,
    useMainSIM: Boolean
) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            callContactWithSim(recipient, useMainSIM)
        }
    } else {
        callContactWithSim(recipient, useMainSIM)
    }
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(
    intent: Intent?,
    phoneNumber: String,
    forceSimSelector: Boolean = false,
    callback: (handle: PhoneAccountHandle?) -> Unit
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        if (it) {
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                forceSimSelector -> showSelectSimDialog(phoneNumber, callback)
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> {
                    callback(intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!)
                }

                config.getCustomSIM(phoneNumber) != null -> {
                    callback(config.getCustomSIM(phoneNumber))
                }

                // Rosenweg: persoenliches Konto der angemeldeten Person, wenn registriert
                // (nie fuer Notrufe — die gehen ueber das Modem)
                PersoenlichesKonto.fuerAnruf(this, phoneNumber) != null ->
                    callback(PersoenlichesKonto.fuerAnruf(this, phoneNumber))

                defaultHandle != null -> callback(defaultHandle)
                else -> showSelectSimDialog(phoneNumber, callback)
            }
        }
    }
}

fun SimpleActivity.showSelectSimDialog(
    phoneNumber: String,
    callback: (handle: PhoneAccountHandle?) -> Unit
) = SelectSIMDialog(
    activity = this,
    phoneNumber = phoneNumber,
    onDismiss = {
        if (this is DialerActivity) {
            finish()
        }
    }
) { handle ->
    callback(handle)
}

fun SimpleActivity.handleFullScreenNotificationsPermission(callback: (granted: Boolean) -> Unit) {
    handleNotificationPermission { granted ->
        if (granted) {
            if (canUseFullScreenIntent()) {
                callback(true)
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = R.string.allow_full_screen_notifications_incoming_calls,
                    positiveActionCallback = {
                        @SuppressLint("NewApi")
                        openFullScreenIntentSettings(BuildConfig.APPLICATION_ID)
                    },
                    negativeActionCallback = {
                        callback(false)
                    }
                )
            }
        } else {
            PermissionRequiredDialog(
                activity = this,
                textId = R.string.allow_notifications_incoming_calls,
                positiveActionCallback = {
                    openNotificationSettings()
                },
                negativeActionCallback = {
                    callback(false)
                }
            )
        }
    }
}
