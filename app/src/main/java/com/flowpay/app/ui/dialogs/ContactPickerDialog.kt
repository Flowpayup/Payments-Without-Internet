package com.flowpay.app.ui.dialogs

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.app.ui.theme.LocalFlowPayAccentTheme
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
 * Contact picker dialog that displays a searchable list of contacts
 * Uses FlowPay's dark theme styling
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

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f),
        containerColor = Color(0xFF1A1A1A),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select Contact",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF8A8A8A)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { 
                        Text("Search contacts...", color = Color(0xFF6A6A6A)) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF8A8A8A)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4A4A4A),
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                        cursorColor = LocalFlowPayAccentTheme.current.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
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
                        CircularProgressIndicator(color = LocalFlowPayAccentTheme.current.accent)
                    }
                } else if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No contacts found" 
                                   else "No matches for \"$searchQuery\"",
                            color = Color(0xFF6A6A6A),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            ContactItem(
                                contact = contact,
                                onClick = {
                                    onContactSelected(contact)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * Individual contact item in the list
 */
@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contact icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = LocalFlowPayAccentTheme.current.accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = LocalFlowPayAccentTheme.current.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Contact details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = contact.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    color = Color(0xFF8A8A8A),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

