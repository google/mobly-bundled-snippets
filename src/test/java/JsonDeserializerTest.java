import android.bluetooth.BluetoothGattCharacteristic;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.MbsEnums;
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
    JSONObject json = new JSONObject();
    json.put("UUID", "ffffffff-ffff-ffff-ffff-ffffffffffff");
    json.put("Properties", "PROPERTY_READ");
    json.put("Permissions", "PERMISSION_READ");

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, json);
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ);
  }

  @Test
  public void testCharacteristicWithMultiplePropertiesPermissions() throws Throwable {
    JSONObject json = new JSONObject();
    json.put("UUID", "ffffffff-ffff-ffff-ffff-ffffffffffff");
    json.put("Properties", "PROPERTY_READ|PROPERTY_WRITE");
    json.put("Permissions", "PERMISSION_READ|PERMISSION_WRITE");

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, json);
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ |BluetoothGattCharacteristic.PERMISSION_WRITE);
  }

  public void testCharacteristicWithPropertyPermission() throws Throwable {
    JSONObject json = new JSONObject();
    json.put("UUID", "ffffffff-ffff-ffff-ffff-ffffffffffff");
    json.put("Property", "PROPERTY_READ");
    json.put("Permission", "PERMISSION_READ");

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, json);
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ);
  }

  @Test
  public void testCharacteristicWithMultiplePropertyPermission() throws Throwable {
    JSONObject json = new JSONObject();
    json.put("UUID", "ffffffff-ffff-ffff-ffff-ffffffffffff");
    json.put("Property", "PROPERTY_READ|PROPERTY_WRITE");
    json.put("Permission", "PERMISSION_READ|PERMISSION_WRITE");

    BluetoothGattCharacteristic characteristic = JsonDeserializer.jsonToBluetoothGattCharacteristic(null, json);
    Truth.assertThat(characteristic.getUuid()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    Truth.assertThat(characteristic.getProperties()).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE);
    Truth.assertThat(characteristic.getPermissions()).isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ |BluetoothGattCharacteristic.PERMISSION_WRITE);
  }
}