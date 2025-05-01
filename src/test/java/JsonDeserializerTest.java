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
import android.bluetooth.BluetoothGattDescriptor;

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

  @Test
  public void testDescriptor() throws Throwable {
    String jsonString =
            "{" +
            "  \"UUID\": \"ffffffff-ffff-ffff-ffff-ffffffffffff\"," +
            "  \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"" +
            "}";

    BluetoothGattDescriptor descriptor = JsonDeserializer.jsonToBluetoothGattDescriptor(new JSONObject(jsonString));
    Truth.assertThat(descriptor.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(descriptor.getPermissions()).isEqualTo(BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
  }

  @Test
  public void testCharacteristicNoDescriptors() throws Throwable {
    String jsonString =
            "{" +
            "  \"UUID\": \"ffffffff-ffff-ffff-ffff-ffffffffffff\"," +
            "  \"Properties\":\"PROPERTY_READ|PROPERTY_WRITE\"," +
            "  \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"" +
            "}";

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, new JSONObject(jsonString));
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
    Truth.assertThat(characteristic.getDescriptors()).isEmpty();
  }

  @Test
  public void testCharacteristicEmptyListDescriptors() throws Throwable {
    String jsonString =
            "{" +
            "  \"UUID\": \"ffffffff-ffff-ffff-ffff-ffffffffffff\"," +
            "  \"Properties\":\"PROPERTY_READ|PROPERTY_WRITE\"," +
            "  \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"," +
            "  \"Descriptors\": []" +
            "}";

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, new JSONObject(jsonString));
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
    Truth.assertThat(characteristic.getDescriptors()).isEmpty();
  }

  @Test
  public void testCharacteristic1Descriptor() throws Throwable {
    String jsonString =
            "{" +
            "  \"UUID\": \"ffffffff-ffff-ffff-ffff-ffffffffffff\"," +
            "  \"Properties\":\"PROPERTY_READ|PROPERTY_WRITE\"," +
            "  \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"," +
            "  \"Descriptors\":" +
            "  [" +
            "    {" +
            "      \"UUID\": \"dddddddd-dddd-dddd-dddd-dddddddddddd\"," +
            "      \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"" +
            "    }" +
            "  ]" +
            "}";

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, new JSONObject(jsonString));
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
    Truth.assertThat(characteristic.getDescriptors().size()).isEqualTo(1);
    Truth.assertThat(characteristic.getDescriptors().get(0).getUuid()).isEqualTo(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    Truth.assertThat(characteristic.getDescriptors().get(0).getPermissions()).isEqualTo(BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
  }
  @Test
  public void testCharacteristic2Descriptors() throws Throwable {
    String jsonString =
            "{" +
            "  \"UUID\": \"ffffffff-ffff-ffff-ffff-ffffffffffff\"," +
            "  \"Properties\":\"PROPERTY_READ|PROPERTY_WRITE\"," +
            "  \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"," +
            "  \"Descriptors\":" +
            "  [" +
            "    {" +
            "      \"UUID\": \"dddddddd-dddd-dddd-dddd-dddddddddddd\"," +
            "      \"Permissions\": \"PERMISSION_READ|PERMISSION_WRITE\"" +
            "    }," +
            "    {" +
            "      \"UUID\": \"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee\"," +
            "      \"Permissions\": \"PERMISSION_READ\"" +
            "    }" +
            "  ]" +
            "}";

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, new JSONObject(jsonString));
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
    Truth.assertThat(characteristic.getDescriptors().size()).isEqualTo(2);
    Truth.assertThat(characteristic.getDescriptors().get(0).getUuid()).isEqualTo(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    Truth.assertThat(characteristic.getDescriptors().get(0).getPermissions()).isEqualTo(BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
    Truth.assertThat(characteristic.getDescriptors().get(1).getUuid()).isEqualTo(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
    Truth.assertThat(characteristic.getDescriptors().get(1).getPermissions()).isEqualTo(BluetoothGattDescriptor.PERMISSION_READ);
  }
}
