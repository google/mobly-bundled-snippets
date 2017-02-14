/*
 * Copyright (C) 2017 Google Inc.
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
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AccountsException;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.os.Handler;
import android.support.test.InstrumentationRegistry;
import android.widget.Toast;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Snippet class exposing Android APIs related to management of device accounts.
 *
 * <p>Android devices can have accounts of any type added and synced. New types can be created by
 * apps by implementing a {@link android.content.ContentProvider} for a particular account type.
 *
 * <p>Google (gmail) accounts are of type "com.google" and their handling is managed by the
 * operating system. This class allows you to add and remove Google accounts from a device.
 * */
public class AccountSnippet implements Snippet {
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String AUTH_TOKEN_TYPE = "mail";

    private static class AccountSnippetException extends Exception {
        public AccountSnippetException(String msg) {
            super(msg);
        }
    }

    private final AccountManager mAccountManager;
    private final List<Object> mSyncStatusObserverHandles;

    public AccountSnippet() {
        Context context = InstrumentationRegistry.getContext();
        mAccountManager = AccountManager.get(context);
        mSyncStatusObserverHandles = new LinkedList<>();
    }

    /**
     * Adds a Google account to the device.
     *
     * <p>TODO(adorokhine): Support adding accounts of other types with an optional 'type' kwarg.
     * <p>TODO(adorokhine): Allow users to choose whether to enable/disable sync with a kwarg.
     *
     * @param username Username of the account to add (including @gmail.com).
     * @param password Password of the account to add.
     */
    @Rpc(description =
        "Add a Google (GMail) account to the device, with account data sync disabled.")
    public void addAccount(String username, String password)
        throws AccountSnippetException, AccountsException, IOException {
        // Check for existing account. If we try to re-add an existing account, Android throws an
        // exception that says "Account does not exist or not visible. Maybe change pwd?" which is
        // a little hard to understand.
        if (listAccounts().contains(username)) {
            throw new AccountSnippetException(
                "Account " + username + " alreaady exists on the device");
        }
        Bundle addAccountOptions = new Bundle();
        addAccountOptions.putString("username", username);
        addAccountOptions.putString("password", password);
        AccountManagerFuture<Bundle> future =
            mAccountManager.addAccount(
                GOOGLE_ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                null /* requiredFeatures */,
                addAccountOptions,
                null /* activity */,
                null /* authCallback */,
                null /* handler */);
        Bundle result = future.getResult();
        if (result.containsKey(AccountManager.KEY_ERROR_CODE)) {
            throw new AccountSnippetException(
                String.format("Failed to add account due to code %d: %s",
                    result.getInt(AccountManager.KEY_ERROR_CODE),
                    result.getString(AccountManager.KEY_ERROR_MESSAGE)));
        }

        // Disable sync to avoid test flakiness as accounts fetch additional data.
        // It takes a while for all sync adapters to be populated, so register for broadcasts when
        // sync is starting and disable them there.
        // NOTE: this listener is NOT unregistered because several sync requests for the new account
        // will come in over time.
        Account account = new Account(username, GOOGLE_ACCOUNT_TYPE);
        Object handle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE | ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
            which -> {
                Log.i("Attempt to sync account " + username + " detected! Disabling.");
                for (SyncAdapterType adapter : ContentResolver.getSyncAdapterTypes()) {
                    if (!adapter.accountType.equals(GOOGLE_ACCOUNT_TYPE)) {
                        continue;
                    }
                    ContentResolver
                        .setSyncAutomatically(account, adapter.authority, false /* sync */);
                    ContentResolver.cancelSync(account, adapter.authority);
                }
            });
        mSyncStatusObserverHandles.add(handle);
    }

    /**
     * Returns a list of all Google accounts on the device.
     *
     * <p>TODO(adorokhine): Support accounts of other types with an optional 'type' kwarg.
     */
    @Rpc(description = "List all Google (GMail) accounts on the device.")
    public Set<String> listAccounts() throws SecurityException {
        Account[] accounts = mAccountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        Set<String> usernames = new TreeSet<>();
        for (Account account : accounts) {
            usernames.add(account.name);
        }
        return usernames;
    }

    @Override
    public void shutdown() {
        for (Object handle : mSyncStatusObserverHandles) {
            ContentResolver.removeStatusChangeListener(handle);
        }
    }
}
