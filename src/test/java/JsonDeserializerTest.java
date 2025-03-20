/*
 * Copyright (C) 2025 Google Inc.
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

import android.bluetooth.BluetoothGattCharacteristic;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.common.truth.Truth;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = 33)
public class JsonDeserializerTest {
  @Test
  public void testCharacteristicWithPropertiesPermissions() throws Throwable {
    String uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";

    JSONObject json = new JSONObject();
    json.put("UUID", uuid);
    json.put("Properties", "PROPERTY_READ");
    json.put("Permissions", "PERMISSION_READ");

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, json);
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString(uuid));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ);
  }

  @Test
  public void testCharacteristicWithMultiplePropertiesPermissions() throws Throwable {
    String uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";

    JSONObject json = new JSONObject();
    json.put("UUID", uuid);
    json.put("Properties", "PROPERTY_READ|PROPERTY_WRITE");
    json.put("Permissions", "PERMISSION_READ|PERMISSION_WRITE");

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, json);
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString(uuid));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ |BluetoothGattCharacteristic.PERMISSION_WRITE);
  }
}