/*
 * Copyright (C) 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.mobly.snippet.bundled;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.util.ArrayList;

/* Snippet class for operating contacts. */
public class ContactSnippet implements Snippet {

  public static class ContactSnippetException extends Exception {

    ContactSnippetException(String msg) {
      super(msg);
    }
  }

  private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
  private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
  private final AccountManager mAccountManager = AccountManager.get(context);

  @Rpc(description = "Add a contact with a given email address to a Google account on the device.")
  public void contactAddToGoogleAccountByEmail(String contactEmailAddress,
      String accountEmailAddress)
      throws ContactSnippetException, OperationApplicationException, RemoteException {
    assertAccountExists(accountEmailAddress);
    ArrayList<ContentProviderOperation> contentProviderOperations = new ArrayList<>();

    // Specify where the new contact should be stored.
    contentProviderOperations.add(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, GOOGLE_ACCOUNT_TYPE)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountEmailAddress).build());

    // Specify data to associate with the new contact.
    contentProviderOperations.add(
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, contactEmailAddress)
            .withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.TYPE_HOME).build());

    // Apply the operations to the ContentProvider.
    context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, contentProviderOperations);
  }

  @Rpc(description = "Remove a contact with a given email address from a Google account on the device")
  public void contactRemoveFromGoogleAccountByEmail(String contactEmailAddress,
      String accountEmailAddress)
      throws ContactSnippetException, OperationApplicationException, RemoteException {
    assertAccountExists(accountEmailAddress);

    // Specify data to associate with the target contact to remove.
    long contactId = getContactIdByEmail(contactEmailAddress, accountEmailAddress);
    ArrayList<ContentProviderOperation> contentProviderOperations = new ArrayList<>();
    contentProviderOperations.add(ContentProviderOperation.newDelete(
        ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, contactId)).build());

    // Apply the operations to the ContentProvider.
    context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, contentProviderOperations);
  }

  @Rpc(description = "Requests an immediate synchronization of contact data for the specified Google account.")
  public void syncGoogleContacts(String accountEmailAddress) {
    Bundle settingsBundle = new Bundle();
    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
    ContentResolver.requestSync(new Account(accountEmailAddress, GOOGLE_ACCOUNT_TYPE),
        ContactsContract.AUTHORITY, settingsBundle);
  }

  private long getContactIdByEmail(String emailAddress, String accountEmailAddress)
      throws OperationApplicationException {
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Email.ADDRESS + " = ?"
                    + " AND "
                    + ContactsContract.RawContacts.ACCOUNT_NAME + " = ?",
                new String[]{emailAddress, accountEmailAddress},
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(
            cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID));
      }
      throw new OperationApplicationException(
          "The contact " + emailAddress + " doesn't appear to be saved on " + accountEmailAddress);
    }
  }

  private void assertAccountExists(String emailAddress) throws ContactSnippetException {
    Account[] accounts = mAccountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
    for (Account account : accounts) {
      if (account.name.equals(emailAddress)) {
        return;
      }
    }
    throw new ContactSnippetException(
        "The account " + emailAddress + " doesn't appear to be login on the device");
  }

  @Override
  public void shutdown() {
  }
}
