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
import android.accounts.AccountManagerFuture;
import android.accounts.AccountsException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Snippet class exposing Android APIs related to management of device accounts.
 *
 * <p>Android devices can have accounts of any type added and synced. New types can be created by
 * apps by implementing a {@link android.content.ContentProvider} for a particular account type.
 *
 * <p>Google (gmail) accounts are of type "com.google" and their handling is managed by the
 * operating system. This class allows you to add and remove Google accounts from a device.
 */
public class AccountSnippet implements Snippet {
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String AUTH_TOKEN_TYPE = "mail";

    private static class AccountSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public AccountSnippetException(String msg) {
            super(msg);
        }
    }

    private final AccountManager mAccountManager;
    private final List<Object> mSyncStatusObserverHandles;

    private final Map<String, Set<String>> mSyncAllowList;
    private final ReentrantReadWriteLock mLock;

    public AccountSnippet() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mAccountManager = AccountManager.get(context);
        mSyncStatusObserverHandles = new LinkedList<>();
        mSyncAllowList = new HashMap<>();
        mLock = new ReentrantReadWriteLock();
    }

    /**
     * Adds a Google account to the device.
     *
     * @param username Username of the account to add (including @gmail.com).
     * @param password Password of the account to add.
     */
    @Rpc(
            description =
                    "Add a Google (GMail) account to the device, with account data sync disabled.")
    public void addAccount(String username, String password)
            throws AccountSnippetException, AccountsException, IOException {
        // Check for existing account. If we try to re-add an existing account, Android throws an
        // exception that says "Account does not exist or not visible. Maybe change pwd?" which is
        // a little hard to understand.
        if (listAccounts().contains(username)) {
            throw new AccountSnippetException(
                    "Account " + username + " already exists on the device");
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
                    String.format(
                            Locale.US,
                            "Failed to add account due to code %d: %s",
                            result.getInt(AccountManager.KEY_ERROR_CODE),
                            result.getString(AccountManager.KEY_ERROR_MESSAGE)));
        }

        // Disable sync to avoid test flakiness as accounts fetch additional data.
        // It takes a while for all sync adapters to be populated, so register for broadcasts when
        // sync is starting and disable them there.
        // NOTE: this listener is NOT unregistered because several sync requests for the new account
        // will come in over time.
        Account account = new Account(username, GOOGLE_ACCOUNT_TYPE);
        Object handle =
                ContentResolver.addStatusChangeListener(
                        ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                                | ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
                        which -> {
                            for (SyncAdapterType adapter : ContentResolver.getSyncAdapterTypes()) {
                                // Ignore non-Google account types.
                                if (!adapter.accountType.equals(GOOGLE_ACCOUNT_TYPE)) {
                                    continue;
                                }
                                // If a content provider is not allowListed, then disable it.
                                // Because startSync and stopSync synchronously update the allowList
                                // and sync settings, writelock both the allowList check and the
                                // call to sync together.
                                mLock.writeLock().lock();
                                try {
                                    if (!isAdapterAllowListed(username, adapter.authority)) {
                                        updateSync(account, adapter.authority, false /* sync */);
                                    }
                                } finally {
                                    mLock.writeLock().unlock();
                                }
                            }
                        });
        mSyncStatusObserverHandles.add(handle);
    }

    /**
     * Removes an account from the device.
     *
     * <p>The account has to be Google account.
     *
     * @param username the username of the account to remove.
     * @throws AccountSnippetException if removing the account failed.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Rpc(description = "Remove a Google account.")
    public void removeAccount(String username) throws AccountSnippetException {
        if (!mAccountManager.removeAccountExplicitly(getAccountByName(username))) {
            throw new AccountSnippetException("Failed to remove account '" + username + "'.");
        }
    }

    /**
     * Get an existing account by its username.
     *
     * <p>Google account only.
     *
     * @param username the username of the account to remove.
     * @return tHe account with the username.
     * @throws AccountSnippetException if no account has the given username.
     */
    private Account getAccountByName(String username) throws AccountSnippetException {
        Account[] accounts = mAccountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        for (Account account : accounts) {
            if (account.name.equals(username)) {
                return account;
            }
        }
        throw new AccountSnippetException(
                "Account '" + username + "' does not exist on the device.");
    }

    /**
     * Checks to see if the SyncAdapter is allowListed.
     *
     * <p>AccountSnippet disables syncing by default when adding an account, except for allowListed
     * SyncAdapters. This function checks the allowList for a specific account-authority pair.
     *
     * @param username Username of the account (including @gmail.com).
     * @param authority The authority of a content provider that should be checked.
     */
    private boolean isAdapterAllowListed(String username, String authority) {
        boolean result = false;
        mLock.readLock().lock();
        try {
            Set<String> allowListedProviders = mSyncAllowList.get(username);
            if (allowListedProviders != null) {
                result = allowListedProviders.contains(authority);
            }
        } finally {
            mLock.readLock().unlock();
        }
        return result;
    }

    /**
     * Updates ContentResolver sync settings for an Account's specified SyncAdapter.
     *
     * <p>Sets an accounts SyncAdapter (selected based on authority) to sync/not-sync automatically
     * and immediately requests/cancels a sync.
     *
     * <p>updateSync should always be called under {@link AccountSnippet#mLock} write lock to avoid
     * flapping between the getSyncAutomatically and setSyncAutomatically calls.
     *
     * @param account A Google Account.
     * @param authority The authority of a content provider that should (not) be synced.
     * @param sync Whether or not the account's content provider should be synced.
     */
    private void updateSync(Account account, String authority, boolean sync) {
        if (ContentResolver.getSyncAutomatically(account, authority) != sync) {
            ContentResolver.setSyncAutomatically(account, authority, sync);
            if (sync) {
                ContentResolver.requestSync(account, authority, new Bundle());
            } else {
                ContentResolver.cancelSync(account, authority);
            }
            Log.i(
                    "Set sync to "
                            + sync
                            + " for account "
                            + account
                            + ", adapter "
                            + authority
                            + ".");
        }
    }

    /**
     * Enables syncing of a SyncAdapter for a given content provider.
     *
     * <p>Adds the authority to a allowList, and immediately requests a sync.
     *
     * @param username Username of the account (including @gmail.com).
     * @param authority The authority of a content provider that should be synced.
     */
    @Rpc(description = "Enables syncing of a SyncAdapter for a content provider.")
    public void startSync(String username, String authority) throws AccountSnippetException {
        if (!listAccounts().contains(username)) {
            throw new AccountSnippetException("Account " + username + " is not on the device");
        }
        // Add to the allowList
        mLock.writeLock().lock();
        try {
            if (mSyncAllowList.containsKey(username)) {
                mSyncAllowList.get(username).add(authority);
            } else {
                mSyncAllowList.put(username, new HashSet<String>(Arrays.asList(authority)));
            }
            // Update the Sync settings
            for (SyncAdapterType adapter : ContentResolver.getSyncAdapterTypes()) {
                // Find the Google account content provider.
                if (adapter.accountType.equals(GOOGLE_ACCOUNT_TYPE)
                        && adapter.authority.equals(authority)) {
                    Account account = new Account(username, GOOGLE_ACCOUNT_TYPE);
                    updateSync(account, authority, true);
                }
            }
        } finally {
            mLock.writeLock().unlock();
        }
    }

    /**
     * Disables syncing of a SyncAdapter for a given content provider.
     *
     * <p>Removes the content provider authority from a allowList.
     *
     * @param username Username of the account (including @gmail.com).
     * @param authority The authority of a content provider that should not be synced.
     */
    @Rpc(description = "Disables syncing of a SyncAdapter for a content provider.")
    public void stopSync(String username, String authority) throws AccountSnippetException {
        if (!listAccounts().contains(username)) {
            throw new AccountSnippetException("Account " + username + " is not on the device");
        }
        // Remove from allowList
        mLock.writeLock().lock();
        try {
            if (mSyncAllowList.containsKey(username)) {
                Set<String> allowListedProviders = mSyncAllowList.get(username);
                allowListedProviders.remove(authority);
                if (allowListedProviders.isEmpty()) {
                    mSyncAllowList.remove(username);
                }
            }
            // Update the Sync settings
            for (SyncAdapterType adapter : ContentResolver.getSyncAdapterTypes()) {
                // Find the Google account content provider.
                if (adapter.accountType.equals(GOOGLE_ACCOUNT_TYPE)
                        && adapter.authority.equals(authority)) {
                    Account account = new Account(username, GOOGLE_ACCOUNT_TYPE);
                    updateSync(account, authority, false);
                }
            }
        } finally {
            mLock.writeLock().unlock();
        }
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
