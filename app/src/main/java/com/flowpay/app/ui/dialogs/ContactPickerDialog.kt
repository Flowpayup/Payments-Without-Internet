// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.dialogs

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.R
import com.flowpay.app.ui.components.LedgerDragHandle
import com.flowpay.app.ui.theme.FlowpayDarkGray
import com.flowpay.app.ui.theme.FlowpayInkWarm
import com.flowpay.app.ui.theme.FlowpayLedgerRule
import com.flowpay.app.ui.theme.FlowpayMonoStyle
import com.flowpay.app.ui.theme.FlowpayTextGray
import com.flowpay.app.ui.theme.FlowpayTextLightGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing a contact with phone number
 */
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String
)

/**
 * Contact picker — a bottom sheet with a searchable, ledger-styled list of
 * contacts, matching the same sheet pattern as Pay Contact and Transaction
 * Detail rather than a centered dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var filteredContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load contacts when dialog opens
    LaunchedEffect(Unit) {
        val loadedContacts = try {
            loadContacts(context.contentResolver)
        } catch (e: SecurityException) {
            Log.e("ContactPicker", "Contacts permission denied or restricted", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("ContactPicker", "Failed to load contacts", e)
            emptyList()
        }
        contacts = loadedContacts
        filteredContacts = loadedContacts
        isLoading = false
    }

    // Filter contacts based on search query
    LaunchedEffect(searchQuery) {
        filteredContacts = if (searchQuery.isEmpty()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                    contact.phoneNumber.contains(searchQuery)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FlowpayDarkGray,
        dragHandle = { LedgerDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Text(
                stringResource(R.string.contacts_select_title),
                color = FlowpayInkWarm,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                placeholder = {
                    Text(stringResource(R.string.contacts_search_hint), color = FlowpayTextGray)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = FlowpayTextLightGray
                    )
                },
                shape = com.flowpay.app.ui.theme.FlowpayRecordShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FlowpayInkWarm,
                    unfocusedTextColor = FlowpayInkWarm,
                    focusedBorderColor = FlowpayInkWarm,
                    unfocusedBorderColor = FlowpayLedgerRule,
                    cursorColor = FlowpayInkWarm,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                singleLine = true
            )

            // Contacts list
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FlowpayLedgerRule)
                }
            } else if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) {
                            "No contacts found"
                        } else {
                            "No matches for \"$searchQuery\""
                        },
                        color = FlowpayTextGray,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(filteredContacts) { index, contact ->
                        ContactItem(
                            contact = contact,
                            onClick = {
                                onContactSelected(contact)
                                onDismiss()
                            },
                            showDivider = index < filteredContacts.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual contact item — a ledger row (plain ink initial, hairline
 * divider) rather than a rounded card with a colored circular avatar.
 */
@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = contact.name.first().uppercase(),
                color = FlowpayLedgerRule,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    color = FlowpayInkWarm,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    style = FlowpayMonoStyle.copy(fontSize = 12.sp, color = FlowpayTextLightGray)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = FlowpayLedgerRule.copy(alpha = 0.4f))
        }
    }
}

/**
 * Load contacts from the device's contact database
 * Returns a list of contacts with 10-digit phone numbers
 */
suspend fun loadContacts(contentResolver: ContentResolver): List<Contact> = withContext(Dispatchers.IO) {
    val contactsList = mutableListOf<Contact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    val cursor: Cursor? = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    )

    cursor?.use {
        val idColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        if (idColumn < 0 || nameColumn < 0 || numberColumn < 0) {
            return@withContext emptyList()
        }

        while (it.moveToNext()) {
            val id = it.getString(idColumn)
            val name = it.getString(nameColumn) ?: "Unknown"
            val number = it.getString(numberColumn) ?: ""

            // Clean the phone number (remove spaces, dashes, brackets, etc.)
            val cleanedNumber = number.replace(Regex("[^0-9+]"), "")
                .replace("+91", "") // Remove country code
                .takeLast(10) // Get last 10 digits for Indian numbers

            if (cleanedNumber.length == 10) {
                contactsList.add(Contact(id, name, cleanedNumber))
            }
        }
    }

    // Remove duplicates based on phone number
    contactsList.distinctBy { it.phoneNumber }
}
