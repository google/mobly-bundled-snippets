/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.mobly.snippet.bundled.utils;

import android.bluetooth.BluetoothGattCharacteristic;
import java.util.HashMap;

/** A holder to hold android objects for snippets. */
// TODO(ko1in1u): For future extensions between Snippet classes and Utils.
public class DataHolder {
    private final HashMap<BluetoothGattCharacteristic, String> dataToBeRead;

    public DataHolder() {
        dataToBeRead = new HashMap<>();
    }

    public String get(BluetoothGattCharacteristic characteristic) {
        return dataToBeRead.get(characteristic);
    }

    public void insertData(BluetoothGattCharacteristic characteristic, String string) {
        dataToBeRead.put(characteristic, string);
    }
}
